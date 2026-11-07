package com.marcuspaulo.tarefas.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations do banco, uma para cada aumento de `version` em [AppDatabase].
 *
 * Ao mudar o schema: suba a versão, adicione aqui um `Migration(N, N + 1)` com o SQL
 * da mudança e faça commit do novo JSON gerado em `app/schemas/`. Se faltar a migration,
 * o app fecha com erro ao abrir o banco em vez de apagar os dados.
 */
val ALL_MIGRATIONS: Array<Migration>
    get() = arrayOf(MIGRATION_4_5)

/**
 * 4 → 5: IDs passam de número sequencial para UUID gerado no aparelho, e entram
 * `seriesId`, `updatedAt` e `deletedAt`, preparando a sincronização com o servidor.
 *
 * O SQLite não troca o tipo da chave primária, então as três tabelas são recriadas.
 * Os dados são lidos para a memória (é um banco pessoal, pequeno), as tabelas antigas
 * são apagadas e as novas recebem os dados com os IDs convertidos. O Room roda tudo
 * dentro de uma transação: se algo falhar, o banco continua na versão 4.
 *
 * Tarefas recorrentes antigas ficam com `seriesId` nulo e ganham um na próxima conclusão.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        val tagIds = HashMap<Long, String>()
        val tags = mutableListOf<Array<Any?>>()
        db.query("SELECT id, name, colorHex FROM tags").use { c ->
            while (c.moveToNext()) {
                val newId = Ids.newId(now)
                tagIds[c.getLong(0)] = newId
                tags += arrayOf(newId, c.getString(1), c.getString(2), now)
            }
        }

        val taskIds = HashMap<Long, String>()
        val tasks = mutableListOf<Array<Any?>>()
        db.query(
            """
            SELECT id, title, description, date, originalDate, deadline,
                   priority, recurrence, completed, createdAt
            FROM tasks
            """
        ).use { c ->
            while (c.moveToNext()) {
                val createdAt = c.getLong(9)
                // O horário de criação vira o começo do UUID e preserva a ordem das tarefas.
                val newId = Ids.newId(createdAt)
                taskIds[c.getLong(0)] = newId
                tasks += arrayOf(
                    newId,
                    c.getString(1),
                    c.getString(2),
                    c.getLong(3),
                    c.getLong(4),
                    if (c.isNull(5)) null else c.getLong(5),
                    c.getString(6),
                    if (c.isNull(7)) null else c.getString(7),
                    null, // seriesId
                    c.getLong(8),
                    createdAt,
                    now // updatedAt
                )
            }
        }

        val refs = mutableListOf<Array<Any?>>()
        db.query("SELECT taskId, tagId FROM task_tag_cross_ref").use { c ->
            while (c.moveToNext()) {
                val taskId = taskIds[c.getLong(0)] ?: continue
                val tagId = tagIds[c.getLong(1)] ?: continue
                refs += arrayOf(taskId, tagId)
            }
        }

        // A tabela de ligação sai primeiro, por depender das outras duas.
        db.execSQL("DROP TABLE task_tag_cross_ref")
        db.execSQL("DROP TABLE tasks")
        db.execSQL("DROP TABLE tags")

        // Mesmas definições de app/schemas/.../5.json.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`colorHex` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `date` INTEGER NOT NULL, `originalDate` INTEGER NOT NULL, " +
                "`deadline` INTEGER, `priority` TEXT NOT NULL, `recurrence` TEXT, `seriesId` TEXT, " +
                "`completed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_tag_cross_ref` (`taskId` TEXT NOT NULL, `tagId` TEXT NOT NULL, " +
                "PRIMARY KEY(`taskId`, `tagId`), " +
                "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tag_cross_ref_taskId` ON `task_tag_cross_ref` (`taskId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_tag_cross_ref_tagId` ON `task_tag_cross_ref` (`tagId`)")

        tags.forEach {
            db.execSQL("INSERT INTO tags (id, name, colorHex, updatedAt, deletedAt) VALUES (?, ?, ?, ?, NULL)", it)
        }
        tasks.forEach {
            db.execSQL(
                "INSERT INTO tasks (id, title, description, date, originalDate, deadline, priority, " +
                    "recurrence, seriesId, completed, createdAt, updatedAt, deletedAt) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)",
                it
            )
        }
        refs.forEach {
            db.execSQL("INSERT INTO task_tag_cross_ref (taskId, tagId) VALUES (?, ?)", it)
        }
    }
}
