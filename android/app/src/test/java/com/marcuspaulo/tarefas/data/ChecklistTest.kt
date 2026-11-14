package com.marcuspaulo.tarefas.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcuspaulo.tarefas.backup.BackupManager
import com.marcuspaulo.tarefas.sync.HttpSyncApi
import com.marcuspaulo.tarefas.sync.SyncTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ChecklistTest {

    private val today = LocalDate.of(2026, 11, 10)
    private var now = 1_000L
    private lateinit var database: AppDatabase
    private lateinit var repository: TaskRepository

    private val warmUp = ChecklistItem(id = Ids.newId(), text = "Aquecer")
    private val workout = ChecklistItem(id = Ids.newId(), text = "Treino")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = TaskRepository(database) { now }
    }

    @After
    fun tearDown() = database.close()

    private fun add(checklist: List<ChecklistItem>, recurrence: String? = null): Task = runBlocking {
        repository.addTask("Academia", "", today, null, Priority.MEDIUM, recurrence, emptySet(), checklist)
    }

    private fun stored(id: String): Task = runBlocking { database.taskDao().getById(id)!! }

    @Test
    fun `checklist e gravado limpo e na ordem`() {
        val task = add(listOf(warmUp.copy(text = "  Aquecer "), ChecklistItem(text = "   "), workout))

        assertEquals(listOf(warmUp, workout), stored(task.id).checklist)
    }

    @Test
    fun `marcar item atualiza a tarefa para sincronizar`() = runBlocking {
        val task = add(listOf(warmUp, workout))
        database.taskDao().update(stored(task.id).copy(needsSync = false))

        now = 2_000L
        val updated = repository.toggleChecklistItem(task.id, workout.id)!!

        assertEquals(listOf(false, true), updated.checklist.map { it.done })
        assertEquals(updated, stored(task.id))
        assertEquals(2_000L, updated.updatedAt)
        assertTrue(updated.needsSync)
        assertNull(repository.toggleChecklistItem(task.id, "desconhecido"))
    }

    @Test
    fun `editar substitui os itens`() = runBlocking {
        val task = add(listOf(warmUp, workout))

        repository.updateTask(stored(task.id).copy(checklist = listOf(workout.copy(done = true))), emptySet())

        assertEquals(listOf(workout.copy(done = true)), stored(task.id).checklist)
    }

    @Test
    fun `pergunta para concluir so ao marcar o ultimo item pendente`() {
        val task = Task(title = "x", date = today, checklist = listOf(warmUp.copy(done = true), workout))

        assertTrue(Checklist.completesChecklist(task, workout.id))
        assertFalse(Checklist.completesChecklist(task, warmUp.id))
        assertFalse(Checklist.completesChecklist(task.copy(completed = true), workout.id))
        assertFalse(Checklist.completesChecklist(task.copy(checklist = listOf(warmUp, workout)), workout.id))
    }

    @Test
    fun `proxima ocorrencia recebe so os itens pendentes`() = runBlocking {
        val task = add(listOf(warmUp.copy(done = true), workout), recurrence = "DAILY")

        repository.toggleCompleted(stored(task.id), emptySet(), today)

        val next = repository.tasksForDate(today.plusDays(1), today).first().single().task
        assertEquals(listOf(workout), next.checklist)
        assertEquals(listOf(warmUp.copy(done = true), workout), stored(task.id).checklist)
    }

    @Test
    fun `sincronizacao envia e recebe o checklist`() {
        val task = Task(title = "x", date = today, checklist = listOf(warmUp, workout.copy(done = true)))

        val json = HttpSyncApi.taskJson(SyncTask(task, emptyList()))
        assertEquals(2, json.getJSONArray("checklist").length())
        assertEquals(task.copy(needsSync = false), HttpSyncApi.parseTask(json).task)

        json.remove("checklist")
        assertEquals(emptyList<ChecklistItem>(), HttpSyncApi.parseTask(json).task.checklist)
    }

    @Test
    fun `backup leva o checklist e aceita arquivo sem ele`() = runBlocking {
        val task = add(listOf(warmUp, workout.copy(done = true)))
        val backup = BackupManager(database)
        val json = backup.exportToJson()

        backup.importFromJson(json)
        assertEquals(listOf(warmUp, workout.copy(done = true)), stored(task.id).checklist)

        val root = JSONObject(json)
        root.getJSONArray("tasks").getJSONObject(0).remove("checklist")
        backup.importFromJson(root.toString())
        assertEquals(emptyList<ChecklistItem>(), stored(task.id).checklist)
    }
}
