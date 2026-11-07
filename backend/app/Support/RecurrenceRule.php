<?php

namespace App\Support;

use Carbon\CarbonImmutable;

/**
 * Regra de repetição no mesmo formato texto do app: "DAILY" ou "WEEKLY:1,3,5"
 * (dias ISO 8601, 1 = segunda, 7 = domingo). Mesmas regras de RecurrenceRule.kt,
 * conferidas pelos casos de shared/fixtures/rules.json.
 */
final class RecurrenceRule
{
    /**
     * @param  list<int>  $weekdays
     */
    private function __construct(
        private readonly bool $isDaily,
        private readonly array $weekdays,
    ) {}

    public static function decode(?string $raw): ?self
    {
        if ($raw === 'DAILY') {
            return new self(true, []);
        }

        if ($raw === null || ! str_starts_with($raw, 'WEEKLY:')) {
            return null;
        }

        $weekdays = [];
        foreach (explode(',', substr($raw, strlen('WEEKLY:'))) as $part) {
            if (ctype_digit($part) && (int) $part >= 1 && (int) $part <= 7) {
                $weekdays[] = (int) $part;
            }
        }

        return $weekdays === [] ? null : new self(false, array_values(array_unique($weekdays)));
    }

    /**
     * Próximo dia da série depois de $date (Y-m-d).
     */
    public function nextOccurrenceAfter(string $date): string
    {
        $current = CarbonImmutable::createFromFormat('!Y-m-d', $date);

        if ($this->isDaily) {
            return $current->addDay()->format('Y-m-d');
        }

        $next = $current->addDay();
        for ($i = 0; $i < 7; $i++) {
            if (in_array($next->dayOfWeekIso, $this->weekdays, true)) {
                return $next->format('Y-m-d');
            }
            $next = $next->addDay();
        }

        return $current->addWeek()->format('Y-m-d');
    }
}
