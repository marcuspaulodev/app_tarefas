package com.marcuspaulo.tarefas.data

import androidx.room.migration.Migration

/**
 * Migrations do banco, uma para cada aumento de `version` em [AppDatabase].
 *
 * Ao mudar o schema: suba a versão, adicione aqui um `Migration(N, N + 1)` com o SQL
 * da mudança e faça commit do novo JSON gerado em `app/schemas/`. Se faltar a migration,
 * o app fecha com erro ao abrir o banco em vez de apagar os dados.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
