<?php

namespace App\Actions\Tags;

use App\Models\Tag;
use App\Models\User;
use Illuminate\Support\Facades\DB;

class CreateTag
{
    /**
     * @param  array{id?: ?string, name: string, color_hex: string}  $attributes
     */
    public function handle(User $user, array $attributes): Tag
    {
        return DB::transaction(function () use ($user, $attributes): Tag {
            $tag = $user->tags()->make(array_filter($attributes, fn (mixed $value): bool => $value !== null));
            $tag->sync_version = $user->claimNextSyncVersion();
            $tag->save();

            return $tag;
        });
    }
}
