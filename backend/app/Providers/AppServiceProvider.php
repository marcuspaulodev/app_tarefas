<?php

namespace App\Providers;

use App\Models\User;
use Illuminate\Auth\Notifications\ResetPassword;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        //
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        $resetUrl = static fn (User $user, string $token): string => rtrim(config('app.url'), '/').route('password.reset', [
            'token' => $token, 'email' => $user->getEmailForPasswordReset(),
        ], absolute: false);

        ResetPassword::createUrlUsing($resetUrl);
        ResetPassword::toMailUsing(static fn (User $user, string $token): MailMessage => (new MailMessage)
            ->subject('Redefinir sua senha — Tarefas')
            ->greeting('Olá, '.$user->name.'!')
            ->line('Recebemos um pedido para redefinir a senha da sua conta no Tarefas.')
            ->action('Criar nova senha', $resetUrl($user, $token))
            ->line('Este link é válido por '.config('auth.passwords.users.expire').' minutos e só pode ser usado uma vez.')
            ->line('Se você não fez este pedido, ignore este e-mail. Sua senha continua a mesma.')
            ->salutation('Equipe Tarefas'));
    }
}
