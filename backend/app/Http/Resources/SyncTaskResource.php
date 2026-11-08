<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;

class SyncTaskResource extends TaskResource
{
    /**
     * Transform the resource into an array.
     *
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            ...parent::toArray($request),
            'created_at' => $this->created_at->getTimestampMs(),
            'updated_at' => $this->updated_at->getTimestampMs(),
            'deleted_at' => $this->deleted_at?->getTimestampMs(),
            'sync_version' => $this->sync_version,
        ];
    }
}
