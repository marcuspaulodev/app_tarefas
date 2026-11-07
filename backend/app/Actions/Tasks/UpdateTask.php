<?php

namespace App\Actions\Tasks;

use App\Models\Task;
use Illuminate\Support\Facades\DB;

class UpdateTask
{
    /**
     * Mudar a data segue a regra do app: manter o dia em que a tarefa aparece hoje
     * preserva o "atrasada desde"; escolher outro dia recomeça a contagem.
     *
     * @param  array{title?: string, description?: ?string, date?: string, deadline?: ?string, priority?: string, recurrence?: ?string}  $attributes
     * @param  list<string>|null  $tagIds  null mantém as tags atuais.
     */
    public function handle(Task $task, array $attributes, ?array $tagIds, ?string $today): Task
    {
        return DB::transaction(function () use ($task, $attributes, $tagIds, $today): Task {
            if (array_key_exists('date', $attributes)) {
                $task->rescheduleTo($attributes['date'], $today);
                unset($attributes['date']);
            }
            if (array_key_exists('description', $attributes)) {
                $attributes['description'] ??= '';
            }

            $task->fill($attributes);
            $task->sync_version = $task->user->claimNextSyncVersion();
            $task->save();

            if ($tagIds !== null) {
                $task->tags()->sync($tagIds);
            }

            return $task->load('tags');
        });
    }
}
