package com.marcuspaulo.tarefas.data

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

/**
 * Cria um banco na versão 4, igual ao que está nos celulares hoje, migra para a
 * versão 5 e confere os dados. `runMigrationsAndValidate` também compara o schema
 * resultante com app/schemas/.../5.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private val day = LocalDate.of(2026, 11, 2)

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            AppDatabase::class.java,
            dbName
        ).addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
            .also(helper::closeWhenFinished)

    private fun createVersion4(fill: androidx.sqlite.db.SupportSQLiteDatabase.() -> Unit) {
        helper.createDatabase(dbName, 4).apply {
            fill()
            close()
        }
        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5).close()
    }

    private fun insertTask(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long,
        title: String,
        date: LocalDate,
        originalDate: LocalDate = date,
        deadline: LocalDate? = null,
        priority: String = "MEDIUM",
        recurrence: String? = null,
        completed: Boolean = false,
        createdAt: Long
    ) = db.execSQL(
        "INSERT INTO tasks (id, title, description, date, originalDate, deadline, priority, " +
            "recurrence, completed, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        arrayOf<Any?>(
            id, title, "descrição de $title", date.toEpochDay(), originalDate.toEpochDay(),
            deadline?.toEpochDay(), priority, recurrence, if (completed) 1 else 0, createdAt
        )
    )

    @Test
    fun `migracao preserva tarefas, tags e as ligacoes entre elas`() = runBlocking {
        createVersion4 {
            execSQL("INSERT INTO tags (id, name, colorHex) VALUES (1, 'Trabalho', '#3D5AFE')")
            execSQL("INSERT INTO tags (id, name, colorHex) VALUES (2, 'Casa', '#00C853')")
            insertTask(
                this, 1, "Relatório", day, originalDate = day.minusDays(3),
                deadline = day.plusDays(5), priority = "HIGH", createdAt = 1_700_000_000_000
            )
            insertTask(this, 2, "Academia", day, recurrence = "DAILY", createdAt = 1_700_000_001_000)
            insertTask(this, 3, "Mercado", day.minusDays(1), completed = true, createdAt = 1_700_000_002_000)
            execSQL("INSERT INTO task_tag_cross_ref (taskId, tagId) VALUES (1, 1), (1, 2), (3, 2)")
        }

        val db = openMigrated()
        val tasks = db.taskDao().getAllTasksOnce().associateBy { it.title }
        assertEquals(setOf("Relatório", "Academia", "Mercado"), tasks.keys)

        val report = tasks.getValue("Relatório")
        assertEquals("descrição de Relatório", report.description)
        assertEquals(day, report.date)
        assertEquals(day.minusDays(3), report.originalDate)
        assertEquals(day.plusDays(5), report.deadline)
        assertEquals(Priority.HIGH, report.priority)
        assertEquals(1_700_000_000_000, report.createdAt)
        assertNull(report.deletedAt)
        assertNull(report.seriesId)

        assertEquals("DAILY", tasks.getValue("Academia").recurrence)
        assertEquals(true, tasks.getValue("Mercado").completed)

        val tagsByTask = tasks.mapValues { (_, task) ->
            db.taskDao().getTaskWithTagsById(task.id)!!.tags.map { it.name }.toSet()
        }
        assertEquals(setOf("Trabalho", "Casa"), tagsByTask["Relatório"])
        assertEquals(emptySet<String>(), tagsByTask["Academia"])
        assertEquals(setOf("Casa"), tagsByTask["Mercado"])

        val tags = db.tagDao().getAllTags().first()
        assertEquals(listOf("Casa" to "#00C853", "Trabalho" to "#3D5AFE"), tags.map { it.name to it.colorHex })

        (tasks.values.map { it.id } + tags.map { it.id }).forEach { id ->
            assertEquals(7, UUID.fromString(id).version())
        }
        // O UUID começa pelo horário de criação, então a ordem das tarefas se mantém.
        assertEquals(
            listOf("Relatório", "Academia", "Mercado"),
            tasks.values.sortedBy { it.id }.map { it.title }
        )
    }

    @Test
    fun `tarefa recorrente migrada continua gerando a proxima ocorrencia`() = runBlocking {
        createVersion4 {
            insertTask(this, 1, "Academia", day, recurrence = "DAILY", createdAt = 1_700_000_000_000)
        }

        val db = openMigrated()
        val repository = TaskRepository(db)
        val task = db.taskDao().getAllTasksOnce().single()

        repository.toggleCompleted(task, emptySet(), day)

        val next = repository.tasksForDate(day.plusDays(1), day).first().single().task
        assertEquals("Academia", next.title)
        assertEquals(task.id, next.seriesId)
    }

    @Test
    fun `banco vazio tambem migra`() = runBlocking {
        createVersion4 { }

        val db = openMigrated()

        assertEquals(emptyList<Task>(), db.taskDao().getAllTasksOnce())
        assertEquals(emptyList<Tag>(), db.tagDao().getAllTagsOnce())
    }
    @Test
    fun `migracao 5 para 6 preserva exclusoes e marca todos os registros para envio`() = runBlocking {
        val id = Ids.newId()
        val tagId = Ids.newId()
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO tags (id, name, colorHex, updatedAt, deletedAt) VALUES (?, 'Casa', '#00C853', 2000, 2000)", arrayOf(tagId))
            execSQL("INSERT INTO tasks (id, title, description, date, originalDate, priority, completed, createdAt, updatedAt, deletedAt) VALUES (?, 'Excluída', '', 20000, 19999, 'HIGH', 1, 1000, 2000, 2000)", arrayOf(id))
            execSQL("INSERT INTO task_tag_cross_ref (taskId, tagId) VALUES (?, ?)", arrayOf(id, tagId))
            close()
        }
        helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6).close()
        val db = openMigrated()
        val task = db.taskDao().getIncludingDeleted(id)!!
        assertEquals(2000L, task.deletedAt)
        assertEquals(true, task.needsSync)
        assertEquals(LocalDate.ofEpochDay(19999), task.originalDate)
        assertEquals(true, db.tagDao().getIncludingDeleted(tagId)!!.needsSync)
        assertNull(db.syncStateDao().get())
        db.syncStateDao().save(SyncState(cursor = 42, initialized = true))
        assertEquals(42L, db.syncStateDao().get()!!.cursor)
    }

}
