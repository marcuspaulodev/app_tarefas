package com.marcuspaulo.tarefas.sync

import androidx.room.withTransaction
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.Ids
import com.marcuspaulo.tarefas.data.RecurrenceRule
import com.marcuspaulo.tarefas.data.SyncState
import com.marcuspaulo.tarefas.data.Tag
import com.marcuspaulo.tarefas.data.TaskTagCrossRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Um único ciclo por conta; a rede nunca mantém uma transação do Room aberta. */
class SyncEngine(
    private val database: AppDatabase,
    private val api: SyncApi,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val tasks = database.taskDao()
    private val tags = database.tagDao()
    private val state = database.syncStateDao()

    /** false pede outra execução para terminar uma fila grande. */
    suspend fun synchronize(session: Session, stillActive: () -> Boolean = { true }): Boolean =
        locks.getOrPut(session.key) { Mutex() }.withLock {
            repeat(20) {
                if (!stillActive()) return@withLock true
                val batch = database.withTransaction { nextBatch() }
                val response = api.exchange(session, batch)
                if (!stillActive()) return@withLock true
                apply(batch, response)
                val pending = database.withTransaction { tags.pendingSync().isNotEmpty() || tasks.pendingSync().isNotEmpty() }
                if (!pending) return@withLock true
            }
            false
        }

    private suspend fun nextBatch(): SyncBatch {
        val current = state.get() ?: SyncState()
        // Primeiro recebe o catálogo de tags, para conciliar as três tags padrão antes do envio.
        if (!current.initialized) return SyncBatch(0)
        val pendingTags = tags.pendingSync()
        if (pendingTags.isNotEmpty()) return SyncBatch(current.cursor, tags = pendingTags)
        val pending = tasks.pendingSync().map { SyncTask(it.task, it.tags.map { tag -> tag.id }) }
        val byId = pending.associateBy { it.task.id }
        val selected = linkedMapOf<String, SyncTask>()
        for (item in pending) {
            if (item.task.id in selected) continue
            val task = item.task
            val rule = if (task.completed && task.deletedAt == null) RecurrenceRule.decode(task.recurrence) else null
            val next = if (rule != null && task.seriesId != null)
                byId[Ids.occurrenceId(task.seriesId, rule.nextOccurrenceAfter(task.date))] else null
            val group = listOfNotNull(item, next).filterNot { it.task.id in selected }.distinctBy { it.task.id }
            if (selected.size + group.size > 100) break
            group.forEach { selected[it.task.id] = it }
        }
        return SyncBatch(current.cursor, tasks = selected.values.toList())
    }

    internal suspend fun apply(batch: SyncBatch, response: SyncResponse) = database.withTransaction {
        require(response.cursor >= batch.cursor) { "O servidor devolveu um cursor inválido." }
        if (!response.tasks.map { it.task.id }.containsAll(batch.tasks.map { it.task.id }) ||
            !response.tags.map { it.id }.containsAll(batch.tags.map { it.id })) {
            throw IOException("O servidor não confirmou todos os registros enviados.")
        }
        val currentState = state.get() ?: SyncState()
        if (!currentState.initialized) reconcileDefaults(response.tags)
        val sentTasks = batch.tasks.associateBy { it.task.id }
        val sentTags = batch.tags.associateBy { it.id }
        for (remote in response.tags) {
            val local = tags.getIncludingDeleted(remote.id)
            if (local == null || !local.needsSync || local == sentTags[remote.id]) {
                tags.upsert(remote.copy(needsSync = false))
            }
        }
        for (remote in response.tasks) {
            val local = tasks.getIncludingDeleted(remote.task.id)
            if (local == null || !local.needsSync || local == sentTasks[remote.task.id]?.task) {
                tasks.upsert(remote.task.copy(needsSync = false))
                tasks.clearTagsForTask(remote.task.id)
                val refs = remote.tagIds.mapNotNull { id ->
                    val tag = tags.getIncludingDeleted(id) ?: throw IOException("A resposta contém uma tag desconhecida.")
                    if (tag.deletedAt == null) TaskTagCrossRef(remote.task.id, id) else null
                }
                if (refs.isNotEmpty()) tasks.insertCrossRefs(refs)
            }
        }
        // Uma tag excluída não volta por meio de uma tarefa editada durante a requisição.
        tags.getIncludingDeleted().filter { it.deletedAt != null }.forEach { tags.clearTagFromTasks(it.id) }
        state.save(currentState.copy(cursor = response.cursor, initialized = true, lastSyncedAt = clock()))
    }

    private suspend fun reconcileDefaults(remote: List<Tag>) {
        val defaults = mapOf("Trabalho" to "#3D5AFE", "Casa" to "#00C853", "Faculdade" to "#FF6D00")
        for (local in tags.getIncludingDeleted()) {
            if (defaults[local.name] != local.colorHex.uppercase()) continue
            val match = remote.firstOrNull { it.name == local.name && it.colorHex.equals(local.colorHex, true) && it.deletedAt == null }
                ?: continue
            if (match.id == local.id) continue
            val replacement = if (local.deletedAt == null) match.copy(needsSync = false) else {
                val timestamp = maxOf(clock(), match.updatedAt + 1, local.updatedAt)
                match.copy(deletedAt = timestamp, updatedAt = timestamp, needsSync = true)
            }
            tags.upsert(replacement)
            tags.copyReferences(local.id, match.id)
            tags.removeImportedDefault(local.id)
        }
    }

    companion object { private val locks = ConcurrentHashMap<String, Mutex>() }
}
