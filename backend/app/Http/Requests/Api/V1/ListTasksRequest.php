<?php

namespace App\Http\Requests\Api\V1;

use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

/**
 * Duas formas de listar: as tarefas de um dia (date + today, o "hoje" do aparelho)
 * ou uma busca por título e descrição em todas as datas (search).
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
            'search' => ['required_without:date', 'nullable', 'string', 'min:1', 'max:100'],
            'date' => ['required_without:search', 'nullable', 'date_format:Y-m-d'],
            'today' => ['required_with:date', 'nullable', 'date_format:Y-m-d'],
        ];
    }
}
