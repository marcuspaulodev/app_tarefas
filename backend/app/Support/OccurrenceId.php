<?php

namespace App\Support;

/**
 * ID da ocorrência de uma série recorrente em um dia. É o mesmo calculado pelo app
 * (UUID.nameUUIDFromBytes do Java: MD5 do texto "serie/data", versão 3), então
 * concluir a mesma tarefa no app e no web gera a mesma próxima ocorrência.
 */
final class OccurrenceId
{
    public static function for(string $seriesId, string $date): string
    {
        $hash = md5("{$seriesId}/{$date}", binary: true);
        $hash[6] = chr((ord($hash[6]) & 0x0F) | 0x30);
        $hash[8] = chr((ord($hash[8]) & 0x3F) | 0x80);
        $hex = bin2hex($hash);

        return implode('-', [
            substr($hex, 0, 8),
            substr($hex, 8, 4),
            substr($hex, 12, 4),
            substr($hex, 16, 4),
            substr($hex, 20),
        ]);
    }
}
