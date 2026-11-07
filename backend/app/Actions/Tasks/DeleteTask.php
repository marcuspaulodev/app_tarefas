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
            $task->sync_version = $task->user->claimNextSyncVersion();
            $task->save();
            $task->delete();
        });
    }
}
