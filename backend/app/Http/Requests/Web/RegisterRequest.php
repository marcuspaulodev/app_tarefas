<?php

namespace App\Http\Requests\Web;

use App\Http\Requests\Api\V1\RegisterRequest as ApiRegisterRequest;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Support\Arr;

class RegisterRequest extends ApiRegisterRequest
{
    /**
     * Get the validation rules that apply to the request.
     *
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return Arr::except(parent::rules(), 'device_name');
    }

    /** @return array<string, string> */
    public function messages(): array
    {
        return [
            'name.required' => 'Informe seu nome.',
            'name.max' => 'Use até 255 caracteres no nome.',
            'email.required' => 'Informe seu e-mail.',
            'email.email' => 'Informe um e-mail válido.',
            'email.max' => 'Use até 255 caracteres no e-mail.',
            'email.unique' => 'Este e-mail já está cadastrado.',
            'password.required' => 'Crie uma senha.',
            'password.confirmed' => 'As senhas precisam ser iguais.',
            'password.min' => 'Use pelo menos :min caracteres na senha.',
        ];
    }
}
