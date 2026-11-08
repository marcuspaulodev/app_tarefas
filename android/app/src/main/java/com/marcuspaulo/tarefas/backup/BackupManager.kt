package com.marcuspaulo.tarefas.backup

import androidx.room.withTransaction
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.Ids
import com.marcuspaulo.tarefas.data.Priority
import com.marcuspaulo.tarefas.data.Tag
import com.marcuspaulo.tarefas.data.Task
import com.marcuspaulo.tarefas.data.TaskTagCrossRef
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Formato do arquivo:
 * - versão 1: IDs numéricos (app até o banco versão 4);
 * - versão 2: IDs UUID, mais `seriesId` e `updatedAt`.
 * Arquivos da versão 1 continuam podendo ser restaurados: os IDs ganham UUIDs novos.
 */
private const val BACKUP_VERSION = 2

class BackupManager(
    private val database: AppDatabase,
    private val mergeIntoAccount: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis
) {

    suspend fun exportToJson(): String = database.withTransaction {
        val taskDao = database.taskDao()
        val tagDao = database.tagDao()

        val tagsJson = JSONArray()
        tagDao.getAllTagsOnce().forEach { tag ->
            tagsJson.put(
                JSONObject()
                    .put("id", tag.id)
                    .put("name", tag.name)
                    .put("colorHex", tag.colorHex)
                    .put("updatedAt", tag.updatedAt)
            )
        }

        val tasksJson = JSONArray()
        taskDao.getAllTasksOnce().forEach { task ->
            tasksJson.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("description", task.description)
                    .put("date", task.date.toString())
                    .put("originalDate", task.originalDate.toString())
                    .put("deadline", task.deadline?.toString() ?: JSONObject.NULL)
                    .put("priority", task.priority.name)
                    .put("recurrence", task.recurrence ?: JSONObject.NULL)
                    .put("seriesId", task.seriesId ?: JSONObject.NULL)
                    .put("completed", task.completed)
                    .put("createdAt", task.createdAt)
                    .put("updatedAt", task.updatedAt)
            )
        }

        val crossRefsJson = JSONArray()
        taskDao.getAllCrossRefsOnce().forEach { ref ->
            crossRefsJson.put(
                JSONObject()
                    .put("taskId", ref.taskId)
                    .put("tagId", ref.tagId)
            )
        }

        JSONObject()
            .put("version", BACKUP_VERSION)
            .put("portable", mergeIntoAccount)
            .put("tags", tagsJson)
            .put("tasks", tasksJson)
            .put("taskTags", crossRefsJson)
            .toString(2)
    }

    suspend fun importFromJson(json: String) {
        val root = JSONObject(json)
        val legacy = root.optInt("version", 1) < 2
        val now = clock()
        val tagsArray = root.getJSONArray("tags")
        val tasksArray = root.getJSONArray("tasks")
        val crossRefsArray = root.getJSONArray("taskTags")

        // Na versão 1, tarefas e tags podiam ter o mesmo número, por isso um mapa para cada.
        val tagIds = HashMap<String, String>()
        val taskIds = HashMap<String, String>()
        fun newIdFor(map: HashMap<String, String>, raw: String) =
            if (legacy) map.getOrPut(raw) { Ids.newId(now) } else raw

        val tags = (0 until tagsArray.length()).map { i ->
            val obj = tagsArray.getJSONObject(i)
            Tag(
                id = newIdFor(tagIds, obj.getString("id")),
                name = obj.getString("name"),
                colorHex = obj.getString("colorHex"),
                updatedAt = obj.optLong("updatedAt", now)
            )
        }

        val tasks = (0 until tasksArray.length()).map { i ->
            val obj = tasksArray.getJSONObject(i)
            Task(
                id = newIdFor(taskIds, obj.getString("id")),
                title = obj.getString("title"),
                description = obj.optString("description"),
                date = LocalDate.parse(obj.getString("date")),
                originalDate = LocalDate.parse(obj.getString("originalDate")),
                deadline = obj.stringOrNull("deadline")?.let { LocalDate.parse(it) },
                priority = obj.stringOrNull("priority")?.let { Priority.valueOf(it) } ?: Priority.MEDIUM,
                recurrence = obj.stringOrNull("recurrence"),
                seriesId = obj.stringOrNull("seriesId"),
                completed = obj.getBoolean("completed"),
                createdAt = obj.getLong("createdAt"),
                updatedAt = obj.optLong("updatedAt", now)
            )
        }

        val crossRefs = (0 until crossRefsArray.length()).mapNotNull { i ->
            val obj = crossRefsArray.getJSONObject(i)
            val rawTask = obj.getString("taskId")
            val rawTag = obj.getString("tagId")
            if (legacy) {
                val taskId = taskIds[rawTask] ?: return@mapNotNull null
                val tagId = tagIds[rawTag] ?: return@mapNotNull null
                TaskTagCrossRef(taskId = taskId, tagId = tagId)
            } else {
                TaskTagCrossRef(taskId = rawTask, tagId = rawTag)
            }
        }

        val portable = mergeIntoAccount || root.optBoolean("portable")
        val importedTagIds = tags.associate { it.id to if (portable) Ids.newId(now) else it.id }
        val importedTaskIds = tasks.associate { it.id to if (portable) Ids.newId(now) else it.id }.toMutableMap()
        val seriesIds = mutableMapOf<String, String>()
        val usedIds = mutableSetOf<String>()
        val importedTasks = tasks.map { task ->
            if (!portable) task else {
                val series = task.seriesId?.let { seriesIds.getOrPut(it) { importedTaskIds[it] ?: Ids.newId(now) } }
                var id = if (series != null && task.id != task.seriesId) Ids.occurrenceId(series, task.originalDate)
                    else importedTaskIds.getValue(task.id)
                if (!usedIds.add(id)) { id = Ids.newId(now); usedIds.add(id) }
                importedTaskIds[task.id] = id
                task.copy(id = id, seriesId = series, updatedAt = maxOf(now, task.createdAt), needsSync = true)
            }
        }
        val importedTags = tags.map { if (portable) it.copy(id = importedTagIds.getValue(it.id), updatedAt = now, needsSync = true) else it }
        val importedRefs = crossRefs.map { TaskTagCrossRef(importedTaskIds.getValue(it.taskId), importedTagIds.getValue(it.tagId)) }

        database.withTransaction {
            val taskDao = database.taskDao()
            val tagDao = database.tagDao()
            if (!mergeIntoAccount) {
                taskDao.deleteAllTasks()
                tagDao.deleteAllTags()
            }
            if (importedTags.isNotEmpty()) tagDao.insertAllTags(importedTags)
            if (importedTasks.isNotEmpty()) taskDao.insertAllTasks(importedTasks)
            if (importedRefs.isNotEmpty()) taskDao.insertCrossRefs(importedRefs)
        }
    }
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
