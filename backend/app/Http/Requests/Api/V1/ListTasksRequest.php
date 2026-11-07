<?php

namespace App\Http\Requests\Api\V1;

use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

/**
 * Lista por dia, intervalo, busca ou estado. Filtros por data usam o "hoje" do aparelho.
 */
class ListTasksRequest extends FormRequest
{
    /**
     * Determine if the user is authorized to make this request.
     */
    public function authorize(): bool
    {
        return true;
    }

    /**
     * Get the validation rules that apply to the request.
     *
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'search' => ['required_without_all:date,start,completed', 'nullable', 'string', 'min:1', 'max:100'],
            'date' => ['required_without_all:search,start,completed', 'nullable', 'date_format:Y-m-d'],
            'start' => ['required_with:end', 'nullable', 'date_format:Y-m-d'],
            'end' => ['required_with:start', 'nullable', 'date_format:Y-m-d', 'after_or_equal:start'],
            'today' => ['required_with:date,start', 'nullable', 'date_format:Y-m-d'],
            'completed' => ['sometimes', 'boolean'],
        ];
    }
}
