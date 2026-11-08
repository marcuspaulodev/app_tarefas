<?php

namespace App\Http\Controllers\Api\V1;

use App\Actions\Auth\SendEmailVerification;
use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Auth\Events\Verified;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\URL;

class EmailVerificationController extends Controller
{
    public function send(Request $request, SendEmailVerification $sendVerification): JsonResponse
    {
        $result = $sendVerification->handle($request->user());
        $status = match ($result) {
            'throttled' => 429,
            'failed' => 503,
            default => 200,
        };

        $response = response()->json([
            'email_verified' => $result === 'verified',
            'message' => match ($result) {
                'verified' => 'Seu e-mail já está confirmado.',
                'sent' => 'Link enviado. Confira seu e-mail e a caixa de spam.',
                'throttled' => 'Aguarde um minuto antes de solicitar outro link.',
                default => 'Não foi possível enviar o e-mail agora. Tente novamente em instantes.',
            },
        ], $status);

        return $result === 'throttled' ? $response->header('Retry-After', '60') : $response;
    }

    public function verify(Request $request, string $id, string $hash): Response
    {
        $verified = false;
        if (URL::hasValidSignature($request, absolute: false)) {
            $changed = false;
            $user = DB::transaction(function () use ($id, $hash, &$changed): ?User {
                $user = User::whereKey($id)->lockForUpdate()->first();
                if ($user === null || ! hash_equals(sha1($user->getEmailForVerification()), $hash)) {
                    return null;
                }
                if (! $user->hasVerifiedEmail()) {
                    $changed = $user->markEmailAsVerified();
                }

                return $user;
            });
            $verified = $user !== null;
            if ($changed) {
                event(new Verified($user));
            }
        }

        return response()->view('app', ['emailVerification' => ['verified' => $verified]], $verified ? 200 : 403)
            ->header('Cache-Control', 'no-store, private')->header('Referrer-Policy', 'no-referrer');
    }
}
