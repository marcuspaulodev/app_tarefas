<?php

namespace App\Http\Resources;

use App\Models\Task;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * @mixin Task
 */
class TaskResource extends JsonResource
{
    /**
     * Transform the resource into an array.
     *
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'title' => $this->title,
            'description' => $this->description,
            'checklist' => $this->checklist,
            'date' => $this->date,
            'original_date' => $this->original_date,
            'deadline' => $this->deadline,
            'priority' => $this->priority,
            'recurrence' => $this->recurrence,
            'series_id' => $this->series_id,
            'completed' => $this->completed,
            'tag_ids' => $this->whenLoaded('tags', fn () => $this->tags->pluck('id')->values()),
            'created_at' => $this->created_at,
            'updated_at' => $this->updated_at,
        ];
    }
}
