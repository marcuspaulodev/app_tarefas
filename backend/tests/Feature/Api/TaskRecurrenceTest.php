<?php

namespace Tests\Feature\Api;

use App\Models\Tag;
use App\Models\Task;
use App\Models\User;
use App\Support\OccurrenceId;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class TaskRecurrenceTest extends TestCase
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

    private function toggle(Task $task, string $today = self::TODAY): void
    {
        $this->postJson(route('api.v1.tasks.toggle', $task), ['today' => $today])->assertOk();
    }

    public function test_completing_a_recurring_task_creates_the_next_occurrence(): void
    {
        $tag = Tag::factory()->for($this->user)->create();
        $task = Task::factory()->for($this->user)->on(self::TODAY)->recurring()->create(['deadline' => self::TODAY]);
        $task->tags()->attach($tag);

        $this->toggle($task);

        $next = Task::where('date', '2026-11-11')->sole();
        $this->assertSame(OccurrenceId::for($task->id, '2026-11-11'), $next->id);
        $this->assertSame($task->id, $next->series_id);
        $this->assertSame($task->id, $task->fresh()->series_id);
        $this->assertSame($task->title, $next->title);
        $this->assertSame('DAILY', $next->recurrence);
        $this->assertFalse($next->completed);
        $this->assertNull($next->deadline);
        $this->assertSame([$tag->id], $next->tags()->pluck('tags.id')->all());
    }

    public function test_completing_again_does_not_duplicate_the_next_occurrence(): void
    {
        $task = Task::factory()->for($this->user)->on(self::TODAY)->recurring()->create();

        $this->toggle($task);
        $this->toggle($task);
        $this->toggle($task);

        $this->assertSame(1, Task::where('date', '2026-11-11')->count());
    }

    public function test_a_deleted_occurrence_does_not_come_back(): void
    {
        $task = Task::factory()->for($this->user)->on(self::TODAY)->recurring()->create();
        $this->toggle($task);
        $this->deleteJson(route('api.v1.tasks.destroy', Task::where('date', '2026-11-11')->sole()))->assertNoContent();

        $this->toggle($task);
        $this->toggle($task);

        $this->assertSame(0, Task::where('date', '2026-11-11')->count());
        $this->assertSame(1, Task::withTrashed()->where('date', '2026-11-11')->count());
    }

    public function test_weekly_rules_jump_to_the_next_chosen_weekday(): void
    {
        $task = Task::factory()->for($this->user)->on('2026-11-13')->recurring('WEEKLY:1,3,5')->create();

        $this->toggle($task, '2026-11-13');

        $this->assertSame(1, Task::where('date', '2026-11-16')->count());
    }

    public function test_an_overdue_recurring_task_continues_from_today(): void
    {
        $task = Task::factory()->for($this->user)->on('2026-11-05')->recurring()->create();

        $this->toggle($task);

        $this->assertSame(1, Task::where('date', '2026-11-11')->count());
        $this->assertSame(0, Task::where('date', '2026-11-06')->count());
    }

    public function test_completing_a_task_without_recurrence_creates_nothing(): void
    {
        $task = Task::factory()->for($this->user)->on(self::TODAY)->create();

        $this->toggle($task);

        $this->assertSame(1, Task::count());
        $this->assertNull($task->fresh()->series_id);
    }
}
