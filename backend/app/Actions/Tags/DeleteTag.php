<?php

namespace App\Actions\Tags;

use App\Models\Tag;
use Illuminate\Support\Facades\DB;

class DeleteTag
{
    public function __construct(private DetachTagFromTasks $detachTagFromTasks) {}

    /**
     * Exclusão lógica da tag. Ela sai das tarefas que a usavam, e essas tarefas ganham
     * nova versão, já que as tags fazem parte da tarefa (mesma regra do app).
     */
    public function handle(Tag $tag): void
    {
        DB::transaction(function () use ($tag): void {
            $user = $tag->user;
            $version = $user->claimNextSyncVersion();
            $tag = $user->tags()->findOrFail($tag->id);
            $tag->sync_version = $version;
            $tag->save();
            $tag->delete();
            $this->detachTagFromTasks->handle($tag, $version);
        });
    }
}
