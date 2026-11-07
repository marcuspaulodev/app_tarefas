<?php

namespace App\Http\Requests\Api\V1;

use App\Enums\Priority;
use App\Rules\RecurrenceRuleFormat;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class StoreTaskRequest extends FormRequest
{
    /**
     * Determine if the user is authorized to make this request.
     */
    public function authorize(): bool
    {
        return true;
    }

    /**
     * O id é opcional: o app manda o UUID que gerou offline; o web pode deixar o servidor gerar.
     *
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'id' => ['nullable', 'uuid', 'unique:tasks,id'],
            'title' => ['required', 'string', 'max:255'],
            'description' => ['nullable', 'string', 'max:5000'],
            'date' => ['required', 'date_format:Y-m-d'],
            'deadline' => ['nullable', 'date_format:Y-m-d'],
            'priority' => ['nullable', Rule::enum(Priority::class)],
            'recurrence' => ['nullable', new RecurrenceRuleFormat],
            'tag_ids' => ['array'],
            'tag_ids.*' => ['uuid', 'distinct', OwnedTag::rule($this->user())],
        ];
    }
}
