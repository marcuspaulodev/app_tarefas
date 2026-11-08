package com.marcuspaulo.tarefas.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Campos que a sincronização vai usar: `updatedAt`, `deletedAt` e `seriesId`. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TaskSyncFieldsTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val tomorrow = today.plusDays(1)

    private var now = 1_000L
    private lateinit var database: AppDatabase
    private lateinit var dao: TaskDao
    private lateinit var tagDao: TagDao
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.taskDao()
        tagDao = database.tagDao()
        repository = TaskRepository(database) { now }
    }

    @After
    fun tearDown() = database.close()

    private fun add(title: String, recurrence: String? = null, tagIds: Set<String> = emptySet()): Task = runBlocking {
        repository.addTask(title, "", today, null, Priority.MEDIUM, recurrence, tagIds)
    }

    private fun stored(id: String): Task? = runBlocking { dao.getById(id) }

    private fun rawRow(id: String): Pair<Long, Long?> =
        database.openHelper.readableDatabase
            .query("SELECT updatedAt, deletedAt FROM tasks WHERE id = ?", arrayOf(id))
            .use { c ->
                c.moveToFirst()
                c.getLong(0) to (if (c.isNull(1)) null else c.getLong(1))
            }

    private fun titlesOn(date: LocalDate): List<String> = runBlocking {
        repository.tasksForDate(date, today).first().map { it.task.title }
    }

    @Test
    fun `criar, editar e concluir atualizam o updatedAt`() = runBlocking {
        val task = add("tarefa")
        assertEquals(1_000L, stored(task.id)!!.updatedAt)

        now = 2_000L
        repository.updateTask(task.copy(title = "editada"), emptySet())
        assertEquals(2_000L, stored(task.id)!!.updatedAt)

        now = 3_000L
        repository.toggleCompleted(stored(task.id)!!, emptySet(), today)
        assertEquals(3_000L, stored(task.id)!!.updatedAt)
    }

    @Test
    fun `excluir tarefa marca deletedAt e a tira das listas`() = runBlocking {
        val task = add("apagar")

        now = 5_000L
        repository.deleteTask(task)

        assertEquals(emptyList<String>(), titlesOn(today))
        assertNull(stored(task.id))
        assertEquals(5_000L to 5_000L, rawRow(task.id))
        assertEquals(emptyList<Task>(), dao.getAllTasksOnce())
    }

    @Test
    fun `excluir tag a tira das tarefas e atualiza essas tarefas`() = runBlocking {
        repository.addTag("Casa", "#00C853")
        val tag = repository.allTags.first().single()
        val task = add("com tag", tagIds = setOf(tag.id))

        now = 7_000L
        repository.deleteTag(tag)

        assertEquals(emptyList<Tag>(), repository.allTags.first())
        assertEquals(emptyList<Tag>(), dao.getTaskWithTagsById(task.id)!!.tags)
        assertEquals(7_000L, stored(task.id)!!.updatedAt)
    }

    @Test
    fun `concluir, desmarcar e concluir de novo nao duplica a proxima ocorrencia`() = runBlocking {
        val task = add("diaria", recurrence = "DAILY")

        val done = repository.toggleCompleted(task, emptySet(), today)
        val undone = repository.toggleCompleted(done, emptySet(), today)
        repository.toggleCompleted(undone, emptySet(), today)

        assertEquals(listOf("diaria"), titlesOn(tomorrow))
    }

    @Test
    fun `ocorrencias da mesma serie compartilham o seriesId`() = runBlocking {
        val first = add("diaria", recurrence = "DAILY")

        val done = repository.toggleCompleted(first, emptySet(), today)
        val next = repository.tasksForDate(tomorrow, today).first().single().task

        assertEquals(first.id, done.seriesId)
        assertEquals(first.id, next.seriesId)
        assertEquals(Ids.occurrenceId(first.id, tomorrow), next.id)
    }

    @Test
    fun `ocorrencia excluida nao volta ao concluir de novo`() = runBlocking {
        val task = add("diaria", recurrence = "DAILY")
        val done = repository.toggleCompleted(task, emptySet(), today)
        val next = repository.tasksForDate(tomorrow, today).first().single().task
        repository.deleteTask(next)

        val undone = repository.toggleCompleted(done, emptySet(), today)
        repository.toggleCompleted(undone, emptySet(), today)

        assertEquals(emptyList<String>(), titlesOn(tomorrow))
    }

    @Test
    fun `proxima ocorrencia herda as tags`() = runBlocking {
        repository.addTag("Academia", "#3D5AFE")
        val tag = repository.allTags.first().single()
        val task = add("treino", recurrence = "DAILY", tagIds = setOf(tag.id))

        repository.toggleCompleted(task, setOf(tag.id), today)

        val next = repository.tasksForDate(tomorrow, today).first().single()
        assertEquals(listOf("Academia"), next.tags.map { it.name })
    }

    @Test
    fun `tarefa sem recorrencia continua sem seriesId`() = runBlocking {
        val task = add("simples")

        val done = repository.toggleCompleted(task, emptySet(), today)

        assertNull(done.seriesId)
        assertTrue(done.completed)
        assertNotNull(stored(task.id))
    }
    @Test
    fun `edicao aberta antes de concluir no servidor preserva conclusao e datas atuais`() = runBlocking {
        val task = add("editar")
        val old = task.copy(date = today.minusDays(5), originalDate = today.minusDays(5))
        dao.update(old)
        val completed = old.copy(completed = true, date = today, updatedAt = 5000, needsSync = false)
        dao.update(completed)
        repository.updateTask(old.copy(title = "Título novo"), emptySet(), selectedDate = today, today = today)
        val edited = stored(task.id)!!
        assertTrue(edited.completed)
        assertEquals(today, edited.date)
        assertEquals(old.originalDate, edited.originalDate)
        assertEquals("Título novo", edited.title)
        assertTrue(edited.updatedAt > completed.updatedAt)
        assertTrue(edited.needsSync)
    }

    @Test
    fun `tarefa excluida recebida do servidor nao e restaurada por formulario antigo`() = runBlocking {
        val task = add("excluída")
        dao.update(task.copy(deletedAt = 5000, updatedAt = 5000, needsSync = false))
        assertNull(repository.updateTask(task.copy(title = "antiga"), emptySet()))
        assertNull(stored(task.id))
        assertTrue(dao.pendingSync().isEmpty())
    }

}
