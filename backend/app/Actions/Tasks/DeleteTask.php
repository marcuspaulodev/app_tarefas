<?php

namespace App\Actions\Tasks;

use App\Models\Task;
use Illuminate\Support\Facades\DB;

class DeleteTask
{
    /**
     * Exclusão lógica: a linha fica, com nova versão, para a sincronização avisar os aparelhos.
     */
    public function handle(Task $task): void
    {
        DB::transaction(function () use ($task): void {
            $user = $task->user;
            $version = $user->claimNextSyncVersion();
            $task = $user->tasks()->findOrFail($task->id);
            $task->sync_version = $version;
            $task->save();
            $task->delete();
        });
    }
}
