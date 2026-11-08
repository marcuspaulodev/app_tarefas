package com.marcuspaulo.tarefas.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.Ids
import com.marcuspaulo.tarefas.data.Priority
import com.marcuspaulo.tarefas.data.Task
import com.marcuspaulo.tarefas.data.TaskRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.time.LocalDate
import java.util.UUID

/** Executado pelo AndroidSyncIntegrationTest do Laravel, com MySQL e HTTP reais. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SyncIntegrationTest {
    private lateinit var context: Context
    private lateinit var session: Session
    private lateinit var db: AppDatabase
    private val api = HttpSyncApi()
    private val databases = linkedMapOf<AppDatabase, String>()
    private val day = LocalDate.of(2026, 11, 10)
    private var now = System.currentTimeMillis() - 60_000

    @Before fun setUp() = runBlocking {
        assumeTrue("Inicie pelo teste de integração do Laravel", System.getenv("SYNC_TEST_URL") != null)
        require(URI(server).host == "127.0.0.1" && URI(server).scheme == "http")
        require(prefix.matches(Regex("sync-[a-f0-9]{24}-")))
        context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("account_workspaces", Context.MODE_PRIVATE).edit().clear().commit()
        val registered = registerAccount()
        session = api.login(server, registered.email, password)
        assertEquals(registered.key, session.key)
        assertNotEquals(registered.token, session.token)
        db = openDatabase()
    }

    @After fun tearDown() {
        databases.keys.forEach { it.close() }
        databases.values.toSet().forEach { context.deleteDatabase(it) }
    }

    @Test fun initialImportAndWebRoundTrip() = runBlocking {
        val repo = repository()
        listOf("Trabalho" to "#3D5AFE", "Casa" to "#00C853", "Faculdade" to "#FF6D00").forEach {
            repo.addTag(it.first, it.second)
        }
        val localTag = db.tagDao().getAllTagsOnce().single { it.name == "Casa" }
        val original = repo.addTask("Revisão 📝", "Descrição\ncom acentos", day.minusDays(3), day.plusDays(2),
            Priority.HIGH, "WEEKLY:1,3,5", setOf(localTag.id))
        sync()

        val imported = remote().tasks.single()
        assertEquals(original.copy(needsSync = false), imported.task)
        assertEquals(3, remote().tags.size)
        assertNotEquals(localTag.id, imported.tagIds.single())
        assertEquals("Casa", db.taskDao().getTaskWithTagsById(original.id)!!.tags.single().name)

        request(session, "PATCH", "tasks/${original.id}", JSONObject().put("title", "Editada no web"))
        val created = request(session, "POST", "tasks", JSONObject().put("title", "Criada no web")
            .put("date", day.toString()), 201).getJSONObject("data").getString("id")
        sync()
        val updated = db.taskDao().getById(original.id)!!
        assertEquals("Editada no web", updated.title)
        assertEquals(original.originalDate, updated.originalDate)
        assertEquals(original.deadline, updated.deadline)
        assertEquals("Criada no web", db.taskDao().getById(created)!!.title)
        assertConverged(db)
        val cursor = db.syncStateDao().get()!!.cursor
        sync()
        assertEquals(cursor, db.syncStateDao().get()!!.cursor)
    }

    @Test fun conflictingEditsConvergeAcrossTwoClients() = runBlocking {
        val task = addTask()
        sync()
        val other = openDatabase()
        sync(other)
        now = task.updatedAt + 10
        repository().updateTask(task.copy(title = "Edição antiga"), emptySet())
        now += 10
        repository(other).updateTask(task.copy(title = "Edição recente"), emptySet())
        sync(other)
        val newestCursor = remote().cursor
        sync()
        assertEquals("Edição recente", db.taskDao().getById(task.id)!!.title)
        assertEquals(newestCursor, remote().cursor)

        now += 10
        repository().updateTask(task.copy(title = "Primeira no empate"), emptySet())
        repository(other).updateTask(task.copy(title = "Segunda no empate"), emptySet())
        sync()
        val tiedCursor = remote().cursor
        sync(other)
        assertEquals("Primeira no empate", other.taskDao().getById(task.id)!!.title)
        assertEquals(tiedCursor, remote().cursor)

        request(session, "PATCH", "tasks/${task.id}", JSONObject().put("title", "Web"))
        now = remote().tasks.single().task.updatedAt + 1
        repository().updateTask(task.copy(title = "Android depois do web"), emptySet())
        sync()
        assertEquals("Android depois do web", remote().tasks.single().task.title)
        request(session, "PATCH", "tasks/${task.id}", JSONObject().put("title", "Web depois do Android"))
        sync()
        sync(other)
        assertEquals("Web depois do Android", db.taskDao().getById(task.id)!!.title)
        assertConverged(db)
        assertConverged(other)
    }

    @Test fun lostResponseAfterCommitCanBeReplayedAfterRestart() = runBlocking {
        sync()
        val before = db.syncStateDao().get()
        val task = addTask()
        val lostResponse = intercept { _, _ -> throw IOException("Resposta perdida depois do commit") }
        expectNetworkFailure { sync(client = lostResponse) }
        assertEquals(before, db.syncStateDao().get())
        assertTrue(db.taskDao().getById(task.id)!!.needsSync)
        val committed = remote()
        assertEquals(task.id, committed.tasks.single().task.id)

        db = reopen(db)
        sync()
        assertEquals(committed, remote())
        assertEquals(committed.cursor, db.syncStateDao().get()!!.cursor)
        assertConverged(db)
    }

    @Test fun offlineQueueSurvivesDatabaseReopen() = runBlocking {
        val edited = addTask(title = "Antes")
        val deleted = addTask(title = "Excluir offline")
        sync()
        val before = db.syncStateDao().get()
        now += 100
        repository().updateTask(edited.copy(title = "Depois"), emptySet())
        repository().deleteTask(deleted)
        val created = addTask(title = "Criada offline")
        val closedPort = ServerSocket(0).use { it.localPort }
        val disconnected = object : SyncApi {
            override suspend fun exchange(session: Session, batch: SyncBatch): SyncResponse =
                api.exchange(session.copy(server = "http://127.0.0.1:$closedPort"), batch)
        }
        expectNetworkFailure { sync(client = disconnected) }
        db = reopen(db)
        assertEquals(before, db.syncStateDao().get())
        assertEquals(3, db.taskDao().pendingSync().size)
        assertEquals("Depois", db.taskDao().getById(edited.id)!!.title)
        assertNotNull(db.taskDao().getIncludingDeleted(deleted.id)!!.deletedAt)
        assertNotNull(db.taskDao().getById(created.id))
        sync()
        val other = openDatabase()
        sync(other)
        assertConverged(db)
        assertConverged(other)
        assertEquals(2, other.taskDao().getAllTasksOnce().size)
    }

    @Test fun recurringCompletionOnWebAndTwoClientsDoesNotRestoreDeletedOccurrence() = runBlocking {
        val original = addTask(date = day.minusDays(3), recurrence = "DAILY")
        sync()
        val other = openDatabase()
        sync(other)
        repository().toggleCompleted(original, emptySet(), day)
        repository(other).toggleCompleted(original, emptySet(), day)
        val nextId = Ids.occurrenceId(original.id, day.plusDays(1))
        request(session, "POST", "tasks/${original.id}/toggle", JSONObject().put("today", day.toString()))
        sync()
        sync(other)
        assertEquals(2, remote().tasks.size)
        assertEquals(nextId, remote().tasks.single { !it.task.completed }.task.id)
        assertEquals(day, db.taskDao().getById(original.id)!!.date)
        assertEquals(day.minusDays(3), db.taskDao().getById(original.id)!!.originalDate)

        request(session, "DELETE", "tasks/$nextId", expected = 204)
        now = remote().tasks.single { it.task.id == nextId }.task.updatedAt + 1
        repository(other).updateTask(other.taskDao().getById(nextId)!!.copy(title = "Edição offline"), emptySet())
        sync(other)
        sync()
        assertNull(db.taskDao().getById(nextId))
        val cursor = remote().cursor
        sync(other)
        assertEquals(cursor, remote().cursor)
        assertEquals(2, remote().tasks.size)
        assertConverged(db)
        assertConverged(other)
    }

    @Test fun remoteDeletionsRemoveReferencesWithoutLosingNewerLocalEdits() = runBlocking {
        repository().addTag("Etiqueta", "#112233")
        val tag = db.tagDao().getAllTagsOnce().single()
        val task = addTask(tags = setOf(tag.id))
        sync()
        val other = openDatabase()
        sync(other)
        request(session, "DELETE", "tags/${tag.id}", expected = 204)
        now = remote().tasks.single().task.updatedAt + 1
        repository().updateTask(task.copy(title = "Edição mais recente"), setOf(tag.id))
        sync()
        sync(other)
        assertEquals("Edição mais recente", db.taskDao().getById(task.id)!!.title)
        assertTrue(db.taskDao().getTaskWithTagsById(task.id)!!.tags.isEmpty())
        assertNotNull(db.tagDao().getIncludingDeleted(tag.id)!!.deletedAt)
        request(session, "DELETE", "tasks/${task.id}", expected = 204)
        now = remote().tasks.single().task.updatedAt + 1
        repository(other).updateTask(task.copy(title = "Não restaurar"), emptySet())
        sync(other)
        sync()
        assertNull(db.taskDao().getById(task.id))
        assertConverged(db)
        assertConverged(other)
    }

    @Test fun largeInitialImportRespectsBatchLimitsAndTagDependencies() = runBlocking {
        repeat(102) { repository().addTag("Etiqueta $it", "#112233") }
        val tagId = db.tagDao().getAllTagsOnce().first().id
        repeat(205) { addTask(title = "Tarefa $it", tags = setOf(tagId)) }
        val batches = mutableListOf<SyncBatch>()
        sync(client = intercept { batch, _ -> batches += batch })
        assertEquals(listOf(100, 2), batches.filter { it.tags.isNotEmpty() }.map { it.tags.size })
        assertEquals(listOf(100, 100, 5), batches.filter { it.tasks.isNotEmpty() }.map { it.tasks.size })
        val result = remote()
        assertEquals(205, result.tasks.size)
        assertEquals(105, result.tags.size)
        assertTrue(result.tasks.all { it.tagIds == listOf(tagId) })
        assertConverged(db)
        val other = openDatabase()
        sync(other)
        assertConverged(other)
    }

    @Test fun editingDuringTheResponseIsUploadedInTheNextBatch() = runBlocking {
        sync()
        val task = addTask()
        val batches = mutableListOf<SyncBatch>()
        sync(client = intercept { batch, _ ->
            batches += batch
            if (batches.size == 1) repository().updateTask(task.copy(title = "Editada durante envio"), emptySet())
        })
        assertEquals(2, batches.size)
        assertTrue(batches[1].tasks.single().task.updatedAt > batches[0].tasks.single().task.updatedAt)
        assertEquals("Editada durante envio", remote().tasks.single().task.title)
        assertConverged(db)
    }

    @Test fun switchingAccountsDuringResponseKeepsDatabasesAndCursorsSeparate() = runBlocking {
        val cipher = object : SessionCipher {
            override fun encrypt(value: String) = value.reversed()
            override fun decrypt(value: String) = value.reversed()
        }
        var store = SessionStore(context, cipher)
        store.connect(session)
        db = openDatabase(store.databaseName(session)!!)
        sync()
        val before = db.syncStateDao().get()
        val firstTask = addTask(title = "Conta A")
        val second = registerAccount()
        val engine = SyncEngine(db, intercept { _, _ -> store.logout(); store.connect(second) })
        assertTrue(engine.synchronize(session) { store.session.value == session })
        assertEquals(before, db.syncStateDao().get())
        assertTrue(db.taskDao().getById(firstTask.id)!!.needsSync)
        assertNull(store.databaseName(null))

        val secondDb = openDatabase(store.databaseName(second)!!)
        sync(secondDb, second)
        assertTrue(secondDb.taskDao().getIncludingDeleted().isEmpty())
        val secondTask = addTask(secondDb, title = "Conta B")
        sync(secondDb, second)
        request(second, "GET", "tasks/${firstTask.id}", expected = 404)
        request(session, "GET", "tasks/${secondTask.id}", expected = 404)
        store.logout()
        store = SessionStore(context, cipher)
        assertNull(store.workspaceKey)
        val returned = api.login(server, session.email, password)
        store.connect(returned)
        assertEquals(databases[db], store.databaseName(returned))
        db = reopen(db)
        sync(db, returned)
        assertEquals(firstTask.id, db.taskDao().getAllTasksOnce().single().id)
        assertEquals(secondTask.id, secondDb.taskDao().getAllTasksOnce().single().id)
        assertConverged(db, returned)
        assertConverged(secondDb, second)
    }

    @Test fun revokedTokenPreservesPendingChangesUntilLoginWithoutRevokingOtherDevice() = runBlocking {
        val task = addTask()
        sync()
        val before = db.syncStateDao().get()
        val otherSession = api.login(server, session.email, password)
        repository().updateTask(task.copy(title = "Pendente de login"), emptySet())
        api.logout(session)
        try {
            sync()
            fail("Token encerrado deve retornar 401")
        } catch (error: ApiException) { assertEquals(401, error.status) }
        assertEquals(before, db.syncStateDao().get())
        assertTrue(db.taskDao().getById(task.id)!!.needsSync)
        assertEquals(task.title, remote(otherSession).tasks.single().task.title)
        val renewed = api.login(server, session.email, password)
        assertEquals(session.key, renewed.key)
        sync(account = renewed)
        assertEquals("Pendente de login", remote(otherSession).tasks.single().task.title)
        assertConverged(db, renewed)
    }

    private fun openDatabase(name: String = "sync-${UUID.randomUUID()}.db"): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
            .also { databases[it] = name }

    private fun reopen(database: AppDatabase): AppDatabase {
        val name = databases.getValue(database)
        database.close()
        return openDatabase(name)
    }

    private fun repository(database: AppDatabase = db) = TaskRepository(database) { now }

    private suspend fun addTask(database: AppDatabase = db, title: String = "Local", date: LocalDate = day,
                                recurrence: String? = null, tags: Set<String> = emptySet()): Task =
        repository(database).addTask(title, "", date, null, Priority.MEDIUM, recurrence, tags)

    private suspend fun sync(database: AppDatabase = db, account: Session = session, client: SyncApi = api) {
        assertTrue("A fila deve terminar nesta execução", SyncEngine(database, client).synchronize(account))
    }

    private suspend fun remote(account: Session = session) = api.exchange(account, SyncBatch(0))

    private suspend fun assertConverged(database: AppDatabase, account: Session = session) {
        val result = remote(account)
        assertEquals(result.tasks.associate { it.task.id to it.task }, database.taskDao().getIncludingDeleted().associateBy { it.id })
        assertEquals(result.tags.associateBy { it.id }, database.tagDao().getIncludingDeleted().associateBy { it.id })
        for (item in result.tasks.filter { it.task.deletedAt == null }) {
            assertEquals(item.tagIds.toSet(), database.taskDao().getTaskWithTagsById(item.task.id)!!.tags.map { it.id }.toSet())
        }
        assertEquals(result.cursor, database.syncStateDao().get()!!.cursor)
        assertTrue(database.taskDao().pendingSync().isEmpty())
        assertTrue(database.tagDao().pendingSync().isEmpty())
    }

    private fun intercept(after: suspend (SyncBatch, SyncResponse) -> Unit) = object : SyncApi {
        override suspend fun exchange(session: Session, batch: SyncBatch): SyncResponse =
            api.exchange(session, batch).also { after(batch, it) }
    }

    private suspend fun expectNetworkFailure(block: suspend () -> Unit) {
        try { block(); fail("Esperava falha de transporte") }
        catch (error: IOException) { assertFalse("Não deve ser erro da API: $error", error is ApiException) }
    }

    private fun registerAccount(): Session {
        val json = request(null, "POST", "register", JSONObject().put("name", "Integração Android")
            .put("email", "$prefix${UUID.randomUUID()}@example.test").put("password", password)
            .put("password_confirmation", password).put("device_name", "Teste"), 201)
        val user = json.getJSONObject("user")
        return Session(server, user.getLong("id"), user.getString("name"), user.getString("email"), json.getString("token"))
    }

    /** As escritas usam os mesmos endpoints do Vue; PATCH via override suportado pelo Laravel. */
    private fun request(account: Session?, method: String, path: String, body: JSONObject? = null, expected: Int = 200): JSONObject {
        val connection = URI("$server/api/v1/$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")
            account?.let { connection.setRequestProperty("Authorization", "Bearer ${it.token}") }
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            assertEquals("$method $path: $response", expected, status)
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally { connection.disconnect() }
    }

    companion object {
        private val server: String get() = System.getenv("SYNC_TEST_URL")!!
        private val prefix: String get() = System.getenv("SYNC_TEST_PREFIX")!!
        private const val password = "Sync-integration-123!"

    }
}
