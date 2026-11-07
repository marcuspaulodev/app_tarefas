<?php

namespace App\Http\Controllers\Api\V1;

use App\Actions\Tags\CreateTag;
use App\Actions\Tags\DeleteTag;
use App\Actions\Tags\UpdateTag;
use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\StoreTagRequest;
use App\Http\Requests\Api\V1\UpdateTagRequest;
use App\Http\Resources\TagResource;
use App\Models\Tag;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Gate;

class TagController extends Controller
{
    public function index(Request $request): AnonymousResourceCollection
    {
        return TagResource::collection($request->user()->tags()->orderBy('name')->get());
    }

    public function store(StoreTagRequest $request, CreateTag $createTag): JsonResponse
    {
        $tag = $createTag->handle($request->user(), $request->validated());

        return (new TagResource($tag))->response()->setStatusCode(Response::HTTP_CREATED);
    }

    public function show(Tag $tag): TagResource
    {
        Gate::authorize('view', $tag);

        return new TagResource($tag);
    }

    public function update(UpdateTagRequest $request, Tag $tag, UpdateTag $updateTag): TagResource
    {
        Gate::authorize('update', $tag);

        return new TagResource($updateTag->handle($tag, $request->validated()));
    }

    /**
     * A tag sai das tarefas que a usavam.
     */
    public function destroy(Tag $tag, DeleteTag $deleteTag): Response
    {
        Gate::authorize('delete', $tag);
        $deleteTag->handle($tag);

        return response()->noContent();
    }
}
