<?php

namespace App\Actions\Tasks;

use App\Models\Task;
use App\Support\Checklist;
use App\Support\OccurrenceId;
use App\Support\RecurrenceRule;
use Illuminate\Support\Facades\DB;

class ToggleTaskCompletion
{
    /**
     * Alterna a conclusão, com as mesmas regras de TaskRepository.toggleCompleted() no app.
     *
     * Concluir registra o dia em que a tarefa estava aparecendo. Se ela é recorrente,
     * cria a próxima ocorrência com ID derivado da série e da data: concluir de novo,
     * aqui ou no app, não duplica, e uma ocorrência excluída não volta. Do checklist,
     * a próxima ocorrência recebe só os itens que ficaram pendentes.
     */
    public function handle(Task $task, string $today): Task
    {
        return DB::transaction(function () use ($task, $today): Task {
            $user = $task->user;
            $version = $user->claimNextSyncVersion();
            $task = $user->tasks()->findOrFail($task->id);
            $isCompleting = ! $task->completed;
            $rule = $isCompleting ? RecurrenceRule::decode($task->recurrence) : null;

            if ($isCompleting) {
                $task->date = $task->shownOn($today);
            }
            if ($rule !== null) {
                $task->series_id ??= $task->id;
            }
            $task->completed = $isCompleting;
            $task->sync_version = $version;
            $task->save();

            if ($rule !== null) {
                $nextDate = $rule->nextOccurrenceAfter($task->date);
                $nextId = OccurrenceId::for($task->series_id, $nextDate);

                if (! Task::withTrashed()->whereKey($nextId)->exists()) {
                    $next = $user->tasks()->make([
                        'id' => $nextId,
                        'title' => $task->title,
                        'description' => $task->description,
                        'checklist' => Checklist::forNextOccurrence($task->checklist),
                        'date' => $nextDate,
                        'original_date' => $nextDate,
                        'deadline' => null,
                        'priority' => $task->priority,
                        'recurrence' => $task->recurrence,
                        'series_id' => $task->series_id,
                        'completed' => false,
                    ]);
                    $next->sync_version = $version;
                    $next->save();
                    $next->tags()->sync($task->tags()->pluck('tags.id'));
                }
            }

            return $task->load('tags');
        });
    }
}
