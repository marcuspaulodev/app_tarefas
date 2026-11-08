<?php

namespace App\Http\Controllers\Api\V1;

use App\Actions\Auth\ResetAccountPassword;
use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\ForgotPasswordRequest;
use App\Http\Requests\Api\V1\ResetPasswordRequest;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Password;
use Illuminate\Validation\ValidationException;

class PasswordController extends Controller
{
    public function show(Request $request, ?string $token = null): Response
    {
        return response()->view('app', ['passwordRecovery' => [
            'mode' => $token === null ? 'forgot' : 'reset',
            'token' => $token ?? '',
            'email' => is_string($request->query('email')) ? $request->query('email') : '',
        ]])->header('Cache-Control', 'no-store, private')->header('Referrer-Policy', 'no-referrer');
    }

    public function sendResetLink(ForgotPasswordRequest $request): JsonResponse
    {
        Password::broker('users')->sendResetLink($request->safe()->only('email'));

        return response()->json(['message' => 'Se este e-mail estiver cadastrado, você receberá um link para criar uma nova senha. Confira também a caixa de spam.']);
    }

    public function reset(ResetPasswordRequest $request, ResetAccountPassword $resetPassword): JsonResponse
    {
        $user = $resetPassword->handle($request->validated());
        if ($user === null) {
            throw ValidationException::withMessages(['token' => 'Este link é inválido ou expirou. Solicite um novo link.']);
        }

        if ($request->hasSession() && Auth::guard('web')->id() === $user->id) {
            Auth::guard('web')->logoutCurrentDevice();
            $request->session()->invalidate();
            $request->session()->regenerateToken();
        }

        return response()->json(['message' => 'Senha alterada. Entre novamente no site e no app com a nova senha.']);
    }
}
