<?php

namespace App\Models\Concerns;

use Illuminate\Support\Carbon;

trait HasSyncTimestamp
{
    /**
     * A edição no servidor sucede a versão alterada mesmo quando o relógio do
     * aparelho que a enviou está um pouco adiantado.
     */
    public function freshTimestamp(): Carbon
    {
        return Carbon::createFromTimestampMs(max(
            now()->getTimestampMs(),
            ($this->updated_at?->getTimestampMs() ?? -1) + 1,
        ), 'UTC');
    }
}
