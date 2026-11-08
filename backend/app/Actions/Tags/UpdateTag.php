<?php

namespace App\Actions\Tags;

use App\Models\Tag;
use Illuminate\Support\Facades\DB;

class UpdateTag
{
    /**
     * @param  array{name?: string, color_hex?: string}  $attributes
     */
    public function handle(Tag $tag, array $attributes): Tag
    {
        return DB::transaction(function () use ($tag, $attributes): Tag {
            $user = $tag->user;
            $version = $user->claimNextSyncVersion();
            $tag = $user->tags()->findOrFail($tag->id);
            $tag->fill($attributes);
            $tag->sync_version = $version;
            $tag->save();

            return $tag;
        });
    }
}
