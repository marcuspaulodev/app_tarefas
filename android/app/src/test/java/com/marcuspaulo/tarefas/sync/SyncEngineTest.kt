package com.marcuspaulo.tarefas.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.Ids
import com.marcuspaulo.tarefas.data.Priority
import com.marcuspaulo.tarefas.data.SyncState
import com.marcuspaulo.tarefas.data.Tag
import com.marcuspaulo.tarefas.data.Task
import com.marcuspaulo.tarefas.data.TaskRepository
import com.marcuspaulo.tarefas.data.TaskTagCrossRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SyncEngineTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TaskRepository
    private val day = LocalDate.of(2026, 11, 10)
    private var now = 1000L
    private val session = Session("https://example.test", 1, "Conta", "conta@example.test", "test-token")

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = TaskRepository(db) { now }
        db.syncStateDao().save(SyncState(initialized = true))
    }
    @After fun tearDown() { db.close() }

    private suspend fun task(title: String = "Local", recurrence: String? = null, tags: Set<String> = emptySet()): Task =
        repository.addTask(title, "", day, null, Priority.MEDIUM, recurrence, tags)

    private fun api(block: suspend (SyncBatch) -> SyncResponse) = object : SyncApi {
        override suspend fun exchange(session: Session, batch: SyncBatch): SyncResponse {
            assertEquals(this@SyncEngineTest.session, session)
            return block(batch)
        }
    }
    private fun echo(batch: SyncBatch) = SyncResponse(batch.cursor + 1, batch.tasks.map { it.copy(task = it.task.copy(needsSync = false)) },
        batch.tags.map { it.copy(needsSync = false) })

    @Test fun `primeiro acesso concilia tags padrao preservando os vinculos`() = runBlocking {
        db.syncStateDao().save(SyncState())
        repository.addTag("Casa", "#00C853")
        val original = db.tagDao().getAllTagsOnce().single()
        val local = task(tags = setOf(original.id))
        val remoteTag = original.copy(id = Ids.newId(), updatedAt = 2000, needsSync = false)
        var calls = 0
        val engine = SyncEngine(db, api { batch ->
            calls++
            if (calls == 1) {
                assertTrue(batch.tasks.isEmpty()); assertTrue(batch.tags.isEmpty())
                SyncResponse(1, emptyList(), listOf(remoteTag))
            } else {
                assertTrue(batch.tags.isEmpty())
                assertEquals(listOf(remoteTag.id), batch.tasks.single().tagIds)
                echo(batch)
            }
        }) { now }
        assertTrue(engine.synchronize(session))
        assertEquals(2, calls)
        assertEquals(listOf(remoteTag.id), db.tagDao().getAllTagsOnce().map { it.id })
        assertEquals(listOf(remoteTag.id), db.taskDao().getTaskWithTagsById(local.id)!!.tags.map { it.id })
        assertFalse(db.taskDao().getById(local.id)!!.needsSync)
        assertEquals(2L, db.syncStateDao().get()!!.cursor)
    }

    @Test fun `tag padrao excluida localmente continua excluida apos importar`() = runBlocking {
        db.syncStateDao().save(SyncState())
        repository.addTag("Casa", "#00C853")
        val original = db.tagDao().getAllTagsOnce().single()
        repository.deleteTag(original)
        val remote = original.copy(id = Ids.newId(), updatedAt = 2000, needsSync = false)
        var calls = 0
        val engine = SyncEngine(db, api { batch ->
            if (++calls == 1) SyncResponse(1, emptyList(), listOf(remote)) else {
                assertEquals(remote.id, batch.tags.single().id)
                assertNotNull(batch.tags.single().deletedAt)
                assertTrue(batch.tags.single().updatedAt > remote.updatedAt)
                echo(batch)
            }
        }) { now }
        assertTrue(engine.synchronize(session))
        assertTrue(db.tagDao().getAllTagsOnce().isEmpty())
    }

    @Test fun `falha de rede preserva fila e cursor para reenvio`() = runBlocking {
        val local = task()
        val engine = SyncEngine(db, api { throw IOException("offline") })
        try { engine.synchronize(session); fail("Esperava falha de rede") } catch (_: IOException) { }
        assertTrue(db.taskDao().getById(local.id)!!.needsSync)
        assertEquals(0L, db.syncStateDao().get()!!.cursor)
        assertTrue(SyncEngine(db, api { echo(it) }).synchronize(session))
        assertFalse(db.taskDao().getById(local.id)!!.needsSync)
    }

    @Test fun `edicao durante envio fica pendente e segue no lote seguinte mesmo com relogio parado`() = runBlocking {
        val local = task()
        val payloads = mutableListOf<SyncBatch>()
        val engine = SyncEngine(db, api { batch ->
            payloads += batch
            if (payloads.size == 1) repository.updateTask(local.copy(title = "Editada durante envio"), emptySet())
            echo(batch)
        })
        assertTrue(engine.synchronize(session))
        assertEquals(2, payloads.size)
        assertEquals("Local", payloads[0].tasks.single().task.title)
        assertEquals("Editada durante envio", payloads[1].tasks.single().task.title)
        assertTrue(payloads[1].tasks.single().task.updatedAt > payloads[0].tasks.single().task.updatedAt)
        assertEquals("Editada durante envio", db.taskDao().getById(local.id)!!.title)
        assertFalse(db.taskDao().getById(local.id)!!.needsSync)
    }

    @Test fun `edicao de tag durante envio nao e confirmada pela resposta antiga`() = runBlocking {
        repository.addTag("Antes", "#000000")
        val tag = db.tagDao().getAllTagsOnce().single()
        var calls = 0
        SyncEngine(db, api { batch ->
            if (++calls == 1) repository.updateTag(tag.copy(name = "Depois"))
            else assertEquals("Depois", batch.tags.single().name)
            echo(batch)
        }).synchronize(session)
        assertEquals(2, calls)
        assertEquals("Depois", db.tagDao().getAllTagsOnce().single().name)
    }

    @Test fun `conflito confirma estado vencedor do servidor inclusive exclusao definitiva`() = runBlocking {
        val local = task()
        val deleted = local.copy(title = "No servidor", updatedAt = 500, deletedAt = 500, needsSync = false)
        SyncEngine(db, api { batch -> SyncResponse(batch.cursor, listOf(SyncTask(deleted, emptyList())), emptyList()) }).synchronize(session)
        assertNull(db.taskDao().getById(local.id))
        assertEquals(deleted, db.taskDao().getIncludingDeleted(local.id))
        assertTrue(db.taskDao().pendingSync().isEmpty())
    }

    @Test fun `resposta parcial nao confirma nem avanca cursor`() = runBlocking {
        val local = task()
        try {
            SyncEngine(db, api { SyncResponse(99, emptyList(), emptyList()) }).synchronize(session)
            fail("Esperava resposta incompleta")
        } catch (_: IOException) { }
        assertTrue(db.taskDao().getById(local.id)!!.needsSync)
        assertEquals(0L, db.syncStateDao().get()!!.cursor)
    }

    @Test fun `erro ao aplicar resposta faz rollback de dados e cursor`() = runBlocking {
        val local = task()
        val remote = Tag(name = "Nova", colorHex = "#000000", needsSync = false)
        try {
            SyncEngine(db, api { SyncResponse(1, listOf(SyncTask(local.copy(needsSync = false), listOf("inexistente"))), listOf(remote)) })
                .synchronize(session)
            fail("Esperava referencia invalida")
        } catch (_: IOException) { }
        assertTrue(db.tagDao().getAllTagsOnce().isEmpty())
        assertTrue(db.taskDao().getById(local.id)!!.needsSync)
        assertEquals(0L, db.syncStateDao().get()!!.cursor)
    }

    @Test fun `troca de conta durante requisicao nao aplica resposta`() = runBlocking {
        val local = task()
        var active = true
        SyncEngine(db, api { active = false; echo(it) }).synchronize(session) { active }
        assertTrue(db.taskDao().getById(local.id)!!.needsSync)
        assertEquals(0L, db.syncStateDao().get()!!.cursor)
    }

    @Test fun `lotes respeitam limite e enviam tags antes de tarefas`() = runBlocking {
        repeat(102) { db.tagDao().insert(Tag(name = "Tag $it", colorHex = "#000000", updatedAt = now)) }
        val tagId = db.tagDao().getAllTagsOnce().first().id
        repeat(205) { task("Tarefa $it", tags = setOf(tagId)) }
        var calls = 0
        val knownTags = mutableSetOf<String>()
        SyncEngine(db, api { batch ->
            calls++
            assertTrue(batch.tasks.size <= 100); assertTrue(batch.tags.size <= 100)
            knownTags += batch.tags.map { it.id }
            batch.tasks.forEach { assertTrue(knownTags.containsAll(it.tagIds)) }
            echo(batch)
        }).synchronize(session)
        assertEquals(5, calls)
        assertTrue(db.taskDao().pendingSync().isEmpty())
        assertTrue(db.tagDao().pendingSync().isEmpty())
    }

    @Test fun `recorrencia envia conclusao e proxima ocorrencia juntas`() = runBlocking {
        val local = task(recurrence = "DAILY")
        repository.toggleCompleted(local, emptySet(), day)
        SyncEngine(db, api { batch ->
            assertEquals(2, batch.tasks.size)
            assertTrue(batch.tasks.any { it.task.id == local.id && it.task.completed })
            assertTrue(batch.tasks.any { it.task.id == Ids.occurrenceId(local.id, day.plusDays(1)) })
            echo(batch)
        }).synchronize(session)
        assertEquals(2, db.taskDao().getAllTasksOnce().size)
    }

    @Test fun `alteracoes recebidas ao enviar tags nao apagam tarefas locais ainda nao enviadas`() = runBlocking {
        val local = task()
        repository.addTag("Pendente", "#000000")
        var calls = 0
        SyncEngine(db, api { batch ->
            if (++calls == 1) echo(batch).copy(tasks = listOf(SyncTask(local.copy(title = "Antiga no servidor", updatedAt = 500, needsSync = false), emptyList())))
            else {
                assertEquals("Local", batch.tasks.single().task.title)
                echo(batch)
            }
        }).synchronize(session)
        assertEquals("Local", db.taskDao().getById(local.id)!!.title)
        assertFalse(db.taskDao().getById(local.id)!!.needsSync)
    }

    @Test fun `exclusao remota de tag remove vinculo inclusive em tarefa pendente`() = runBlocking {
        val tag = Tag(name = "Tag", colorHex = "#000000", updatedAt = now, needsSync = false)
        db.tagDao().insert(tag)
        val local = task(tags = setOf(tag.id))
        val remoteDeleted = tag.copy(deletedAt = now + 1, updatedAt = now + 1)
        var calls = 0
        SyncEngine(db, api { batch ->
            if (++calls == 1) {
                repository.updateTask(local.copy(title = "Mais nova"), setOf(tag.id))
                echo(batch).copy(tags = listOf(remoteDeleted))
            } else {
                assertTrue(batch.tasks.single().tagIds.isEmpty())
                echo(batch)
            }
        }).synchronize(session)
        assertTrue(db.taskDao().getTaskWithTagsById(local.id)!!.tags.isEmpty())
    }
}
