<?php

namespace App\Http\Requests\Api\V1;

use App\Enums\Priority;
use App\Rules\ChecklistItems;
use App\Rules\RecurrenceRuleFormat;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class UpdateTaskRequest extends FormRequest
{
    /**
     * Determine if the user is authorized to make this request.
     */
    public function authorize(): bool
    {
        return true;
    }

    /**
     * Campos ausentes ficam como estão. Mudar a data exige o "hoje" do aparelho,
     * para saber se o dia exibido mudou de fato.
     *
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'title' => ['sometimes', 'required', 'string', 'max:255'],
            'description' => ['sometimes', 'nullable', 'string', 'max:5000'],
            'checklist' => ['sometimes', 'nullable', 'array', new ChecklistItems],
            'date' => ['sometimes', 'required', 'date_format:Y-m-d'],
            'today' => ['required_with:date', 'date_format:Y-m-d'],
            'deadline' => ['sometimes', 'nullable', 'date_format:Y-m-d'],
            'priority' => ['sometimes', 'required', Rule::enum(Priority::class)],
            'recurrence' => ['sometimes', 'nullable', new RecurrenceRuleFormat],
            'tag_ids' => ['sometimes', 'array'],
            'tag_ids.*' => ['uuid', 'distinct', OwnedTag::rule($this->user())],
        ];
    }
}
