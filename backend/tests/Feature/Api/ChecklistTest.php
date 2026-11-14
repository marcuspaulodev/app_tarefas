<?php

namespace Tests\Feature\Api;

use App\Models\Task;
use App\Models\User;
use App\Support\OccurrenceId;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Carbon;
use Illuminate\Support\Str;
use Illuminate\Testing\TestResponse;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class ChecklistTest extends TestCase
{
    use RefreshDatabase;

    private const TODAY = '2026-11-10';

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();

        $this->travelTo(Carbon::parse('2026-11-10 12:00:00.123', 'UTC'));
        $this->user = User::factory()->create();
        Sanctum::actingAs($this->user);
    }

    /**
     * @return array{id: string, text: string, done: bool}
     */
    private function item(string $text, bool $done = false, ?string $id = null): array
    {
        return ['id' => $id ?? (string) Str::uuid7(), 'text' => $text, 'done' => $done];
    }

    /**
     * @param  array<string, mixed>  $overrides
     * @return array<string, mixed>
     */
    private function syncedTask(array $overrides = []): array
    {
        $now = now()->getTimestampMs();

        return array_replace([
            'id' => (string) Str::uuid7(), 'title' => 'Do aparelho', 'description' => '',
            'date' => self::TODAY, 'original_date' => self::TODAY, 'deadline' => null,
            'priority' => 'MEDIUM', 'recurrence' => null, 'series_id' => null, 'completed' => false,
            'created_at' => $now - 20000, 'updated_at' => $now - 10000, 'deleted_at' => null, 'tag_ids' => [],
        ], $overrides);
    }

    /**
     * @param  list<array<string, mixed>>  $tasks
     */
    private function sync(array $tasks, int $cursor = 0): TestResponse
    {
        return $this->postJson(route('api.v1.sync'), ['cursor' => $cursor, 'tasks' => $tasks]);
    }

    public function test_tasks_start_with_an_empty_checklist(): void
    {
        $task = Task::factory()->for($this->user)->create();

        $this->getJson(route('api.v1.tasks.show', $task))->assertOk()->assertJsonPath('data.checklist', []);
    }

    public function test_creates_and_replaces_the_checklist_keeping_the_order(): void
    {
        $first = $this->item('  Separar documentos ');
        $second = $this->item('Enviar', done: true);

        $id = $this->postJson(route('api.v1.tasks.store'), [
            'title' => 'Imposto', 'date' => self::TODAY, 'checklist' => [$first, $second],
        ])->assertCreated()
            ->assertJsonPath('data.checklist', [[...$first, 'text' => 'Separar documentos'], $second])
            ->json('data.id');

        $this->patchJson(route('api.v1.tasks.update', $id), ['checklist' => [$second, $first]])
            ->assertOk()
            ->assertJsonPath('data.checklist.0.id', $second['id'])
            ->assertJsonPath('data.checklist.1.id', $first['id']);

        $this->patchJson(route('api.v1.tasks.update', $id), ['title' => 'Imposto de renda'])
            ->assertOk()->assertJsonCount(2, 'data.checklist');

        $this->patchJson(route('api.v1.tasks.update', $id), ['checklist' => []])
            ->assertOk()->assertJsonPath('data.checklist', []);
    }

    public function test_rejects_invalid_checklists(): void
    {
        $item = $this->item('Item');
        $invalid = [
            'repeated ids' => [$item, [...$item, 'text' => 'Outro']],
            'blank text' => [$this->item('   ')],
            'long text' => [$this->item(str_repeat('a', 256))],
            'missing done' => [['id' => (string) Str::uuid7(), 'text' => 'Item']],
            'string done' => [[...$item, 'done' => 'sim']],
            'bad id' => [[...$item, 'id' => 'abc']],
            'extra key' => [[...$item, 'position' => 1]],
            'not a list' => ['a' => $item],
            'too many' => array_map(fn (int $i): array => $this->item("Item {$i}"), range(1, 51)),
        ];

        foreach ($invalid as $case => $checklist) {
            $response = $this->postJson(route('api.v1.tasks.store'), ['title' => 'X', 'date' => self::TODAY, 'checklist' => $checklist]);
            $response->assertUnprocessable();
            $this->assertArrayHasKey('checklist', $response->json('errors'), $case);
        }
        $this->assertDatabaseCount('tasks', 0);
    }

    public function test_next_occurrence_receives_only_the_pending_items(): void
    {
        $warmUp = $this->item('Aquecer', done: true);
        $workout = $this->item('Treino');
        $task = Task::factory()->for($this->user)->on(self::TODAY)->recurring()->create([
            'checklist' => [$warmUp, $workout],
        ]);

        $this->postJson(route('api.v1.tasks.toggle', $task), ['today' => self::TODAY])
            ->assertOk()->assertJsonPath('data.checklist', [$warmUp, $workout]);

        $next = Task::findOrFail(OccurrenceId::for($task->id, '2026-11-11'));
        $this->assertSame([$workout], $next->checklist);
    }

    public function test_sync_stores_the_checklist_and_allows_the_same_item_ids_in_different_tasks(): void
    {
        $item = $this->item('Comprar pão');
        $task = $this->syncedTask(['checklist' => [$item]]);
        $occurrence = $this->syncedTask(['checklist' => [$item]]);

        $response = $this->sync([$task, $occurrence])->assertOk();

        $this->assertSame([[$item], [$item]], $response->json('data.tasks.*.checklist'));
        $this->assertSame([$item], Task::findOrFail($task['id'])->checklist);
    }

    public function test_sync_from_an_app_without_checklist_keeps_the_stored_items(): void
    {
        $item = $this->item('Do site');
        $task = $this->syncedTask(['checklist' => [$item]]);
        $this->sync([$task])->assertOk();

        $older = $this->syncedTask(['id' => $task['id'], 'title' => 'Editada no app antigo', 'updated_at' => $task['updated_at'] + 1]);
        unset($older['checklist']);

        $this->sync([$older], cursor: 1)->assertOk()
            ->assertJsonPath('data.tasks.0.title', 'Editada no app antigo')
            ->assertJsonPath('data.tasks.0.checklist', [$item]);
    }

    public function test_sync_rejects_an_invalid_checklist_without_writing(): void
    {
        $item = $this->item('Item');

        $this->sync([$this->syncedTask(['checklist' => [$item, $item]])])
            ->assertUnprocessable()->assertJsonValidationErrors('tasks.0.checklist');
        $this->assertDatabaseCount('tasks', 0);
    }
}
