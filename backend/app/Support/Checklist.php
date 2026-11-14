<?php

namespace App\Support;

/**
 * Itens do checklist de uma tarefa: [{id, text, done}]. Mesmas regras de
 * Checklist.kt no app e de shared/fixtures/rules.json.
 */
final class Checklist
{
    public const MAX_ITEMS = 50;

    public const MAX_TEXT = 255;

    /**
     * Formato canônico de uma lista já validada.
     *
     * @param  ?list<array{id: string, text: string, done: bool}>  $items
     * @return list<array{id: string, text: string, done: bool}>
     */
    public static function normalize(?array $items): array
    {
        return array_map(fn (array $item): array => [
            'id' => strtolower($item['id']),
            'text' => trim($item['text']),
            'done' => (bool) $item['done'],
        ], $items ?? []);
    }

    /**
     * Checklist da próxima ocorrência de uma recorrente: só os itens que ficaram
     * pendentes, com os mesmos IDs, para o app e o servidor gerarem a mesma lista.
     *
     * @param  ?list<array{id: string, text: string, done: bool}>  $items
     * @return list<array{id: string, text: string, done: bool}>
     */
    public static function forNextOccurrence(?array $items): array
    {
        return array_values(array_filter(self::normalize($items), fn (array $item): bool => ! $item['done']));
    }
}
