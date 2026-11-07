<?php

namespace App\Enums;

/**
 * Valores iguais aos nomes do enum Priority do app.
 */
enum Priority: string
{
    case Low = 'LOW';
    case Medium = 'MEDIUM';
    case High = 'HIGH';
}
