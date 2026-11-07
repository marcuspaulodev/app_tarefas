<?php

namespace App\Http\Controllers\Api\V1;

use App\Actions\Tasks\CreateTask;
use App\Actions\Tasks\DeleteTask;
use App\Actions\Tasks\ToggleTaskCompletion;
use App\Actions\Tasks\UpdateTask;
use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\ListTasksRequest;
use App\Http\Requests\Api\V1\PendingCountsRequest;
use App\Http\Requests\Api\V1\StoreTaskRequest;
use App\Http\Requests\Api\V1\ToggleTaskRequest;
use App\Http\Requests\Api\V1\UpdateTaskRequest;
use App\Http\Resources\TaskResource;
use App\Models\Task;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Gate;

class TaskController extends Controller
{
    /**
     * Tarefas de um dia (date + today) ou busca por texto em todas as datas (search).
     */
    public function index(ListTasksRequest $request): AnonymousResourceCollection
    {
        $query = $request->user()->tasks()->with('tags');

        if ($request->filled('search')) {
            $term = '%'.addcslashes($request->validated('search'), '%_\\').'%';
            $query->where(fn ($where) => $where->where('title', 'like', $term)->orWhere('description', 'like', $term))
                ->orderByDesc('date');
        } else {
            $query->visibleOn($request->validated('date'), $request->validated('today'))
                ->orderBy('completed')
                ->orderBy('created_at');
        }

        return TaskResource::collection($query->get());
    }

    /**
     * Quantas pendentes aparecem em cada dia do intervalo, para os indicadores do calendário.
     */
    public function pendingCounts(PendingCountsRequest $request): JsonResponse
    {
        $today = $request->validated('today');

        $counts = $request->user()->tasks()
            ->where('completed', false)
            ->selectRaw('(CASE WHEN date < ? THEN ? ELSE date END) AS shown_on, COUNT(*) AS pending', [$today, $today])
            ->groupBy('shown_on')
            ->havingBetween('shown_on', [$request->validated('start'), $request->validated('end')])
            ->orderBy('shown_on')
            ->get()
            ->map(fn (Task $row): array => ['date' => $row->shown_on, 'count' => (int) $row->pending]);

        return response()->json(['data' => $counts]);
    }

    public function store(StoreTaskRequest $request, CreateTask $createTask): JsonResponse
    {
        $task = $createTask->handle(
            $request->user(),
            $request->safe()->except('tag_ids'),
            $request->validated('tag_ids', []),
        );

        return (new TaskResource($task))->response()->setStatusCode(Response::HTTP_CREATED);
    }

    public function show(Task $task): TaskResource
    {
        Gate::authorize('view', $task);

        return new TaskResource($task->load('tags'));
    }

    public function update(UpdateTaskRequest $request, Task $task, UpdateTask $updateTask): TaskResource
    {
        Gate::authorize('update', $task);

        return new TaskResource($updateTask->handle(
            $task,
            $request->safe()->except(['tag_ids', 'today']),
            $request->has('tag_ids') ? $request->validated('tag_ids', []) : null,
            $request->validated('today'),
        ));
    }

    /**
     * Conclui ou desmarca. Concluir uma recorrente também cria a próxima ocorrência.
     */
    public function toggle(ToggleTaskRequest $request, Task $task, ToggleTaskCompletion $toggleTaskCompletion): TaskResource
    {
        Gate::authorize('update', $task);

        return new TaskResource($toggleTaskCompletion->handle($task, $request->validated('today')));
    }

    public function destroy(Task $task, DeleteTask $deleteTask): Response
    {
        Gate::authorize('delete', $task);
        $deleteTask->handle($task);

        return response()->noContent();
    }
}
