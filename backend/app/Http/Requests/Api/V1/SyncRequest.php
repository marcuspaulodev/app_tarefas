<?php

namespace App\Http\Requests\Api\V1;

use App\Enums\Priority;
use App\Rules\ChecklistItems;
use App\Rules\RecurrenceRuleFormat;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class SyncRequest extends FormRequest
{
    /**
     * Determine if the user is authorized to make this request.
     */
    public function authorize(): bool
    {
        return true;
    }

    /**
     * Estados completos, com os horários em milissegundos UTC usados pelo Android.
     * Sem checklist (apps anteriores a ele), o checklist guardado é mantido.
     *
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        $latestTimestamp = now()->addMinutes(5)->getTimestampMs();

        return [
            'cursor' => ['required', 'integer', 'min:0'],
            'tasks' => ['sometimes', 'array', 'list', 'max:100'],
            'tasks.*' => ['array:id,title,description,checklist,date,original_date,deadline,priority,recurrence,series_id,completed,created_at,updated_at,deleted_at,tag_ids'],
            'tasks.*.id' => ['required', 'uuid', 'distinct:ignore_case'],
            'tasks.*.title' => ['required', 'string', 'max:255'],
            'tasks.*.description' => ['present', 'nullable', 'string', 'max:5000'],
            'tasks.*.checklist' => ['sometimes', 'array', new ChecklistItems],
            'tasks.*.date' => ['required', 'date_format:Y-m-d'],
            'tasks.*.original_date' => ['required', 'date_format:Y-m-d'],
            'tasks.*.deadline' => ['present', 'nullable', 'date_format:Y-m-d'],
            'tasks.*.priority' => ['required', Rule::enum(Priority::class)],
            'tasks.*.recurrence' => ['present', 'nullable', 'string', 'max:50', new RecurrenceRuleFormat],
            'tasks.*.series_id' => ['present', 'nullable', 'uuid'],
            'tasks.*.completed' => ['required', 'boolean'],
            'tasks.*.created_at' => ['required', 'integer', 'min:0', 'lte:tasks.*.updated_at'],
            'tasks.*.updated_at' => ['required', 'integer', 'min:0', 'max:'.$latestTimestamp],
            'tasks.*.deleted_at' => ['present', 'nullable', 'integer', 'min:0', 'lte:tasks.*.updated_at'],
            'tasks.*.tag_ids' => ['present', 'array', 'list', 'max:100'],
            'tasks.*.tag_ids.*' => ['required', 'uuid'],
            'tags' => ['sometimes', 'array', 'list', 'max:100'],
            'tags.*' => ['array:id,name,color_hex,created_at,updated_at,deleted_at'],
            'tags.*.id' => ['required', 'uuid', 'distinct:ignore_case'],
            'tags.*.name' => ['required', 'string', 'max:50'],
            'tags.*.color_hex' => ['required', 'regex:/^#[0-9a-fA-F]{6}$/'],
            'tags.*.created_at' => ['sometimes', 'required', 'integer', 'min:0', 'lte:tags.*.updated_at'],
            'tags.*.updated_at' => ['required', 'integer', 'min:0', 'max:'.$latestTimestamp],
            'tags.*.deleted_at' => ['present', 'nullable', 'integer', 'min:0', 'lte:tags.*.updated_at'],
        ];
    }
}
