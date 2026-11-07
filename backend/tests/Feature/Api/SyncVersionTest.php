<?php

namespace Tests\Feature\Api;

use App\Models\Tag;
use App\Models\Task;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

/**
 * Cada escrita recebe o próximo número de versão do usuário. A sincronização vai
 * pedir "tudo depois da versão N", então nenhuma escrita pode ficar sem número.
 */
class SyncVersionTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();

        $this->user = User::factory()->create();
        Sanctum::actingAs($this->user);
    }

    private function userVersion(): int
    {
        return $this->user->fresh()->sync_version;
    }

    public function test_every_task_write_claims_the_next_version(): void
    {
        $id = $this->postJson(route('api.v1.tasks.store'), ['title' => 'x', 'date' => '2026-11-10'])->json('data.id');
        $this->assertSame(1, Task::find($id)->sync_version);

        $this->patchJson(route('api.v1.tasks.update', $id), ['title' => 'y'])->assertOk();
        $this->assertSame(2, Task::find($id)->sync_version);

        $this->postJson(route('api.v1.tasks.toggle', $id), ['today' => '2026-11-10'])->assertOk();
        $this->assertSame(3, Task::find($id)->sync_version);

        $this->deleteJson(route('api.v1.tasks.destroy', $id))->assertNoContent();
        $this->assertSame(4, Task::withTrashed()->find($id)->sync_version);
        $this->assertSame(4, $this->userVersion());
    }

    public function test_reads_do_not_change_the_version(): void
    {
        $task = Task::factory()->for($this->user)->create();

        $this->getJson(route('api.v1.tasks.show', $task))->assertOk();
        $this->getJson(route('api.v1.tasks.index', ['search' => 'x']))->assertOk();
        $this->getJson(route('api.v1.tags.index'))->assertOk();

        $this->assertSame(0, $this->userVersion());
    }

    public function test_completing_a_recurring_task_stamps_both_occurrences_with_one_version(): void
    {
        $task = Task::factory()->for($this->user)->on('2026-11-10')->recurring()->create();

        $this->postJson(route('api.v1.tasks.toggle', $task), ['today' => '2026-11-10'])->assertOk();

        $this->assertSame(1, $this->userVersion());
        $this->assertSame([1, 1], Task::orderBy('date')->pluck('sync_version')->all());
    }

    public function test_deleting_a_tag_stamps_the_tag_and_only_the_tasks_that_used_it(): void
    {
        $tag = Tag::factory()->for($this->user)->create();
        $withTag = Task::factory()->for($this->user)->create();
        $withTag->tags()->attach($tag);
        $withoutTag = Task::factory()->for($this->user)->create();

        $this->deleteJson(route('api.v1.tags.destroy', $tag))->assertNoContent();

        $this->assertSame(1, Tag::withTrashed()->find($tag->id)->sync_version);
        $this->assertSame(1, $withTag->fresh()->sync_version);
        $this->assertSame(0, $withoutTag->fresh()->sync_version);
    }

    public function test_versions_are_counted_per_user(): void
    {
        $other = User::factory()->create();
        Task::factory()->for($other)->create();

        $this->postJson(route('api.v1.tasks.store'), ['title' => 'x', 'date' => '2026-11-10'])->assertCreated();

        $this->assertSame(1, $this->userVersion());
        $this->assertSame(0, $other->fresh()->sync_version);
    }
}
