<?php

namespace App\Actions\Tags;

use App\Models\User;
use Illuminate\Support\Facades\DB;

class CreateDefaultTags
{
    /**
     * As mesmas tags com que o app começa.
     *
     * @var list<array{name: string, color_hex: string}>
     */
    private const DEFAULT_TAGS = [
        ['name' => 'Trabalho', 'color_hex' => '#3D5AFE'],
        ['name' => 'Casa', 'color_hex' => '#00C853'],
        ['name' => 'Faculdade', 'color_hex' => '#FF6D00'],
    ];

    public function handle(User $user): void
    {
        DB::transaction(function () use ($user): void {
            $version = $user->claimNextSyncVersion();

            foreach (self::DEFAULT_TAGS as $attributes) {
                $tag = $user->tags()->make($attributes);
                $tag->sync_version = $version;
                $tag->save();
            }
        });
    }
}
