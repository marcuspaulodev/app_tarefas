<?php

namespace App\Actions\Tags;

use App\Models\Tag;
use App\Models\Task;
use Illuminate\Support\Facades\DB;

class DeleteTag
{
    /**
     * Exclusão lógica da tag. Ela sai das tarefas que a usavam, e essas tarefas ganham
     * nova versão, já que as tags fazem parte da tarefa (mesma regra do app).
     */
    public function handle(Tag $tag): void
    {
        DB::transaction(function () use ($tag): void {
            $version = $tag->user->claimNextSyncVersion();

            $taskIds = $tag->tasks()->pluck('tasks.id');
            if ($taskIds->isNotEmpty()) {
                Task::whereKey($taskIds)->update(['sync_version' => $version]);
                $tag->tasks()->detach();
            }

            $tag->sync_version = $version;
            $tag->save();
            $tag->delete();
        });
    }
}
