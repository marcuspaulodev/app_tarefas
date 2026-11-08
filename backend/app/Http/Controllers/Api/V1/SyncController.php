<?php

namespace App\Http\Controllers\Api\V1;

use App\Actions\Sync\Synchronize;
use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\SyncRequest;
use App\Http\Resources\SyncTagResource;
use App\Http\Resources\SyncTaskResource;
use Illuminate\Http\JsonResponse;

class SyncController extends Controller
{
    /**
     * Handle the incoming request.
     */
    public function __invoke(SyncRequest $request, Synchronize $synchronize): JsonResponse
    {
        $result = $synchronize->handle(
            $request->user(),
            (int) $request->validated('cursor'),
            $request->validated('tasks', []),
            $request->validated('tags', []),
        );

        return response()->json(['data' => [
            'cursor' => $result['cursor'],
            'server_time' => $result['server_time'],
            'tasks' => SyncTaskResource::collection($result['tasks'])->resolve($request),
            'tags' => SyncTagResource::collection($result['tags'])->resolve($request),
        ]]);
    }
}
