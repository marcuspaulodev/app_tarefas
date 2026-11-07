<?php

namespace App\Models;

use App\Enums\Priority;
use Database\Factories\TaskFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\SoftDeletes;

/**
 * As datas (date, original_date, deadline) ficam como texto Y-m-d, igual ao app e
 * sem fuso horário: "hoje" é sempre o dia do aparelho do usuário, enviado pelo cliente.
 *
 * @property string $date
 * @property string $original_date
 * @property ?string $deadline
 */
#[Fillable(['id', 'title', 'description', 'date', 'original_date', 'deadline', 'priority', 'recurrence', 'series_id', 'completed'])]
class Task extends Model
{
    /** @use HasFactory<TaskFactory> */
    use HasFactory, HasUuids, SoftDeletes;

    /**
     * Milissegundos nos horários, como no app.
     *
     * @var string
     */
    protected $dateFormat = 'Y-m-d H:i:s.v';

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, mixed>
     */
    protected function casts(): array
    {
        return [
            'priority' => Priority::class,
            'completed' => 'boolean',
            'sync_version' => 'integer',
        ];
    }

    /**
     * @return BelongsTo<User, $this>
     */
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    /**
     * @return BelongsToMany<Tag, $this>
     */
    public function tags(): BelongsToMany
    {
        return $this->belongsToMany(Tag::class, 'task_tag');
    }

    /**
     * Dia em que a tarefa aparece: uma pendente cuja data já passou fica em "hoje".
     * Mesma regra de Task.effectiveDate() no app e de shared/fixtures/rules.json.
     */
    public function shownOn(string $today): string
    {
        return (! $this->completed && $this->date < $today) ? $today : $this->date;
    }

    /**
     * Aplica a data escolhida na edição. Manter o dia exibido preserva o "atrasada desde".
     */
    public function rescheduleTo(string $date, string $today): void
    {
        if ($date !== $this->shownOn($today)) {
            $this->date = $date;
            $this->original_date = $date;
        }
    }

    /**
     * Tarefas que aparecem no dia informado, pela mesma regra de shownOn().
     *
     * @param  Builder<Task>  $query
     */
    #[Scope]
    protected function visibleOn(Builder $query, string $date, string $today): void
    {
        $query->whereRaw('(CASE WHEN completed = 0 AND date < ? THEN ? ELSE date END) = ?', [$today, $today, $date]);
    }
}
