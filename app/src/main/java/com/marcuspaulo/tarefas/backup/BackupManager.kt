package com.marcuspaulo.tarefas.backup

import androidx.room.withTransaction
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.Priority
import com.marcuspaulo.tarefas.data.Tag
import com.marcuspaulo.tarefas.data.Task
import com.marcuspaulo.tarefas.data.TaskTagCrossRef
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class BackupManager(private val database: AppDatabase) {

    suspend fun exportToJson(): String {
        val taskDao = database.taskDao()
        val tagDao = database.tagDao()

        val tagsJson = JSONArray()
        tagDao.getAllTagsOnce().forEach { tag ->
            tagsJson.put(
                JSONObject()
                    .put("id", tag.id)
                    .put("name", tag.name)
                    .put("colorHex", tag.colorHex)
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
                    .put("completed", task.completed)
                    .put("createdAt", task.createdAt)
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

        return JSONObject()
            .put("version", 1)
            .put("tags", tagsJson)
            .put("tasks", tasksJson)
            .put("taskTags", crossRefsJson)
            .toString(2)
    }

    suspend fun importFromJson(json: String) {
        val root = JSONObject(json)
        val tagsArray = root.getJSONArray("tags")
        val tasksArray = root.getJSONArray("tasks")
        val crossRefsArray = root.getJSONArray("taskTags")

        val tags = (0 until tagsArray.length()).map { i ->
            val obj = tagsArray.getJSONObject(i)
            Tag(id = obj.getLong("id"), name = obj.getString("name"), colorHex = obj.getString("colorHex"))
        }

        val tasks = (0 until tasksArray.length()).map { i ->
            val obj = tasksArray.getJSONObject(i)
            Task(
                id = obj.getLong("id"),
                title = obj.getString("title"),
                description = obj.optString("description"),
                date = LocalDate.parse(obj.getString("date")),
                originalDate = LocalDate.parse(obj.getString("originalDate")),
                deadline = obj.stringOrNull("deadline")?.let { LocalDate.parse(it) },
                priority = obj.stringOrNull("priority")?.let { Priority.valueOf(it) } ?: Priority.MEDIUM,
                recurrence = obj.stringOrNull("recurrence"),
                completed = obj.getBoolean("completed"),
                createdAt = obj.getLong("createdAt")
            )
        }

        val crossRefs = (0 until crossRefsArray.length()).map { i ->
            val obj = crossRefsArray.getJSONObject(i)
            TaskTagCrossRef(taskId = obj.getLong("taskId"), tagId = obj.getLong("tagId"))
        }

        database.withTransaction {
            val taskDao = database.taskDao()
            val tagDao = database.tagDao()
            taskDao.deleteAllTasks()
            tagDao.deleteAllTags()
            if (tags.isNotEmpty()) tagDao.insertAllTags(tags)
            if (tasks.isNotEmpty()) taskDao.insertAllTasks(tasks)
            if (crossRefs.isNotEmpty()) taskDao.insertCrossRefs(crossRefs)
        }
    }
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
