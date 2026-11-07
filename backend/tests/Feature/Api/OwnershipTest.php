<?php

namespace Tests\Feature\Api;

use App\Models\Tag;
use App\Models\Task;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

/**
 * Um usuário nunca lê nem altera dados de outro, e a resposta (404) não revela que existem.
 */
class OwnershipTest extends TestCase
{
    use RefreshDatabase;

    private Task $othersTask;

    private Tag $othersTag;

    protected function setUp(): void
    {
        parent::setUp();

        $owner = User::factory()->create();
        $this->othersTask = Task::factory()->for($owner)->on('2026-11-10')->recurring()->create(['title' => 'Do dono']);
        $this->othersTag = Tag::factory()->for($owner)->create();
        $this->othersTask->tags()->attach($this->othersTag);

        Sanctum::actingAs(User::factory()->create());
    }

    public function test_cannot_read_or_change_another_users_task(): void
    {
        $task = $this->othersTask;

        $this->getJson(route('api.v1.tasks.show', $task))->assertNotFound();
        $this->patchJson(route('api.v1.tasks.update', $task), ['title' => 'Invadida'])->assertNotFound();
        $this->postJson(route('api.v1.tasks.toggle', $task), ['today' => '2026-11-10'])->assertNotFound();
        $this->deleteJson(route('api.v1.tasks.destroy', $task))->assertNotFound();

        $fresh = $task->fresh();
        $this->assertSame('Do dono', $fresh->title);
        $this->assertFalse($fresh->completed);
        $this->assertNotSoftDeleted($fresh);
        $this->assertSame(1, Task::count());
    }

    public function test_cannot_read_or_change_another_users_tag(): void
    {
        $tag = $this->othersTag;

        $this->getJson(route('api.v1.tags.show', $tag))->assertNotFound();
        $this->patchJson(route('api.v1.tags.update', $tag), ['name' => 'Invadida'])->assertNotFound();
        $this->deleteJson(route('api.v1.tags.destroy', $tag))->assertNotFound();

        $this->assertNotSoftDeleted($tag->fresh());
        $this->assertSame(1, $this->othersTask->tags()->count());
    }

    public function test_lists_and_counts_only_show_the_users_own_data(): void
    {
        $this->getJson(route('api.v1.tasks.index', ['date' => '2026-11-10', 'today' => '2026-11-10']))->assertExactJson(['data' => []]);
        $this->getJson(route('api.v1.tasks.index', ['search' => 'Do dono']))->assertExactJson(['data' => []]);
        $this->getJson(route('api.v1.tags.index'))->assertExactJson(['data' => []]);
        $this->getJson(route('api.v1.tasks.pending-counts', ['start' => '2026-11-01', 'end' => '2026-11-30', 'today' => '2026-11-10']))
            ->assertExactJson(['data' => []]);
    }

    public function test_cannot_attach_another_users_tag(): void
    {
        $this->postJson(route('api.v1.tasks.store'), ['title' => 'x', 'date' => '2026-11-10', 'tag_ids' => [$this->othersTag->id]])
            ->assertUnprocessable()
            ->assertJsonValidationErrors('tag_ids.0');
    }
}
