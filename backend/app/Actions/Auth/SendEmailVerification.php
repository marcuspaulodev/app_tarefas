<?php

namespace App\Actions\Auth;

use App\Models\User;
use Illuminate\Auth\Notifications\VerifyEmail;
use Illuminate\Support\Facades\RateLimiter;
use Throwable;

class SendEmailVerification
{
    public function handle(User $user): string
    {
        if ($user->hasVerifiedEmail()) {
            return 'verified';
        }

        try {
            $sent = RateLimiter::attempt('verify-email:'.$user->id.':'.sha1($user->email), 1, function () use ($user): void {
                $user->notify(new VerifyEmail);
            }, 60);

            return $sent ? 'sent' : 'throttled';
        } catch (Throwable $error) {
            report($error);

            return 'failed';
        }
    }
}
