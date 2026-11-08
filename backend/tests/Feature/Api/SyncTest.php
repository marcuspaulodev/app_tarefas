<?php

namespace Tests\Feature\Api;

use App\Models\Tag;
use App\Models\Task;
use App\Models\User;
use App\Support\OccurrenceId;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Illuminate\Support\Str;
use Illuminate\Testing\TestResponse;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class SyncTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    private int $nowMs;

    protected function setUp(): void
    {
        parent::setUp();

        $this->travelTo(Carbon::parse('2026-11-10 12:00:00.123', 'UTC'));
        $this->nowMs = now()->getTimestampMs();
        $this->user = User::factory()->create();
    }

    /**
     * @param  array<string, mixed>  $overrides
     * @return array<string, mixed>
     */
    private function task(array $overrides = []): array
    {
        return array_replace([
            'id' => (string) Str::uuid7(),
            'title' => 'Tarefa offline',
            'description' => '',
            'date' => '2026-11-05',
            'original_date' => '2026-11-05',
            'deadline' => null,
            'priority' => 'HIGH',
            'recurrence' => null,
            'series_id' => null,
            'completed' => false,
            'created_at' => $this->nowMs - 20000,
            'updated_at' => $this->nowMs - 10000,
            'deleted_at' => null,
            'tag_ids' => [],
        ], $overrides);
    }

    /**
     * @param  array<string, mixed>  $overrides
     * @return array<string, mixed>
     */
    private function tag(array $overrides = []): array
    {
        return array_replace([
            'id' => (string) Str::uuid7(),
            'name' => 'Offline',
            'color_hex' => '#3D5AFE',
            'updated_at' => $this->nowMs - 10000,
            'deleted_at' => null,
        ], $overrides);
    }

    /**
     * @param  array{cursor?: int, tasks?: list<array<string, mixed>>, tags?: list<array<string, mixed>>}  $body
     */
    private function sync(array $body = []): TestResponse
    {
        Sanctum::actingAs($this->user);

        return $this->postJson(route('api.v1.sync'), array_replace(['cursor' => 0], $body));
    }

    public function test_requires_authentication(): void
    {
        $this->postJson(route('api.v1.sync'), ['cursor' => 0])->assertUnauthorized();
    }

    public function test_empty_sync_does_not_advance_the_cursor(): void
    {
        $this->sync()->assertOk()->assertExactJson(['data' => [
            'cursor' => 0, 'server_time' => $this->nowMs, 'tasks' => [], 'tags' => [],
        ]]);
        $this->assertSame(0, $this->user->fresh()->sync_version);
    }

    public function test_imports_tasks_and_tags_atomically_without_reinterpreting_their_dates(): void
    {
        $tag = $this->tag();
        $task = $this->task([
            'date' => '2026-11-10', 'completed' => true, 'tag_ids' => [$tag['id']],
        ]);
        $otherTask = $this->task(['tag_ids' => [$tag['id']]]);

        $response = $this->sync(['tasks' => [$task, $otherTask], 'tags' => [$tag]])
            ->assertOk()->assertJsonPath('data.cursor', 1)->assertJsonCount(2, 'data.tasks');
        $imported = collect($response->json('data.tasks'))->keyBy('id')->get($task['id']);

        $this->assertSame('2026-11-05', $imported['original_date']);
        $this->assertSame('2026-11-10', $imported['date']);
        $this->assertTrue($imported['completed']);
        $this->assertSame('', $imported['description']);
        $this->assertSame($task['created_at'], $imported['created_at']);
        $this->assertSame($task['updated_at'], $imported['updated_at']);
        $this->assertSame([$tag['id']], $imported['tag_ids']);
        $this->assertSame([1, 1], Task::pluck('sync_version')->all());
        $this->assertSame(1, Tag::sole()->sync_version);
        $this->assertDatabaseCount('task_tag', 2);
    }

    public function test_retrying_a_batch_does_not_duplicate_records_or_advance_the_cursor(): void
    {
        $tag = $this->tag();
        $body = ['tags' => [$tag], 'tasks' => [$this->task(['tag_ids' => [$tag['id']]])]];
        $first = $this->sync($body)->assertOk()->json('data');

        $this->sync($body)->assertOk()->assertExactJson(['data' => $first]);
        $this->sync(['cursor' => 1, ...$body])->assertOk()->assertExactJson(['data' => $first]);
        $this->assertDatabaseCount('tasks', 1);
        $this->assertDatabaseCount('tags', 1);
        $this->assertDatabaseCount('task_tag', 1);
        $this->assertSame(1, $this->user->fresh()->sync_version);
    }

    public function test_pulls_only_changes_after_the_cursor_including_web_deletions(): void
    {
        $tag = $this->tag();
        $task = $this->task(['tag_ids' => [$tag['id']]]);
        $untouched = $this->task();
        $this->sync(['tags' => [$tag], 'tasks' => [$task, $untouched]])->assertOk();

        $this->patchJson(route('api.v1.tasks.update', $task['id']), ['title' => 'Editada no web'])->assertOk();
        $this->deleteJson(route('api.v1.tags.destroy', $tag['id']))->assertNoContent();
        $this->sync(['cursor' => 1])->assertOk()
            ->assertJsonPath('data.cursor', 3)
            ->assertJsonPath('data.tasks.0.title', 'Editada no web')
            ->assertJsonPath('data.tasks.0.tag_ids', [])
            ->assertJsonCount(1, 'data.tasks')
            ->assertJsonCount(1, 'data.tags');

        $this->deleteJson(route('api.v1.tasks.destroy', $task['id']))->assertNoContent();
        $this->sync(['cursor' => 3])->assertOk()
            ->assertJsonPath('data.cursor', 4)
            ->assertJsonPath('data.tasks.0.deleted_at', Task::withTrashed()->find($task['id'])->deleted_at->getTimestampMs())
            ->assertJsonCount(1, 'data.tasks')->assertJsonPath('data.tags', []);
        $this->sync(['cursor' => 4])->assertOk()->assertJsonPath('data.tasks', [])->assertJsonPath('data.tags', []);
    }

    public function test_newer_device_changes_win_with_millisecond_precision_and_preserve_creation_time(): void
    {
        $task = $this->task();
        $tag = $this->tag();
        $this->sync(['tasks' => [$task], 'tags' => [$tag]])->assertOk();

        $newTask = array_replace($task, [
            'title' => 'Mais recente', 'updated_at' => $task['updated_at'] + 1, 'created_at' => $task['created_at'] + 1,
        ]);
        $newTag = array_replace($tag, ['name' => 'Nova', 'updated_at' => $tag['updated_at'] + 1]);

        $this->sync(['cursor' => 1, 'tasks' => [$newTask], 'tags' => [$newTag]])->assertOk()
            ->assertJsonPath('data.cursor', 2)
            ->assertJsonPath('data.tasks.0.title', 'Mais recente')
            ->assertJsonPath('data.tasks.0.updated_at', $newTask['updated_at'])
            ->assertJsonPath('data.tasks.0.created_at', $task['created_at'])
            ->assertJsonPath('data.tags.0.name', 'Nova');
    }

    public function test_stale_and_equal_timestamp_writes_return_the_server_winner_even_before_the_cursor(): void
    {
        $task = $this->task();
        $tag = $this->tag();
        $this->sync(['tasks' => [$task, $this->task()], 'tags' => [$tag]])->assertOk();
        $this->sync(['cursor' => 1, 'tasks' => [$this->task()]])->assertOk();

        foreach ([$task['updated_at'] - 1, $task['updated_at']] as $timestamp) {
            $this->sync([
                'cursor' => 2,
                'tasks' => [array_replace($task, ['title' => 'Perdeu', 'updated_at' => $timestamp])],
                'tags' => [array_replace($tag, ['name' => 'Perdeu', 'updated_at' => $timestamp])],
            ])->assertOk()->assertJsonPath('data.cursor', 2)
                ->assertJsonPath('data.tasks.0.title', $task['title'])->assertJsonCount(1, 'data.tasks')
                ->assertJsonPath('data.tags.0.name', $tag['name'])->assertJsonCount(1, 'data.tags');
        }
    }

    public function test_newer_offline_deletions_are_kept_and_retransmitted(): void
    {
        $task = $this->task();
        $this->sync(['tasks' => [$task]])->assertOk();
        $deleted = array_replace($task, ['updated_at' => $this->nowMs, 'deleted_at' => $this->nowMs]);

        $this->sync(['cursor' => 1, 'tasks' => [$deleted]])->assertOk()
            ->assertJsonPath('data.cursor', 2)->assertJsonPath('data.tasks.0.deleted_at', $this->nowMs);
        $this->assertSoftDeleted('tasks', ['id' => $task['id']]);
        $this->sync(['cursor' => 1])->assertOk()->assertJsonPath('data.tasks.0.deleted_at', $this->nowMs);
        $this->sync(['cursor' => 2, 'tasks' => [$deleted]])->assertOk()->assertJsonPath('data.cursor', 2);

        $this->sync(['cursor' => 2, 'tasks' => [array_replace($task, ['updated_at' => $this->nowMs + 1])]])
            ->assertOk()->assertJsonPath('data.cursor', 2)->assertJsonPath('data.tasks.0.deleted_at', $this->nowMs);
    }

    public function test_a_stale_offline_delete_does_not_overwrite_a_newer_edit(): void
    {
        $task = $this->task(['updated_at' => $this->nowMs]);
        $this->sync(['tasks' => [$task]])->assertOk();
        $this->sync(['cursor' => 1, 'tasks' => [array_replace($task, [
            'updated_at' => $this->nowMs - 1, 'deleted_at' => $this->nowMs - 1,
        ])]])->assertOk()->assertJsonPath('data.cursor', 1)->assertJsonPath('data.tasks.0.deleted_at', null);
        $this->assertNotSoftDeleted('tasks', ['id' => $task['id']]);
    }

    public function test_accepts_tombstones_for_records_created_and_deleted_before_the_first_sync(): void
    {
        $tag = $this->tag(['deleted_at' => $this->nowMs - 10000]);
        $task = $this->task(['deleted_at' => $this->nowMs - 10000, 'tag_ids' => [$tag['id']]]);
        $this->sync(['tasks' => [$task], 'tags' => [$tag]])->assertOk()
            ->assertJsonPath('data.tasks.0.tag_ids', [])
            ->assertJsonPath('data.tasks.0.deleted_at', $task['deleted_at'])
            ->assertJsonPath('data.tags.0.deleted_at', $tag['deleted_at']);
        $this->assertDatabaseCount('tasks', 1);
        $this->assertSame(0, Task::count());
        $this->assertSame(0, Tag::count());
    }

    public function test_deleting_a_tag_keeps_task_edits_from_the_same_batch_and_versions_other_affected_tasks(): void
    {
        $tag = $this->tag();
        $edited = $this->task(['tag_ids' => [$tag['id']]]);
        $other = $this->task(['tag_ids' => [$tag['id']]]);
        $untouched = $this->task();
        $this->sync(['tasks' => [$edited, $other, $untouched], 'tags' => [$tag]])->assertOk();
        $this->sync([
            'cursor' => 1,
            'tags' => [array_replace($tag, ['updated_at' => $this->nowMs, 'deleted_at' => $this->nowMs])],
            'tasks' => [array_replace($edited, ['title' => 'Edição offline', 'updated_at' => $this->nowMs - 1])],
        ])->assertOk()->assertJsonPath('data.cursor', 2)->assertJsonCount(2, 'data.tasks');

        $this->assertSame('Edição offline', Task::find($edited['id'])->title);
        $this->assertSame($this->nowMs, Task::find($other['id'])->updated_at->getTimestampMs());
        $this->assertSame(2, Task::find($other['id'])->sync_version);
        $this->assertSame(1, Task::find($untouched['id'])->sync_version);
        $this->assertDatabaseCount('task_tag', 0);

        $this->sync(['cursor' => 2, 'tasks' => [array_replace($other, ['updated_at' => $this->nowMs + 1])]])
            ->assertOk()->assertJsonPath('data.tasks.0.tag_ids', []);
        $this->sync(['cursor' => 3, 'tags' => [array_replace($tag, ['updated_at' => $this->nowMs + 1])]])
            ->assertOk()->assertJsonPath('data.cursor', 3)->assertJsonPath('data.tags.0.deleted_at', $this->nowMs);
    }

    public function test_recurrence_snapshots_do_not_generate_duplicate_occurrences_or_restore_deleted_ones(): void
    {
        $task = $this->task(['date' => '2026-11-10', 'recurrence' => 'DAILY']);
        $this->sync(['tasks' => [$task]])->assertOk();
        $this->postJson(route('api.v1.tasks.toggle', $task['id']), ['today' => '2026-11-10'])->assertOk();

        $nextId = OccurrenceId::for($task['id'], '2026-11-11');
        $completed = array_replace($task, ['completed' => true, 'series_id' => $task['id']]);
        $next = $this->task([
            'id' => $nextId, 'date' => '2026-11-11', 'original_date' => '2026-11-11',
            'series_id' => $task['id'], 'recurrence' => 'DAILY',
        ]);
        $this->sync(['cursor' => 1, 'tasks' => [$completed, $next]])->assertOk()->assertJsonPath('data.cursor', 2);
        $this->assertDatabaseCount('tasks', 2);

        $this->deleteJson(route('api.v1.tasks.destroy', $nextId))->assertNoContent();
        $this->sync(['cursor' => 3, 'tasks' => [array_replace($next, ['updated_at' => $this->nowMs + 1000])]])
            ->assertOk()->assertJsonPath('data.cursor', 3);
        $this->assertSoftDeleted('tasks', ['id' => $nextId]);
        $this->assertDatabaseCount('tasks', 2);
    }

    public function test_completed_offline_recurrence_is_imported_as_state_without_replaying_the_toggle(): void
    {
        $task = $this->task(['completed' => true, 'recurrence' => 'DAILY']);
        $task['series_id'] = $task['id'];
        $this->sync(['tasks' => [$task]])->assertOk()->assertJsonPath('data.tasks.0.completed', true);
        $this->assertDatabaseCount('tasks', 1);
    }

    public function test_reads_are_isolated_to_the_authenticated_account(): void
    {
        Task::factory()->create(['sync_version' => 50]);
        Tag::factory()->create(['sync_version' => 50]);
        $this->sync()->assertOk()->assertJsonPath('data.tasks', [])->assertJsonPath('data.tags', []);
    }

    public function test_other_accounts_ids_and_tags_are_rejected_without_partial_writes(): void
    {
        $owner = User::factory()->create();
        $otherTask = Task::factory()->for($owner)->trashed()->create();
        $otherTag = Tag::factory()->for($owner)->create();
        $validTask = $this->task();
        $validTag = $this->tag();
        $cases = [
            ['tasks' => [$validTask, $this->task(['id' => $otherTask->id])], 'tags' => [$validTag]],
            ['tasks' => [$validTask], 'tags' => [$validTag, $this->tag(['id' => $otherTag->id])]],
            ['tasks' => [$this->task(['tag_ids' => [$otherTag->id]])], 'tags' => [$validTag]],
            ['tasks' => [$this->task(['tag_ids' => [(string) Str::uuid7()]])], 'tags' => [$validTag]],
        ];
        foreach ($cases as $body) {
            $this->sync($body)->assertUnprocessable();
            $this->assertSame(0, $this->user->fresh()->sync_version);
            $this->assertSame(0, $this->user->tasks()->withTrashed()->count());
            $this->assertSame(0, $this->user->tags()->count());
        }
        $this->assertSame($otherTask->title, $otherTask->fresh()->title);
        $this->assertNotSoftDeleted($otherTag);
    }

    public function test_rejects_future_cursors_and_invalid_batches_without_advancing_version(): void
    {
        $this->sync(['cursor' => 1, 'tasks' => [$this->task()]])
            ->assertUnprocessable()->assertJsonValidationErrors('cursor');
        $cases = [
            ['id' => 'invalid'], ['title' => ''], ['date' => '2026-02-30'], ['original_date' => 'yesterday'],
            ['priority' => 'URGENT'], ['recurrence' => 'WEEKLY:8'], ['completed' => 'yes'],
            ['updated_at' => -1], ['updated_at' => $this->nowMs + 300001],
            ['created_at' => $this->nowMs], ['deleted_at' => $this->nowMs],
            ['user_id' => $this->user->id], ['sync_version' => 900],
        ];
        foreach ($cases as $overrides) {
            $this->sync(['tasks' => [$this->task(), $this->task($overrides)], 'tags' => [$this->tag()]])
                ->assertUnprocessable();
        }
        $incomplete = $this->task();
        unset($incomplete['deleted_at']);
        $this->sync(['tasks' => [$incomplete]])->assertUnprocessable()->assertJsonValidationErrors('tasks.0.deleted_at');
        $this->assertDatabaseCount('tasks', 0);
        $this->assertDatabaseCount('tags', 0);
        $this->assertSame(0, $this->user->fresh()->sync_version);
    }

    public function test_rejects_duplicate_record_ids_case_insensitively_and_limits_batch_size(): void
    {
        $task = $this->task();
        $tag = $this->tag();
        $this->sync(['tasks' => [$task, array_replace($task, ['id' => strtoupper($task['id'])])]])
            ->assertUnprocessable()->assertJsonValidationErrors('tasks.0.id');
        $this->sync(['tags' => [$tag, array_replace($tag, ['id' => strtoupper($tag['id'])])]])
            ->assertUnprocessable()->assertJsonValidationErrors('tags.0.id');
        $this->sync(['tasks' => array_map(fn (): array => $this->task(), range(1, 101))])
            ->assertUnprocessable()->assertJsonValidationErrors('tasks');
        $this->sync(['tags' => array_map(fn (): array => $this->tag(), range(1, 101))])
            ->assertUnprocessable()->assertJsonValidationErrors('tags');
        $this->assertSame(0, $this->user->fresh()->sync_version);
    }

    public function test_normalizes_uuid_case_and_duplicate_tag_references(): void
    {
        $tag = $this->tag();
        $task = $this->task(['tag_ids' => [$tag['id'], strtoupper($tag['id'])]]);
        $this->sync(['tasks' => [$task], 'tags' => [array_replace($tag, ['id' => strtoupper($tag['id'])])]])
            ->assertOk()->assertJsonPath('data.tasks.0.tag_ids', [$tag['id']])
            ->assertJsonPath('data.tags.0.id', $tag['id']);
        $this->assertDatabaseCount('task_tag', 1);
    }
}
