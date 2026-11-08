package com.marcuspaulo.tarefas.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.Priority
import com.marcuspaulo.tarefas.data.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class BackupManagerTest {

    private val day = LocalDate.of(2026, 11, 10)

    private lateinit var database: AppDatabase
    private lateinit var backup: BackupManager
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        backup = BackupManager(database)
        repository = TaskRepository(database)
    }

    @After
    fun tearDown() = database.close()

    /** Arquivo no formato que o app gerava até o banco versão 4. */
    private val legacyBackup = """
        {
          "version": 1,
          "tags": [
            {"id": 1, "name": "Trabalho", "colorHex": "#3D5AFE"},
            {"id": 2, "name": "Casa", "colorHex": "#00C853"}
          ],
          "tasks": [
            {"id": 1, "title": "Relatório", "description": "mensal", "date": "2026-11-10",
             "originalDate": "2026-11-08", "deadline": "2026-11-15", "priority": "HIGH",
             "recurrence": null, "completed": false, "createdAt": 1700000000000},
            {"id": 2, "title": "Academia", "description": "", "date": "2026-11-10",
             "originalDate": "2026-11-10", "deadline": null, "priority": "LOW",
             "recurrence": "DAILY", "completed": true, "createdAt": 1700000001000}
          ],
          "taskTags": [
            {"taskId": 1, "tagId": 2},
            {"taskId": 2, "tagId": 1}
          ]
        }
    """.trimIndent()

    @Test
    fun `restaura backup antigo com ids numericos`() = runBlocking {
        backup.importFromJson(legacyBackup)

        val tasks = database.taskDao().getAllTasksOnce().associateBy { it.title }
        val report = tasks.getValue("Relatório")
        assertEquals("mensal", report.description)
        assertEquals(LocalDate.of(2026, 11, 8), report.originalDate)
        assertEquals(LocalDate.of(2026, 11, 15), report.deadline)
        assertEquals(Priority.HIGH, report.priority)
        assertEquals(true, tasks.getValue("Academia").completed)

        // O id 1 era tanto de uma tarefa quanto de uma tag: cada um vira um UUID próprio.
        suspend fun tagsOf(title: String) =
            database.taskDao().getTaskWithTagsById(tasks.getValue(title).id)!!.tags.map { it.name }
        assertEquals(listOf("Casa"), tagsOf("Relatório"))
        assertEquals(listOf("Trabalho"), tagsOf("Academia"))
        tasks.values.forEach { UUID.fromString(it.id) }
    }

    @Test
    fun `exportar e restaurar mantem ids, serie e tags`() = runBlocking {
        repository.addTag("Casa", "#00C853")
        val tag = repository.allTags.first().single()
        val task = repository.addTask("Academia", "", day, null, Priority.MEDIUM, "DAILY", setOf(tag.id))
        repository.toggleCompleted(task, setOf(tag.id), day)
        val before = database.taskDao().getAllTasksOnce().sortedBy { it.id }

        val json = backup.exportToJson()
        assertEquals(2, JSONObject(json).getInt("version"))
        backup.importFromJson(json)

        assertEquals(before, database.taskDao().getAllTasksOnce().sortedBy { it.id })
        val next = repository.tasksForDate(day.plusDays(1), day).first().single()
        assertEquals(listOf("Casa"), next.tags.map { it.name })
    }

    @Test
    fun `backup nao leva tarefas excluidas`() = runBlocking {
        repository.addTag("Casa", "#00C853")
        val tag = repository.allTags.first().single()
        val kept = repository.addTask("fica", "", day, null, Priority.MEDIUM, null, setOf(tag.id))
        val removed = repository.addTask("sai", "", day, null, Priority.MEDIUM, null, setOf(tag.id))
        repository.deleteTask(removed)

        val json = JSONObject(backup.exportToJson())

        assertEquals(1, json.getJSONArray("tasks").length())
        assertEquals(kept.id, json.getJSONArray("tasks").getJSONObject(0).getString("id"))
        assertEquals(1, json.getJSONArray("taskTags").length())

        // E o arquivo restaura sem erro de chave estrangeira.
        backup.importFromJson(json.toString())
        assertEquals(listOf("fica"), database.taskDao().getAllTasksOnce().map { it.title })
    }
    @Test
    fun `importacao em conta adiciona copias preservando tarefas atuais e recorrencia`() = runBlocking {
        repository.addTag("Casa", "#00C853")
        val tag = repository.allTags.first().single()
        val task = repository.addTask("Recorrente", "", day, null, Priority.MEDIUM, "DAILY", setOf(tag.id))
        repository.toggleCompleted(task, setOf(tag.id), day)
        val originalIds = database.taskDao().getAllTasksOnce().map { it.id }.toSet()
        val json = backup.exportToJson()
        database.syncStateDao().save(com.marcuspaulo.tarefas.data.SyncState(cursor = 9, initialized = true))
        BackupManager(database, mergeIntoAccount = true).importFromJson(json)
        val all = database.taskDao().getAllTasksOnce()
        assertEquals(4, all.size)
        assertEquals(9L, database.syncStateDao().get()!!.cursor)
        val imported = all.filter { it.id !in originalIds }
        val first = imported.single { it.completed }
        val next = imported.single { !it.completed }
        assertEquals(first.id, first.seriesId)
        assertEquals(com.marcuspaulo.tarefas.data.Ids.occurrenceId(first.id, day.plusDays(1)), next.id)
        assertEquals(listOf("Casa"), database.taskDao().getTaskWithTagsById(next.id)!!.tags.map { it.name })
        assertEquals(true, next.needsSync)
    }

    @Test
    fun `backup exportado de conta ganha ids novos ao restaurar em modo local`() = runBlocking {
        val original = repository.addTask("Conta", "", day, null, Priority.MEDIUM, null, emptySet())
        val json = BackupManager(database, mergeIntoAccount = true).exportToJson()
        backup.importFromJson(json)
        val restored = database.taskDao().getAllTasksOnce().single()
        org.junit.Assert.assertNotEquals(original.id, restored.id)
        assertEquals(original.title, restored.title)
    }

    @Test
    fun `backup invalido nao apaga tarefas da conta`() = runBlocking {
        val original = repository.addTask("Manter", "", day, null, Priority.MEDIUM, null, emptySet())
        try {
            BackupManager(database, mergeIntoAccount = true).importFromJson("{}")
            org.junit.Assert.fail("Esperava backup invalido")
        } catch (_: org.json.JSONException) { }
        assertEquals(original.id, database.taskDao().getAllTasksOnce().single().id)
    }

}
