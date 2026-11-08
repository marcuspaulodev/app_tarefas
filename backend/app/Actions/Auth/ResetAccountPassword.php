<?php

namespace App\Actions\Auth;

use App\Models\User;
use Illuminate\Auth\Events\PasswordReset;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Password;
use Illuminate\Support\Str;

class ResetAccountPassword
{
    /** @param array{email: string, token: string, password: string, password_confirmation: string} $credentials */
    public function handle(array $credentials): ?User
    {
        $resetUser = null;
        $status = DB::transaction(function () use ($credentials, &$resetUser): string {
            // A validação e o consumo do token ficam sob a mesma trava, inclusive em dois envios simultâneos.
            DB::table(config('auth.passwords.users.table'))->where('email', $credentials['email'])->lockForUpdate()->first();

            return Password::broker('users')->reset($credentials, function (User $user, string $password) use (&$resetUser): void {
                $user->forceFill(['password' => $password, 'remember_token' => Str::random(60)])->save();
                $user->tokens()->delete();

                if (config('session.driver') === 'database') {
                    DB::connection(config('session.connection'))->table(config('session.table', 'sessions'))
                        ->where('user_id', $user->id)->delete();
                }
                $resetUser = $user;
            });
        }, attempts: 3);

        if ($status !== Password::PASSWORD_RESET) {
            return null;
        }

        event(new PasswordReset($resetUser));

        return $resetUser;
    }
}
