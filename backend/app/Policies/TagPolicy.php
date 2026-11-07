<?php

namespace App\Policies;

use App\Models\Tag;
use App\Models\User;
use Illuminate\Auth\Access\Response;

/**
 * Cada usuário só enxerga o que é dele. O acesso a dados de outro usuário responde 404,
 * para não revelar que o registro existe.
 */
class TagPolicy
{
    /**
     * Determine whether the user can view the model.
     */
    public function view(User $user, Tag $tag): Response
    {
        return $this->owns($user, $tag);
    }

    /**
     * Determine whether the user can update the model.
     */
    public function update(User $user, Tag $tag): Response
    {
        return $this->owns($user, $tag);
    }

    /**
     * Determine whether the user can delete the model.
     */
    public function delete(User $user, Tag $tag): Response
    {
        return $this->owns($user, $tag);
    }

    private function owns(User $user, Tag $tag): Response
    {
        return $tag->user_id === $user->id ? Response::allow() : Response::denyAsNotFound();
    }
}
