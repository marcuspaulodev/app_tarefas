<?php

namespace App\Actions\Tags;

use App\Models\Tag;
use App\Models\Task;

class DetachTagFromTasks
{
    /**
     * Executado sob a trava do usuário. A remoção precisa chegar aos aparelhos
     * que já receberam as tarefas, inclusive as excluídas.
     */
    public function handle(Tag $tag, int $version): void
    {
        foreach ($tag->tasks()->withTrashed()->get() as $task) {
            $task->sync_version = $version;
            if ($tag->updated_at->greaterThan($task->updated_at)) {
                $task->updated_at = $tag->updated_at;
            }
            Task::withoutTimestamps(fn (): bool => $task->save());
        }

        $tag->tasks()->detach();
    }
}
