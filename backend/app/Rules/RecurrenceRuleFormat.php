<?php

namespace App\Rules;

use Closure;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Translation\PotentiallyTranslatedString;

class RecurrenceRuleFormat implements ValidationRule
{
    /**
     * Aceita "DAILY" ou "WEEKLY:" com dias de 1 (segunda) a 7 (domingo), como "WEEKLY:1,3,5".
     *
     * @param  Closure(string, ?string=): PotentiallyTranslatedString  $fail
     */
    public function validate(string $attribute, mixed $value, Closure $fail): void
    {
        $isValid = $value === 'DAILY'
            || (is_string($value) && preg_match('/^WEEKLY:[1-7](,[1-7])*$/', $value) === 1);

        if (! $isValid) {
            $fail('The :attribute must be DAILY or WEEKLY: followed by weekdays from 1 to 7.');
        }
    }
}
