<?php

namespace App\Rules;

use App\Support\Checklist;
use Closure;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Support\Str;
use Illuminate\Translation\PotentiallyTranslatedString;

class ChecklistItems implements ValidationRule
{
    /**
     * Lista de até 50 itens {id, text, done}, com IDs únicos dentro da tarefa.
     * Fica numa regra só porque `distinct` com curinga compararia itens de tarefas
     * diferentes no lote de sincronização, e uma ocorrência recorrente repete os IDs.
     *
     * @param  Closure(string, ?string=): PotentiallyTranslatedString  $fail
     */
    public function validate(string $attribute, mixed $value, Closure $fail): void
    {
        if (! is_array($value) || ! array_is_list($value)) {
            $fail('O checklist deve ser uma lista de itens.');

            return;
        }
        if (count($value) > Checklist::MAX_ITEMS) {
            $fail('O checklist aceita até '.Checklist::MAX_ITEMS.' itens.');

            return;
        }

        $ids = [];
        foreach ($value as $item) {
            $valid = is_array($item)
                && array_keys($item) == array_intersect(array_keys($item), ['id', 'text', 'done'])
                && is_string($item['id'] ?? null) && Str::isUuid($item['id'])
                && is_string($item['text'] ?? null) && trim($item['text']) !== ''
                && mb_strlen($item['text']) <= Checklist::MAX_TEXT
                && is_bool($item['done'] ?? null);

            if (! $valid) {
                $fail('Cada item do checklist precisa de id, texto (até '.Checklist::MAX_TEXT.' caracteres) e marcação.');

                return;
            }

            $ids[] = strtolower($item['id']);
        }

        if (count($ids) !== count(array_unique($ids))) {
            $fail('O checklist tem itens repetidos.');
        }
    }
}
