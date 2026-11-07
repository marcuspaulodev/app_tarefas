<?php

namespace App\Actions\Tasks;

use App\Enums\Priority;
use App\Models\Task;
use App\Models\User;
use Illuminate\Support\Facades\DB;

class CreateTask
{
    /**
     * @param  array{id?: ?string, title: string, description?: ?string, date: string, deadline?: ?string, priority?: ?string, recurrence?: ?string}  $attributes
     * @param  list<string>  $tagIds
     */
    public function handle(User $user, array $attributes, array $tagIds = []): Task
    {
        return DB::transaction(function () use ($user, $attributes, $tagIds): Task {
            $task = $user->tasks()->make([
                ...array_filter($attributes, fn (mixed $value): bool => $value !== null),
                'description' => $attributes['description'] ?? '',
                'original_date' => $attributes['date'],
                'priority' => $attributes['priority'] ?? Priority::Medium,
                'completed' => false,
            ]);
            $task->sync_version = $user->claimNextSyncVersion();
            $task->save();
            $task->tags()->sync($tagIds);

            return $task->load('tags');
        });
    }
}
