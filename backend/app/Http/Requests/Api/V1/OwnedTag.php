<?php

namespace App\Http\Requests\Api\V1;

use App\Models\User;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Rules\Exists;

/**
 * Só aceita tags do próprio usuário que não foram excluídas.
 */
final class OwnedTag
{
    public static function rule(User $user): Exists
    {
        return Rule::exists('tags', 'id')
            ->where('user_id', $user->id)
            ->whereNull('deleted_at');
    }

    /**
     * Revalida após obter a trava de escrita: uma sincronização pode ter excluído
     * a tag entre a validação HTTP e o início da transação.
     *
     * @param  list<string>  $ids
     */
    public static function validateIds(User $user, array $ids): void
    {
        Validator::make(['tag_ids' => $ids], ['tag_ids.*' => [self::rule($user)]])->validate();
    }
}
