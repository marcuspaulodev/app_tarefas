<?php

namespace App\Models;

use App\Actions\Auth\SendEmailVerification;
use Database\Factories\UserFactory;
use Illuminate\Contracts\Auth\MustVerifyEmail;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Illuminate\Support\Facades\DB;
use Laravel\Sanctum\HasApiTokens;
use LogicException;

#[Fillable(['name', 'email', 'password'])]
#[Hidden(['password', 'remember_token', 'sync_version'])]
class User extends Authenticatable implements MustVerifyEmail
{
    /** @use HasFactory<UserFactory> */
    use HasApiTokens, HasFactory, Notifiable;

    public function sendEmailVerificationNotification(): void
    {
        app(SendEmailVerification::class)->handle($this);
    }

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
            'sync_version' => 'integer',
        ];
    }

    /**
     * @return HasMany<Task, $this>
     */
    public function tasks(): HasMany
    {
        return $this->hasMany(Task::class);
    }

    /**
     * @return HasMany<Tag, $this>
     */
    public function tags(): HasMany
    {
        return $this->hasMany(Tag::class);
    }

    /**
     * Reserva o próximo número de versão do usuário para as escritas da transação atual.
     *
     * A linha do usuário fica travada até o fim da transação, então duas escritas
     * simultâneas nunca recebem o mesmo número nem gravam fora de ordem, e a
     * sincronização não pula alterações.
     *
     * A checagem de transação não aparece nos testes, que já rodam dentro de uma
     * (RefreshDatabase); ela protege o uso fora deles.
     */
    public function claimNextSyncVersion(): int
    {
        if (DB::transactionLevel() === 0) {
            throw new LogicException('claimNextSyncVersion() precisa rodar dentro de uma transação.');
        }

        $version = (int) DB::table('users')->where('id', $this->id)->lockForUpdate()->value('sync_version') + 1;
        DB::table('users')->where('id', $this->id)->update(['sync_version' => $version]);
        $this->setRawAttributes(array_merge($this->getAttributes(), ['sync_version' => $version]), sync: true);

        return $version;
    }
}
