package com.marcuspaulo.tarefas.sync

import android.os.Build
import com.marcuspaulo.tarefas.BuildConfig
import com.marcuspaulo.tarefas.data.Priority
import com.marcuspaulo.tarefas.data.Tag
import com.marcuspaulo.tarefas.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate

data class SyncTask(val task: Task, val tagIds: List<String>)
data class SyncBatch(val cursor: Long, val tasks: List<SyncTask> = emptyList(), val tags: List<Tag> = emptyList())
data class SyncResponse(val cursor: Long, val tasks: List<SyncTask>, val tags: List<Tag>)

interface SyncApi {
    suspend fun exchange(session: Session, batch: SyncBatch): SyncResponse
}

class ApiException(val status: Int, message: String) : IOException(message)

/** Não segue redirecionamentos: a credencial só é enviada ao servidor escolhido. */
class HttpSyncApi : SyncApi {
    suspend fun login(server: String, email: String, password: String): Session {
        val normalized = normalizeServer(server)
        val json = request(normalized, "login", null, JSONObject()
            .put("email", email.trim()).put("password", password)
            .put("device_name", "Android ${Build.MANUFACTURER} ${Build.MODEL}".take(255)))
        val user = json.getJSONObject("user")
        return Session(normalized, user.getLong("id"), user.getString("name"), user.getString("email"), json.getString("token"))
    }

    suspend fun logout(session: Session) { request(session.server, "logout", session.token, JSONObject()) }

    suspend fun validateSession(session: Session) { request(session.server, "user", session.token) }

    suspend fun requestPasswordReset(server: String, email: String): String =
        request(normalizeServer(server), "forgot-password", null, JSONObject().put("email", email.trim())).getString("message")

    override suspend fun exchange(session: Session, batch: SyncBatch): SyncResponse {
        val body = JSONObject().put("cursor", batch.cursor)
            .put("tags", JSONArray(batch.tags.map { tagJson(it) }))
            .put("tasks", JSONArray(batch.tasks.map { taskJson(it) }))
        val json = request(session.server, "sync", session.token, body).getJSONObject("data")
        val tasks = json.getJSONArray("tasks")
        val tags = json.getJSONArray("tags")
        return SyncResponse(json.getLong("cursor"),
            (0 until tasks.length()).map { parseTask(tasks.getJSONObject(it)) },
            (0 until tags.length()).map { parseTag(tags.getJSONObject(it)) })
    }

    private suspend fun request(server: String, path: String, token: String?, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = URI("$server/api/v1/$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val error = runCatching { JSONObject(text) }.getOrNull()
                val validation = error?.optJSONObject("errors")?.let { errors ->
                    errors.keys().asSequence().firstOrNull()?.let { errors.optJSONArray(it)?.optString(0) }
                }
                val message = when (status) {
                    401 -> "Sua sessão expirou. Entre novamente para sincronizar."
                    429 -> "Muitas tentativas. Aguarde um pouco e tente novamente."
                    422 -> validation ?: "Confira os dados enviados e o relógio automático do aparelho."
                    in 300..399 -> "O servidor redirecionou a conexão. Confira o endereço e use a URL final."
                    else -> "O servidor não está disponível (HTTP $status)."
                }
                throw ApiException(status, message)
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        fun normalizeServer(value: String, allowLocalHttp: Boolean = BuildConfig.DEBUG): String {
            val uri = runCatching { URI(value.trim().removeSuffix("/").removeSuffix("/api/v1")) }
                .getOrElse { throw IllegalArgumentException("Informe um endereço de servidor válido.") }
            val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Informe o endereço completo do servidor.")
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) { "O endereço não deve incluir credenciais, parâmetros ou fragmentos." }
            val octets = host.split('.').map { it.toIntOrNull() }
            val ipv4 = octets.size == 4 && octets.all { it != null && it in 0..255 }
            val privateHost = host == "localhost" || host == "[::1]" || (ipv4 && (
                octets[0] == 127 || octets[0] == 10 || (octets[0] == 192 && octets[1] == 168) ||
                    (octets[0] == 172 && octets[1] in 16..31)))
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https" || (scheme == "http" && allowLocalHttp && privateHost)) {
                "Use HTTPS. No app de desenvolvimento, HTTP é permitido para emulador e rede local."
            }
            val port = if ((scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80)) -1 else uri.port
            return URI(scheme, null, host, port, uri.path.trimEnd('/'), null, null).toString()
        }

        internal fun tagJson(tag: Tag): JSONObject = JSONObject()
            .put("id", tag.id).put("name", tag.name).put("color_hex", tag.colorHex)
            .put("updated_at", tag.updatedAt).put("deleted_at", tag.deletedAt ?: JSONObject.NULL)

        internal fun taskJson(value: SyncTask): JSONObject = with(value.task) {
            JSONObject().put("id", id).put("title", title).put("description", description)
                .put("date", date.toString()).put("original_date", originalDate.toString())
                .put("deadline", deadline?.toString() ?: JSONObject.NULL).put("priority", priority.name)
                .put("recurrence", recurrence ?: JSONObject.NULL).put("series_id", seriesId ?: JSONObject.NULL)
                .put("completed", completed).put("created_at", createdAt).put("updated_at", updatedAt)
                .put("deleted_at", deletedAt ?: JSONObject.NULL).put("tag_ids", JSONArray(value.tagIds))
        }

        internal fun parseTag(json: JSONObject): Tag = Tag(
            json.getString("id"), json.getString("name"), json.getString("color_hex"),
            json.getLong("updated_at"), json.longOrNull("deleted_at"), needsSync = false
        )

        internal fun parseTask(json: JSONObject): SyncTask {
            val ids = json.getJSONArray("tag_ids")
            return SyncTask(Task(
                id = json.getString("id"), title = json.getString("title"), description = json.getString("description"),
                date = LocalDate.parse(json.getString("date")), originalDate = LocalDate.parse(json.getString("original_date")),
                deadline = json.stringOrNull("deadline")?.let(LocalDate::parse), priority = Priority.valueOf(json.getString("priority")),
                recurrence = json.stringOrNull("recurrence"), seriesId = json.stringOrNull("series_id"),
                completed = json.getBoolean("completed"), createdAt = json.getLong("created_at"),
                updatedAt = json.getLong("updated_at"), deletedAt = json.longOrNull("deleted_at"), needsSync = false
            ), (0 until ids.length()).map(ids::getString))
        }
    }
}

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
