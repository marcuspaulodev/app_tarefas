<?php

namespace App\Actions\Tasks;

use App\Enums\Priority;
use App\Http\Requests\Api\V1\OwnedTag;
use App\Models\Task;
use App\Models\User;
use Illuminate\Support\Facades\DB;

class CreateTask
{
    /**
     * @param  array{id?: ?string, title: string, description?: ?string, checklist?: ?list<array{id: string, text: string, done: bool}>, date: string, deadline?: ?string, priority?: ?string, recurrence?: ?string}  $attributes
     * @param  list<string>  $tagIds
     */
    public function handle(User $user, array $attributes, array $tagIds = []): Task
    {
        return DB::transaction(function () use ($user, $attributes, $tagIds): Task {
            $version = $user->claimNextSyncVersion();
            OwnedTag::validateIds($user, $tagIds);

            $task = $user->tasks()->make([
                ...array_filter($attributes, fn (mixed $value): bool => $value !== null),
                'description' => $attributes['description'] ?? '',
                'original_date' => $attributes['date'],
                'priority' => $attributes['priority'] ?? Priority::Medium,
                'completed' => false,
            ]);
            $task->sync_version = $version;
            $task->save();
            $task->tags()->sync($tagIds);

            return $task->load('tags');
        });
    }
}
