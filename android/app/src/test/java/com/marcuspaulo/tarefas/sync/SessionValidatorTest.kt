package com.marcuspaulo.tarefas.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.Priority
import com.marcuspaulo.tarefas.data.SyncState
import com.marcuspaulo.tarefas.data.TaskRepository
import kotlinx.coroutines.CancellationException
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
class SessionValidatorTest {
    private lateinit var context: Context
    private lateinit var store: SessionStore
    private val cipher = object : SessionCipher {
        override fun encrypt(value: String) = value.reversed()
        override fun decrypt(value: String) = value.reversed()
    }
    private val a = Session("https://example.test", 1, "A", "a@example.test", "token-a")
    private val b = Session("https://example.test", 2, "B", "b@example.test", "token-b")
    private val databases = mutableListOf<AppDatabase>()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("account_workspaces", Context.MODE_PRIVATE).edit().clear().commit()
        store = SessionStore(context, cipher)
    }

    @After fun tearDown() {
        databases.forEach { it.close() }
        context.deleteDatabase("tarefas.db")
    }

    @Test fun `revogacao exige login inclusive apos reinicio e preserva a fila da mesma conta`() = runBlocking {
        store.connect(a)
        val name = store.databaseName(a)!!
        val database = database(name)
        val task = TaskRepository(database).addTask("Criada offline", "", LocalDate.of(2026, 11, 10), null,
            Priority.MEDIUM, null, emptySet())
        val state = SyncState(cursor = 9, initialized = true, lastSyncedAt = 1000)
        database.syncStateDao().save(state)

        SessionValidator(store) { throw ApiException(401, "Sessão encerrada") }.check()
        assertTrue(store.session.value!!.expired)
        assertEquals("", store.session.value!!.token)
        assertNull(store.workspaceKey)
        assertNull(store.databaseName(store.session.value))
        assertNull(store.databaseName(null))
        database.close()

        val restarted = SessionStore(context, cipher)
        assertNull(restarted.workspaceKey)
        assertNull(restarted.databaseName(restarted.session.value))
        restarted.connect(a.copy(token = "novo-token"))
        assertEquals(a.key, restarted.workspaceKey)
        assertEquals(name, restarted.databaseName(restarted.session.value))
        val reopened = database(restarted.databaseName(restarted.session.value)!!)
        assertEquals(task, reopened.taskDao().pendingSync().single().task)
        assertEquals(state, reopened.syncStateDao().get())
    }

    @Test fun `falha de conexao ou do servidor mantem a sessao para funcionar offline`() = runBlocking {
        store.connect(a)
        for (error in listOf(IOException("Sem rede"), ApiException(503, "Indisponível"), ApiException(429, "Aguarde"))) {
            SessionValidator(store) { throw error }.check()
            assertEquals(a, store.session.value)
            assertEquals(a.key, store.workspaceKey)
            assertNotNull(store.databaseName(store.session.value))
        }
    }

    @Test fun `resposta antiga nao encerra outra conta nem um login renovado`() = runBlocking {
        for (replacement in listOf(b, a.copy(token = "novo-token"))) {
            store.connect(a)
            SessionValidator(store) {
                assertEquals(a, it)
                store.connect(replacement)
                throw ApiException(401, "Resposta atrasada")
            }.check()
            assertEquals(replacement, store.session.value)
            assertEquals(replacement.key, store.workspaceKey)
        }
    }

    @Test fun `somente uma sessao ativa e verificada`() = runBlocking {
        var calls = 0
        val validator = SessionValidator(store) { assertEquals(a, it); calls++ }
        validator.check()
        store.connect(a)
        validator.check()
        assertEquals(a, store.session.value)
        store.expire(a)
        validator.check()
        assertEquals(1, calls)
    }

    @Test fun `sair da tela cancela a verificacao sem encerrar a sessao`() = runBlocking {
        store.connect(a)
        try {
            SessionValidator(store) { throw CancellationException() }.check()
            fail("Esperava cancelamento")
        } catch (_: CancellationException) { }
        assertEquals(a, store.session.value)
    }

    private fun database(name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
            .also { databases += it }
}
