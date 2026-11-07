package com.marcuspaulo.tarefas.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Application simples: o TarefasApplication agendaria alarmes e workers durante o teste. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TaskScheduleTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val yesterday = today.minusDays(1)
    private val lastWeek = today.minusDays(7)
    private val tomorrow = today.plusDays(1)

    private lateinit var database: AppDatabase
    private lateinit var dao: TaskDao
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.taskDao()
        repository = TaskRepository(dao, database.tagDao())
    }

    @After
    fun tearDown() = database.close()

    private fun insert(title: String, date: LocalDate, completed: Boolean = false, recurrence: String? = null): String =
        runBlocking {
            val task = Task(title = title, date = date, completed = completed, recurrence = recurrence)
            dao.insert(task)
            task.id
        }

    private fun titlesOn(date: LocalDate): List<String> = runBlocking {
        repository.tasksForDate(date, today).first().map { it.task.title }
    }

    private fun stored(id: String): Task = runBlocking { dao.getById(id)!! }

    @Test
    fun `pendente atrasada aparece em hoje e nao no dia em que atrasou`() {
        insert("atrasada", lastWeek)

        assertEquals(listOf("atrasada"), titlesOn(today))
        assertEquals(emptyList<String>(), titlesOn(lastWeek))
    }

    @Test
    fun `passar dos dias nao grava nada no banco`() {
        val id = insert("atrasada", lastWeek)
        val before = stored(id)

        titlesOn(today)

        assertEquals(before, stored(id))
    }

    @Test
    fun `concluida fica no proprio dia e futura nao adianta`() {
        insert("concluida", yesterday, completed = true)
        insert("futura", tomorrow)

        assertEquals(listOf("concluida"), titlesOn(yesterday))
        assertEquals(emptyList<String>(), titlesOn(today))
        assertEquals(listOf("futura"), titlesOn(tomorrow))
    }

    @Test
    fun `contagem do calendario soma as atrasadas em hoje`() = runBlocking {
        insert("atrasada 1", lastWeek)
        insert("atrasada 2", yesterday)
        insert("de hoje", today)
        insert("futura", tomorrow)
        insert("concluida", yesterday, completed = true)

        val counts = repository
            .pendingCountsBetween(lastWeek, tomorrow, today)
            .first()
            .associate { it.date to it.count }

        assertEquals(mapOf(today to 3, tomorrow to 1), counts)
    }

    @Test
    fun `contagem ignora atrasadas quando hoje esta fora do mes visivel`() = runBlocking {
        insert("atrasada", lastWeek)

        val counts = repository.pendingCountsBetween(lastWeek, yesterday, today).first()

        assertEquals(emptyList<DateCount>(), counts)
    }

    @Test
    fun `concluir atrasada registra hoje e mantem a data original`() = runBlocking {
        val id = insert("atrasada", lastWeek)

        repository.toggleCompleted(stored(id), emptySet(), today)

        val task = stored(id)
        assertTrue(task.completed)
        assertEquals(today, task.date)
        assertEquals(lastWeek, task.originalDate)
        assertEquals(listOf("atrasada"), titlesOn(today))
    }

    @Test
    fun `recorrente atrasada gera a proxima ocorrencia a partir de hoje`() = runBlocking {
        val id = insert("diaria", lastWeek, recurrence = "DAILY")

        repository.toggleCompleted(stored(id), emptySet(), today)

        assertEquals(listOf("diaria"), titlesOn(tomorrow))
    }

    @Test
    fun `desmarcar antiga traz a tarefa de volta para hoje`() = runBlocking {
        val id = insert("concluida", lastWeek, completed = true)

        repository.toggleCompleted(stored(id), emptySet(), today)

        assertFalse(stored(id).completed)
        assertEquals(listOf("concluida"), titlesOn(today))
        assertEquals(emptyList<String>(), titlesOn(lastWeek))
    }

    @Test
    fun `editar sem mudar o dia exibido preserva o atraso`() {
        val task = Task(title = "atrasada", date = lastWeek)

        assertEquals(task, task.rescheduledTo(today, today))
    }

    @Test
    fun `mudar o dia na edicao recomeca a contagem do atraso`() {
        val task = Task(title = "atrasada", date = lastWeek)

        val moved = task.rescheduledTo(tomorrow, today)

        assertEquals(tomorrow, moved.date)
        assertEquals(tomorrow, moved.originalDate)
    }

    @Test
    fun `tarefa ja empurrada pelo rollover antigo continua como estava`() = runBlocking {
        val task = Task(title = "antiga", date = yesterday, originalDate = lastWeek)
        dao.insert(task)
        val id = task.id

        assertEquals(listOf("antiga"), titlesOn(today))
        assertEquals(lastWeek, stored(id).originalDate)
    }
}
