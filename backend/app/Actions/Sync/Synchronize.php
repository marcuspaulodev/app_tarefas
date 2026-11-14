<?php

namespace App\Actions\Sync;

use App\Actions\Tags\DetachTagFromTasks;
use App\Models\Tag;
use App\Models\Task;
use App\Models\User;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Collection;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

class Synchronize
{
    public function __construct(private DetachTagFromTasks $detachTagFromTasks) {}

    /**
     * O cursor mede escritas confirmadas, não o relógio dos aparelhos. A trava do
     * usuário serializa tanto as escritas quanto a leitura que produz o cursor.
     *
     * @param  list<array{id: string, title: string, description: ?string, checklist?: list<array{id: string, text: string, done: bool}>, date: string, original_date: string, deadline: ?string, priority: string, recurrence: ?string, series_id: ?string, completed: bool, created_at: int, updated_at: int, deleted_at: ?int, tag_ids: list<string>}>  $tasks
     * @param  list<array{id: string, name: string, color_hex: string, created_at?: int, updated_at: int, deleted_at: ?int}>  $tags
     * @return array{cursor: int, server_time: int, tasks: Collection<int, Task>, tags: Collection<int, Tag>}
     */
    public function handle(User $user, int $cursor, array $tasks, array $tags): array
    {
        return DB::transaction(function () use ($user, $cursor, $tasks, $tags): array {
            $currentVersion = (int) DB::table('users')->where('id', $user->id)->lockForUpdate()->value('sync_version');
            if ($cursor > $currentVersion) {
                throw ValidationException::withMessages(['cursor' => 'O cursor não pertence ao estado atual desta conta. Recomece com 0.']);
            }

            $tasks = $this->normalizeIds($tasks);
            $tags = $this->normalizeIds($tags);
            $taskIds = array_column($tasks, 'id');
            $tagIds = array_column($tags, 'id');
            $referencedTagIds = array_values(array_unique(array_merge($tagIds, ...array_column($tasks, 'tag_ids'))));
            $storedTasks = Task::withTrashed()->whereKey($taskIds)->get()->keyBy(fn (Task $task): string => strtolower($task->id));
            $storedTags = Tag::withTrashed()->whereKey($referencedTagIds)->get()->keyBy(fn (Tag $tag): string => strtolower($tag->id));

            $this->validateOwnership($user, $tasks, $tags, $storedTasks, $storedTags);

            $version = null;
            $deletedTags = [];
            foreach ($tags as $attributes) {
                $tag = $storedTags->get($attributes['id']);
                if (! $this->shouldApply($tag, $attributes)) {
                    continue;
                }

                $version ??= $user->claimNextSyncVersion();
                $tag ??= $user->tags()->make(['id' => $attributes['id']]);
                $tag->fill(['name' => $attributes['name'], 'color_hex' => $attributes['color_hex']]);
                $this->stamp($tag, $attributes, $version);
                $tag->save();
                $storedTags->put($attributes['id'], $tag);
                if ($tag->trashed()) {
                    $deletedTags[] = $tag;
                }
            }

            foreach ($tasks as $attributes) {
                $task = $storedTasks->get($attributes['id']);
                if (! $this->shouldApply($task, $attributes)) {
                    continue;
                }

                $version ??= $user->claimNextSyncVersion();
                $task ??= $user->tasks()->make(['id' => $attributes['id']]);
                $task->fill([
                    'title' => $attributes['title'],
                    'description' => $attributes['description'] ?? '',
                    'date' => $attributes['date'],
                    'original_date' => $attributes['original_date'],
                    'deadline' => $attributes['deadline'],
                    'priority' => $attributes['priority'],
                    'recurrence' => $attributes['recurrence'],
                    'series_id' => $attributes['series_id'],
                    'completed' => $attributes['completed'],
                ]);
                if (array_key_exists('checklist', $attributes)) {
                    $task->checklist = $attributes['checklist'];
                }
                $this->stamp($task, $attributes, $version);
                $task->save();
                $task->tags()->sync(array_values(array_filter(
                    $attributes['tag_ids'],
                    fn (string $id): bool => ! $storedTags->get($id)->trashed(),
                )));
            }

            // Só remove após aplicar as tarefas do lote, para não descartar uma
            // edição do próprio lote por causa do horário da remoção da tag.
            foreach ($deletedTags as $tag) {
                $this->detachTagFromTasks->handle($tag, $version);
            }

            return [
                'cursor' => $version ?? $currentVersion,
                'server_time' => now()->getTimestampMs(),
                'tasks' => $user->tasks()->withTrashed()->with('tags')
                    ->where(fn (Builder $query) => $this->changedOrSubmitted($query, $cursor, $taskIds))
                    ->orderBy('sync_version')->orderBy('id')->get(),
                'tags' => $user->tags()->withTrashed()
                    ->where(fn (Builder $query) => $this->changedOrSubmitted($query, $cursor, $tagIds))
                    ->orderBy('sync_version')->orderBy('id')->get(),
            ];
        }, attempts: 3);
    }

