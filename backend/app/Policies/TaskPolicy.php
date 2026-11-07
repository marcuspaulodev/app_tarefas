<?php

namespace App\Policies;

use App\Models\Task;
use App\Models\User;
use Illuminate\Auth\Access\Response;

/**
 * Cada usuário só enxerga o que é dele. O acesso a dados de outro usuário responde 404,
 * para não revelar que o registro existe.
 */
class TaskPolicy
{
    /**
     * Determine whether the user can view the model.
     */
    public function view(User $user, Task $task): Response
    {
        return $this->owns($user, $task);
    }

    /**
     * Determine whether the user can update the model.
     */
    public function update(User $user, Task $task): Response
    {
        return $this->owns($user, $task);
    }

    /**
     * Determine whether the user can delete the model.
     */
    public function delete(User $user, Task $task): Response
    {
        return $this->owns($user, $task);
    }

    private function owns(User $user, Task $task): Response
    {
        return $task->user_id === $user->id ? Response::allow() : Response::denyAsNotFound();
    }
}
