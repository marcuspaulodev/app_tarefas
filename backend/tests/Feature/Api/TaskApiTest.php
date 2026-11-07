<?php

namespace Tests\Feature\Api;

use App\Models\Tag;
use App\Models\Task;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class TaskApiTest extends TestCase
{
    use RefreshDatabase;

    private const TODAY = '2026-11-10';

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();

        $this->user = User::factory()->create();
        Sanctum::actingAs($this->user);
    }

    /**
     * @return list<string>
     */
    private function titlesOn(string $date): array
    {
        return $this->getJson(route('api.v1.tasks.index', ['date' => $date, 'today' => self::TODAY]))
            ->assertOk()
            ->json('data.*.title');
    }

    public function test_creates_a_task_with_a_server_generated_id(): void
    {
        $tag = Tag::factory()->for($this->user)->create();

        $response = $this->postJson(route('api.v1.tasks.store'), [
            'title' => 'Relatório',
            'date' => '2026-11-12',
            'deadline' => '2026-11-15',
            'priority' => 'HIGH',
            'tag_ids' => [$tag->id],
        ]);

        $response->assertCreated()
            ->assertJsonPath('data.title', 'Relatório')
            ->assertJsonPath('data.description', '')
            ->assertJsonPath('data.date', '2026-11-12')
            ->assertJsonPath('data.original_date', '2026-11-12')
            ->assertJsonPath('data.deadline', '2026-11-15')
            ->assertJsonPath('data.priority', 'HIGH')
            ->assertJsonPath('data.completed', false)
            ->assertJsonPath('data.tag_ids', [$tag->id]);
        $this->assertTrue(Str::isUuid($response->json('data.id')));
    }

    public function test_keeps_the_id_generated_by_the_device(): void
    {
        $id = (string) Str::uuid7();

        $this->postJson(route('api.v1.tasks.store'), ['id' => $id, 'title' => 'Offline', 'date' => self::TODAY])
            ->assertCreated()
            ->assertJsonPath('data.id', $id)
            ->assertJsonPath('data.priority', 'MEDIUM');
    }

    public function test_rejects_recurrences_the_app_does_not_understand(): void
    {
        foreach (['MONTHLY', 'WEEKLY:', 'WEEKLY:8', 'WEEKLY:1,x'] as $recurrence) {
            $this->postJson(route('api.v1.tasks.store'), ['title' => 'x', 'date' => self::TODAY, 'recurrence' => $recurrence])
                ->assertUnprocessable()
                ->assertJsonValidationErrors('recurrence');
        }
    }

    public function test_pending_overdue_tasks_show_up_today_instead_of_their_old_day(): void
    {
        Task::factory()->for($this->user)->on('2026-11-05')->create(['title' => 'Atrasada']);
        Task::factory()->for($this->user)->on(self::TODAY)->create(['title' => 'De hoje']);
        Task::factory()->for($this->user)->on('2026-11-09')->completed()->create(['title' => 'Concluída']);
        Task::factory()->for($this->user)->on('2026-11-11')->create(['title' => 'Futura']);

        $this->assertEqualsCanonicalizing(['Atrasada', 'De hoje'], $this->titlesOn(self::TODAY));
        $this->assertSame([], $this->titlesOn('2026-11-05'));
        $this->assertSame(['Concluída'], $this->titlesOn('2026-11-09'));
        $this->assertSame(['Futura'], $this->titlesOn('2026-11-11'));
    }

    public function test_pending_counts_add_the_overdue_tasks_to_today(): void
    {
        Task::factory()->for($this->user)->on('2026-11-01')->create();
        Task::factory()->for($this->user)->on('2026-11-09')->create();
        Task::factory()->for($this->user)->on(self::TODAY)->create();
        Task::factory()->for($this->user)->on('2026-11-12')->create();
        Task::factory()->for($this->user)->on('2026-11-08')->completed()->create();
        Task::factory()->on(self::TODAY)->create();

        $this->getJson(route('api.v1.tasks.pending-counts', ['start' => '2026-11-01', 'end' => '2026-11-30', 'today' => self::TODAY]))
            ->assertOk()
            ->assertExactJson(['data' => [
                ['date' => self::TODAY, 'count' => 3],
                ['date' => '2026-11-12', 'count' => 1],
            ]]);
    }

    public function test_editing_without_changing_the_shown_day_keeps_the_overdue_mark(): void
    {
        $task = Task::factory()->for($this->user)->on('2026-11-05')->create();

        $this->patchJson(route('api.v1.tasks.update', $task), ['title' => 'Renomeada', 'date' => self::TODAY, 'today' => self::TODAY])
            ->assertOk()
            ->assertJsonPath('data.title', 'Renomeada')
            ->assertJsonPath('data.date', '2026-11-05')
            ->assertJsonPath('data.original_date', '2026-11-05');
    }

    public function test_moving_a_task_to_another_day_restarts_the_overdue_count(): void
    {
        $task = Task::factory()->for($this->user)->on('2026-11-05')->create();

        $this->patchJson(route('api.v1.tasks.update', $task), ['date' => '2026-11-12', 'today' => self::TODAY])
            ->assertOk()
            ->assertJsonPath('data.date', '2026-11-12')
            ->assertJsonPath('data.original_date', '2026-11-12');
    }

    public function test_changing_the_date_requires_the_device_today(): void
    {
        $task = Task::factory()->for($this->user)->create();

        $this->patchJson(route('api.v1.tasks.update', $task), ['date' => '2026-11-12'])
            ->assertUnprocessable()
            ->assertJsonValidationErrors('today');
    }

    public function test_tags_change_only_when_sent(): void
    {
        $tag = Tag::factory()->for($this->user)->create();
        $task = Task::factory()->for($this->user)->create();
        $task->tags()->attach($tag);

        $this->patchJson(route('api.v1.tasks.update', $task), ['title' => 'Só o título'])
            ->assertJsonPath('data.tag_ids', [$tag->id]);
        $this->patchJson(route('api.v1.tasks.update', $task), ['tag_ids' => []])
            ->assertJsonPath('data.tag_ids', []);
    }

    public function test_completing_an_overdue_task_records_today_and_keeps_the_original_date(): void
    {
        $task = Task::factory()->for($this->user)->on('2026-11-05')->create();

        $this->postJson(route('api.v1.tasks.toggle', $task), ['today' => self::TODAY])
            ->assertOk()
            ->assertJsonPath('data.completed', true)
            ->assertJsonPath('data.date', self::TODAY)
            ->assertJsonPath('data.original_date', '2026-11-05');

        $this->postJson(route('api.v1.tasks.toggle', $task), ['today' => self::TODAY])
            ->assertOk()
            ->assertJsonPath('data.completed', false)
            ->assertJsonPath('data.date', self::TODAY);
    }

    public function test_deleting_a_task_hides_it_but_keeps_the_row_for_sync(): void
    {
        $task = Task::factory()->for($this->user)->on(self::TODAY)->create();

        $this->deleteJson(route('api.v1.tasks.destroy', $task))->assertNoContent();

        $this->assertSoftDeleted($task);
        $this->assertSame([], $this->titlesOn(self::TODAY));
        $this->getJson(route('api.v1.tasks.show', $task))->assertNotFound();
    }

    public function test_search_looks_at_title_and_description_across_dates(): void
    {
        Task::factory()->for($this->user)->on('2026-10-01')->create(['title' => 'Relatório mensal']);
        Task::factory()->for($this->user)->on('2026-12-01')->create(['title' => 'Outra', 'description' => 'parte do relatório']);
        Task::factory()->for($this->user)->create(['title' => 'Mercado']);

        $titles = $this->getJson(route('api.v1.tasks.index', ['search' => 'relatório']))->assertOk()->json('data.*.title');

        $this->assertSame(['Outra', 'Relatório mensal'], $titles);
    }

    public function test_search_treats_wildcards_as_plain_text(): void
    {
        Task::factory()->for($this->user)->create(['title' => '100% feito']);
        Task::factory()->for($this->user)->create(['title' => 'Mercado']);

        $titles = $this->getJson(route('api.v1.tasks.index', ['search' => '%']))->json('data.*.title');

        $this->assertSame(['100% feito'], $titles);
    }

    public function test_listing_needs_a_day_or_a_search(): void
    {
        $this->getJson(route('api.v1.tasks.index'))->assertUnprocessable()->assertJsonValidationErrors(['date', 'search']);
        $this->getJson(route('api.v1.tasks.index', ['date' => self::TODAY]))->assertUnprocessable()->assertJsonValidationErrors('today');
    }

    public function test_upcoming_range_returns_only_owned_pending_tasks_in_the_interval(): void
    {
        Task::factory()->for($this->user)->on('2026-11-05')->create(['title' => 'Atrasada']);
        Task::factory()->for($this->user)->on('2026-11-11')->create(['title' => 'Amanhã']);
        Task::factory()->for($this->user)->on('2026-11-17')->create(['title' => 'Último dia']);
        Task::factory()->for($this->user)->on('2026-11-18')->create(['title' => 'Fora']);
        Task::factory()->for($this->user)->on('2026-11-12')->completed()->create();
        Task::factory()->on('2026-11-11')->create();

        $this->getJson(route('api.v1.tasks.index', [
            'start' => '2026-11-11', 'end' => '2026-11-17', 'today' => self::TODAY, 'completed' => 0,
        ]))->assertOk()->assertJsonPath('data.*.title', ['Amanhã', 'Último dia']);
    }

    public function test_date_range_uses_the_shown_day_for_overdue_tasks(): void
    {
        Task::factory()->for($this->user)->on('2026-11-05')->create(['title' => 'Atrasada']);
        Task::factory()->for($this->user)->on('2026-11-05')->completed()->create(['title' => 'Já concluída']);

        $this->getJson(route('api.v1.tasks.index', [
            'start' => self::TODAY, 'end' => self::TODAY, 'today' => self::TODAY,
        ]))->assertOk()->assertJsonPath('data.*.title', ['Atrasada']);
    }

    public function test_completed_view_returns_only_owned_completed_tasks_from_all_dates(): void
    {
        Task::factory()->for($this->user)->on('2026-11-01')->completed()->create(['title' => 'Antiga']);
        Task::factory()->for($this->user)->on('2026-11-10')->completed()->create(['title' => 'Recente']);
        Task::factory()->for($this->user)->on('2026-11-10')->create();
        Task::factory()->on('2026-11-10')->completed()->create();

        $this->getJson(route('api.v1.tasks.index', ['completed' => 1]))
            ->assertOk()->assertJsonPath('data.*.title', ['Recente', 'Antiga']);
    }

    public function test_range_requires_an_end_today_and_a_valid_order(): void
    {
        $this->getJson(route('api.v1.tasks.index', ['start' => self::TODAY]))
            ->assertUnprocessable()->assertJsonValidationErrors(['end', 'today']);
        $this->getJson(route('api.v1.tasks.index', ['start' => self::TODAY, 'end' => '2026-11-09', 'today' => self::TODAY]))
            ->assertUnprocessable()->assertJsonValidationErrors('end');
    }
}