    /**
     * Empates mantêm o servidor. IDs já excluídos não são reutilizados: o app
     * não tem restauração, e recriar uma ocorrência offline não deve ressuscitá-la.
     *
     * @param  array{updated_at: int, deleted_at: ?int}  $attributes
     */
    private function shouldApply(Task|Tag|null $record, array $attributes): bool
    {
        if ($record === null) {
            return true;
        }

        return (! $record->trashed() || $attributes['deleted_at'] !== null)
            && (int) $attributes['updated_at'] > $record->updated_at->getTimestampMs();
    }

    /**
     * @param  array{created_at?: int, updated_at: int, deleted_at: ?int}  $attributes
     */
    private function stamp(Model $record, array $attributes, int $version): void
    {
        if (! $record->exists) {
            $record->created_at = Carbon::createFromTimestampMs($attributes['created_at'] ?? $attributes['updated_at'], 'UTC');
        }
        $record->updated_at = Carbon::createFromTimestampMs($attributes['updated_at'], 'UTC');
        $record->deleted_at = $attributes['deleted_at'] === null
            ? null
            : Carbon::createFromTimestampMs($attributes['deleted_at'], 'UTC');
        $record->sync_version = $version;
    }

    /**
     * @param  list<array<string, mixed>>  $records
     * @return list<array<string, mixed>>
     */
    private function normalizeIds(array $records): array
    {
        return array_map(function (array $record): array {
            $record['id'] = strtolower($record['id']);
            if (isset($record['series_id'])) {
                $record['series_id'] = strtolower($record['series_id']);
            }
            if (isset($record['tag_ids'])) {
                $record['tag_ids'] = array_values(array_unique(array_map(strtolower(...), $record['tag_ids'])));
            }

            return $record;
        }, $records);
    }

    /**
     * Valida sob a mesma trava das escritas. IDs indisponíveis recebem a mesma
     * mensagem, sem devolver conteúdo pertencente a outra conta.
     *
     * @param  list<array{id: string, tag_ids: list<string>}>  $tasks
     * @param  list<array{id: string}>  $tags
     * @param  Collection<string, Task>  $storedTasks
     * @param  Collection<string, Tag>  $storedTags
     */
    private function validateOwnership(User $user, array $tasks, array $tags, Collection $storedTasks, Collection $storedTags): void
    {
        $newTagIds = array_column($tags, 'id');
        foreach (['tasks' => $tasks, 'tags' => $tags] as $kind => $records) {
            $stored = $kind === 'tasks' ? $storedTasks : $storedTags;
            foreach ($records as $index => $attributes) {
                $record = $stored->get($attributes['id']);
                if ($record !== null && $record->user_id !== $user->id) {
                    throw ValidationException::withMessages(["{$kind}.{$index}.id" => 'ID indisponível para esta conta.']);
                }
            }
        }

        foreach ($tasks as $index => $attributes) {
            foreach ($attributes['tag_ids'] as $tagIndex => $id) {
                $tag = $storedTags->get($id);
                if (($tag !== null && $tag->user_id !== $user->id)
                    || ($tag === null && ! in_array($id, $newTagIds, strict: true))) {
                    throw ValidationException::withMessages(["tasks.{$index}.tag_ids.{$tagIndex}" => 'Tag indisponível para esta conta.']);
                }
            }
        }
    }

    /**
     * Inclui o estado vencedor dos IDs enviados, mesmo anteriores ao cursor.
     * Assim um conflito sempre corrige a cópia local do aparelho.
     *
     * @param  Builder<Task>|Builder<Tag>  $query
     * @param  list<string>  $ids
     */
    private function changedOrSubmitted(Builder $query, int $cursor, array $ids): void
    {
        if ($cursor > 0) {
            $query->where('sync_version', '>', $cursor)->orWhereIn('id', $ids);
        }
    }
}
