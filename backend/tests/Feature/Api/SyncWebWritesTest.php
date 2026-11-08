<?php

namespace Tests\Feature\Api;

use App\Actions\Tags\UpdateTag;
use App\Actions\Tasks\CreateTask;
use App\Actions\Tasks\ToggleTaskCompletion;
use App\Actions\Tasks\UpdateTask;
use App\Http\Resources\SyncTaskResource;
use App\Models\Tag;
use App\Models\Task;
use App\Models\User;
use Illuminate\Database\Eloquent\ModelNotFoundException;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Illuminate\Support\Carbon;
use Illuminate\Validation\ValidationException;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class SyncWebWritesTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();

        $this->travelTo(Carbon::parse('2026-11-10 12:00:00.123', 'UTC'));
        $this->user = User::factory()->create();
        Sanctum::actingAs($this->user);
    }

    /**
     * @param  array<string, mixed>  $changes
     */
    private function pushTask(Task $task, array $changes): void
    {
        $payload = (new SyncTaskResource($task->load('tags')))->resolve(new Request);
        unset($payload['sync_version']);
        $payload['priority'] = $task->priority->value;
        $payload['tag_ids'] = $task->tags->pluck('id')->all();

        $this->postJson(route('api.v1.sync'), [
            'cursor' => 0,
            'tasks' => [array_replace($payload, $changes)],
        ])->assertOk();
    }

    public function test_web_edit_uses_the_latest_synced_date_even_if_route_binding_loaded_the_task_earlier(): void
    {
        $stale = Task::factory()->for($this->user)->on('2026-11-05')->create();
        $this->pushTask($stale, [
            'date' => '2026-11-12', 'original_date' => '2026-11-12',
            'description' => 'Do aparelho', 'updated_at' => now()->getTimestampMs() + 1,
        ]);

        $result = app(UpdateTask::class)->handle(
            $stale, ['title' => 'Do web', 'date' => '2026-11-10'], null, '2026-11-10',
        );

        $this->assertSame('2026-11-10', $result->date);
        $this->assertSame('2026-11-10', $result->original_date);
        $this->assertSame('Do aparelho', $result->description);
        $this->assertSame('Do web', $result->title);
        $this->assertSame(2, $result->sync_version);
    }

    public function test_web_toggle_uses_the_latest_synced_completion_state(): void
    {
        $stale = Task::factory()->for($this->user)->on('2026-11-10')->recurring()->create();
        $this->pushTask($stale, [
            'completed' => true, 'series_id' => $stale->id, 'updated_at' => now()->getTimestampMs() + 1,
        ]);

        $result = app(ToggleTaskCompletion::class)->handle($stale, '2026-11-10');

        $this->assertFalse($result->completed);
        $this->assertSame($stale->id, $result->series_id);
        $this->assertDatabaseCount('tasks', 1);
    }

    public function test_web_changes_and_deletes_succeed_a_slightly_ahead_device_clock(): void
    {
        $task = Task::factory()->for($this->user)->create();
        $deviceTime = now()->addMinutes(2)->getTimestampMs();
        $this->pushTask($task, ['title' => 'Do aparelho', 'updated_at' => $deviceTime]);
        $this->patchJson(route('api.v1.tasks.update', $task), ['title' => 'Do web'])->assertOk();
        $this->assertGreaterThan($deviceTime, $task->fresh()->updated_at->getTimestampMs());

        $this->pushTask($task, ['title' => 'Reenvio atrasado', 'updated_at' => $deviceTime]);
        $this->assertSame('Do web', $task->fresh()->title);
        $this->assertSame(2, $this->user->fresh()->sync_version);

        $this->deleteJson(route('api.v1.tasks.destroy', $task))->assertNoContent();
        $deleted = Task::withTrashed()->find($task->id);
        $this->assertGreaterThan($deviceTime, $deleted->deleted_at->getTimestampMs());
        $this->assertSame($deleted->deleted_at->getTimestampMs(), $deleted->updated_at->getTimestampMs());
    }

    public function test_stale_web_update_cannot_modify_a_task_deleted_by_sync(): void
    {
        $stale = Task::factory()->for($this->user)->create();
        $timestamp = now()->getTimestampMs() + 1;
        $this->pushTask($stale, ['updated_at' => $timestamp, 'deleted_at' => $timestamp]);

        try {
            app(UpdateTask::class)->handle($stale, ['title' => 'Não deve gravar'], null, null);
            $this->fail('A tarefa excluída deve continuar indisponível.');
        } catch (ModelNotFoundException) {
            $this->assertSame($stale->title, $stale->fresh()->title);
            $this->assertSoftDeleted($stale);
            $this->assertSame(1, $this->user->fresh()->sync_version);
        }
    }

    public function test_tag_edits_use_the_latest_synced_values_and_monotonic_timestamp(): void
    {
        $stale = Tag::factory()->for($this->user)->create(['color_hex' => '#000000']);
        $timestamp = now()->addMinutes(2)->getTimestampMs();
        $this->postJson(route('api.v1.sync'), ['cursor' => 0, 'tags' => [[
            'id' => $stale->id, 'name' => 'Aparelho', 'color_hex' => '#FFFFFF',
            'updated_at' => $timestamp, 'deleted_at' => null,
        ]]])->assertOk();

        $result = app(UpdateTag::class)->handle($stale, ['name' => 'Web']);

        $this->assertSame('Web', $result->name);
        $this->assertSame('#FFFFFF', $result->color_hex);
        $this->assertGreaterThan($timestamp, $result->updated_at->getTimestampMs());
    }

    public function test_web_tag_deletion_never_moves_a_tasks_clock_backwards(): void
    {
        $tag = Tag::factory()->for($this->user)->create();
        $task = Task::factory()->for($this->user)->create();
        $task->tags()->attach($tag);
        $timestamp = now()->addMinutes(2)->getTimestampMs();
        $this->pushTask($task, ['updated_at' => $timestamp]);

        $this->deleteJson(route('api.v1.tags.destroy', $tag))->assertNoContent();

        $this->assertSame($timestamp, $task->fresh()->updated_at->getTimestampMs());
        $this->assertSame(2, $task->fresh()->sync_version);
        $this->assertDatabaseCount('task_tag', 0);
    }

    public function test_tags_are_rechecked_inside_the_web_write_transaction(): void
    {
        $tag = Tag::factory()->for($this->user)->create();
        $task = Task::factory()->for($this->user)->create();
        $this->deleteJson(route('api.v1.tags.destroy', $tag))->assertNoContent();

        $writes = [
            fn () => app(CreateTask::class)->handle($this->user, ['title' => 'Nova', 'date' => '2026-11-10'], [$tag->id]),
            fn () => app(UpdateTask::class)->handle($task, ['title' => 'Editada'], [$tag->id], null),
        ];
        foreach ($writes as $write) {
            try {
                $write();
                $this->fail('A tag excluída deve ser recusada.');
            } catch (ValidationException $exception) {
                $this->assertArrayHasKey('tag_ids.0', $exception->errors());
                $this->assertSame(1, $this->user->fresh()->sync_version);
                $this->assertSame($task->title, $task->fresh()->title);
                $this->assertDatabaseCount('tasks', 1);
                $this->assertDatabaseCount('task_tag', 0);
            }
        }
    }

    public function test_web_removes_deleted_tags_from_task_tombstones_as_well(): void
    {
        $tag = Tag::factory()->for($this->user)->create();
        $task = Task::factory()->for($this->user)->create();
        $task->tags()->attach($tag);
        $this->deleteJson(route('api.v1.tasks.destroy', $task))->assertNoContent();
        $this->deleteJson(route('api.v1.tags.destroy', $tag))->assertNoContent();

        $this->postJson(route('api.v1.sync'), ['cursor' => 1])->assertOk()
            ->assertJsonPath('data.tasks.0.id', $task->id)
            ->assertJsonPath('data.tasks.0.tag_ids', [])
            ->assertJsonPath('data.tasks.0.sync_version', 2);
        $this->assertDatabaseCount('task_tag', 0);
    }
}
