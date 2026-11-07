<?php

namespace Tests\Feature\Api;

use App\Models\Tag;
use App\Models\Task;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Str;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class TagApiTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    protected function setUp(): void
    {
        parent::setUp();

        $this->user = User::factory()->create();
        Sanctum::actingAs($this->user);
    }

    public function test_lists_the_user_tags_by_name_without_the_deleted_ones(): void
    {
        Tag::factory()->for($this->user)->create(['name' => 'Trabalho']);
        Tag::factory()->for($this->user)->create(['name' => 'Casa']);
        Tag::factory()->for($this->user)->create(['name' => 'Antiga'])->delete();

        $this->getJson(route('api.v1.tags.index'))
            ->assertOk()
            ->assertJsonPath('data.*.name', ['Casa', 'Trabalho']);
    }

    public function test_creates_a_tag_keeping_the_device_id(): void
    {
        $id = (string) Str::uuid7();

        $this->postJson(route('api.v1.tags.store'), ['id' => $id, 'name' => 'Academia', 'color_hex' => '#3D5AFE'])
            ->assertCreated()
            ->assertJsonPath('data.id', $id)
            ->assertJsonPath('data.name', 'Academia')
            ->assertJsonPath('data.color_hex', '#3D5AFE');
    }

    public function test_rejects_an_invalid_color(): void
    {
        $this->postJson(route('api.v1.tags.store'), ['name' => 'Academia', 'color_hex' => 'azul'])
            ->assertUnprocessable()
            ->assertJsonValidationErrors('color_hex');
    }

    public function test_updates_a_tag(): void
    {
        $tag = Tag::factory()->for($this->user)->create(['name' => 'Casa']);

        $this->patchJson(route('api.v1.tags.update', $tag), ['name' => 'Lar'])
            ->assertOk()
            ->assertJsonPath('data.name', 'Lar')
            ->assertJsonPath('data.color_hex', $tag->color_hex);
    }

    public function test_deleting_a_tag_removes_it_from_the_tasks(): void
    {
        $tag = Tag::factory()->for($this->user)->create();
        $task = Task::factory()->for($this->user)->create();
        $task->tags()->attach($tag);

        $this->deleteJson(route('api.v1.tags.destroy', $tag))->assertNoContent();

        $this->assertSoftDeleted($tag);
        $this->getJson(route('api.v1.tasks.show', $task))->assertOk()->assertJsonPath('data.tag_ids', []);
    }

    public function test_a_deleted_tag_can_no_longer_be_used_in_tasks(): void
    {
        $tag = Tag::factory()->for($this->user)->create();
        $tag->delete();

        $this->postJson(route('api.v1.tasks.store'), ['title' => 'x', 'date' => '2026-11-10', 'tag_ids' => [$tag->id]])
            ->assertUnprocessable()
            ->assertJsonValidationErrors('tag_ids.0');
    }
}
