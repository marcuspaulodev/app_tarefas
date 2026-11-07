<?php

namespace App\Http\Requests\Web;

use App\Http\Requests\Api\V1\LoginRequest as ApiLoginRequest;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Support\Arr;

class LoginRequest extends ApiLoginRequest
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
            'email.required' => 'Informe seu e-mail.',
            'email.email' => 'Informe um e-mail válido.',
            'password.required' => 'Informe sua senha.',
        ];
    }
}
