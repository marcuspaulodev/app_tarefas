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
            $tag->fill($attributes);
            $tag->sync_version = $tag->user->claimNextSyncVersion();
            $tag->save();

            return $tag;
        });
    }
}
