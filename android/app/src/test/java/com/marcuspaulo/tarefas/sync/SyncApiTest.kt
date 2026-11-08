package com.marcuspaulo.tarefas.sync

import android.app.Application
import com.marcuspaulo.tarefas.data.Task
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.Closeable
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SyncApiTest {
    @Test fun `datas horarios nulos e tags usam o contrato do backend`() {
        val task = Task(title = "Atrasada", date = LocalDate.of(2026, 11, 10), originalDate = LocalDate.of(2026, 11, 5),
            createdAt = 1000, updatedAt = 2001, deletedAt = 2001, completed = true)
        val encoded = HttpSyncApi.taskJson(SyncTask(task, listOf("tag")))
        assertEquals("2026-11-05", encoded.getString("original_date"))
        assertEquals(2001L, encoded.getLong("updated_at"))
        assertTrue(encoded.isNull("series_id")); assertTrue(encoded.isNull("deadline"))
        assertFalse(encoded.has("needsSync")); assertFalse(encoded.has("sync_version"))
        val decoded = HttpSyncApi.parseTask(encoded)
        assertEquals(task.copy(needsSync = false), decoded.task)
        assertEquals(listOf("tag"), decoded.tagIds)
    }

    @Test fun `servidor e normalizado sem permitir credenciais ou http publico`() {
        assertEquals("https://example.test", HttpSyncApi.normalizeServer("https://EXAMPLE.test:443/api/v1/"))
        assertEquals("http://10.0.2.2:8000", HttpSyncApi.normalizeServer("http://10.0.2.2:8000/", true))
        for (url in listOf("http://example.test", "http://10.evil.test", "http://192.168.evil.test", "https://user:secret@example.test", "https://example.test?token=x")) {
            try { HttpSyncApi.normalizeServer(url, true); fail(url) } catch (_: IllegalArgumentException) { }
        }
        try { HttpSyncApi.normalizeServer("http://10.0.2.2:8000", false); fail("HTTP no release") } catch (_: IllegalArgumentException) { }
    }

    @Test fun `cliente autentica com token e envia json para a rota de sincronizacao`() = runBlocking {
        TestServer("""{"data":{"cursor":7,"server_time":2000,"tasks":[],"tags":[]}}""").use { server ->
            val session = Session(server.url, 1, "Test", "test@example.test", "test-token")
            val result = HttpSyncApi().exchange(session, SyncBatch(4))
            val request = server.request()
            assertEquals("POST /api/v1/sync HTTP/1.1", request.line)
            assertEquals("Bearer test-token", request.headers["authorization"])
            assertEquals(4, request.body.getInt("cursor"))
            assertEquals(7L, result.cursor)
        }
    }

    @Test fun `login usa o formato real de usuario do Laravel`() = runBlocking {
        TestServer("""{"token":"test-token","user":{"id":4,"name":"Teste","email":"test@example.test"}}""").use { server ->
            val result = HttpSyncApi().login(server.url, "test@example.test", "test-password")
            assertEquals(4L, result.userId)
            assertEquals("test-token", result.token)
            val request = server.request()
            assertEquals("POST /api/v1/login HTTP/1.1", request.line)
            assertEquals("test@example.test", request.body.getString("email"))
            assertTrue(request.body.has("device_name"))
        }
    }

    @Test fun `sessao expirada e redirecionamento sao devolvidos sem seguir outro servidor`() = runBlocking {
        for (status in listOf(401, 302)) {
            TestServer("{}", status).use { server ->
                try {
                    HttpSyncApi().exchange(Session(server.url, 1, "Test", "test@example.test", "test-token"), SyncBatch(0))
                    fail("Esperava HTTP $status")
                } catch (error: ApiException) { assertEquals(status, error.status) }
                server.request()
            }
        }
    }

    private data class CapturedRequest(val line: String, val headers: Map<String, String>, val body: JSONObject)

    /** Servidor HTTP de uma chamada; exercita HttpURLConnection sem dependências extras. */
    private class TestServer(body: String, status: Int = 200) : Closeable {
        private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5000 }
        val url = "http://127.0.0.1:${socket.localPort}"
        private val executor = Executors.newSingleThreadExecutor()
        private val captured = executor.submit<CapturedRequest> {
            socket.accept().use { client ->
                client.soTimeout = 5000
                val input = client.getInputStream()
                val header = StringBuilder()
                while (!header.endsWith("\r\n\r\n")) {
                    val byte = input.read()
                    check(byte >= 0)
                    header.append(byte.toChar())
                }
                val lines = header.toString().split("\r\n")
                val headers = lines.drop(1).filter { ':' in it }.associate {
                    it.substringBefore(':').lowercase() to it.substringAfter(':').trim()
                }
                val content = ByteArray(headers.getValue("content-length").toInt())
                var read = 0
                while (read < content.size) {
                    val count = input.read(content, read, content.size - read)
                    check(count > 0)
                    read += count
                }
                val response = body.toByteArray()
                val output = client.getOutputStream()
                output.write(("HTTP/1.1 $status Response\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${response.size}\r\nLocation: https://example.invalid/other\r\nConnection: close\r\n\r\n").toByteArray())
                output.write(response)
                output.flush()
                CapturedRequest(lines.first(), headers, JSONObject(String(content, Charsets.UTF_8)))
            }
        }
        fun request(): CapturedRequest = captured.get(10, TimeUnit.SECONDS)
        override fun close() { socket.close(); executor.shutdownNow() }
    }
}
