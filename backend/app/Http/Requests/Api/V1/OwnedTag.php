<?php

namespace App\Http\Requests\Api\V1;

use App\Models\User;
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
}
