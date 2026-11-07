package com.marcuspaulo.tarefas.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class TaskRepository(private val dao: TaskDao, private val tagDao: TagDao) {

    fun tasksForDate(date: LocalDate, today: LocalDate): Flow<List<TaskWithTags>> =
        dao.getTasksForDate(date, today)

    fun pendingCountsBetween(start: LocalDate, end: LocalDate, today: LocalDate): Flow<List<DateCount>> =
        dao.getPendingCountsBetween(start, end, today)

    fun searchTasks(query: String): Flow<List<TaskWithTags>> = dao.searchTasks(query)

    /** Pendentes até hoje, da prioridade mais alta pra mais baixa. */
    fun pendingTasksUpTo(today: LocalDate): Flow<List<Task>> =
        dao.getPendingTasksUpTo(today).map { tasks -> tasks.sortedByDescending { it.priority.ordinal } }

    suspend fun taskWithTagsById(id: Long): TaskWithTags? = dao.getTaskWithTagsById(id)

    val allTags: Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun addTask(
        title: String,
        description: String,
        date: LocalDate,
        deadline: LocalDate?,
        priority: Priority,
        recurrence: String?,
        tagIds: Set<Long>
    ): Long {
        val taskId = dao.insert(
            Task(
                title = title,
                description = description,
                date = date,
                originalDate = date,
                deadline = deadline,
                priority = priority,
                recurrence = recurrence
            )
        )
        if (tagIds.isNotEmpty()) {
            dao.insertCrossRefs(tagIds.map { TaskTagCrossRef(taskId = taskId, tagId = it) })
        }
        return taskId
    }

    suspend fun updateTask(task: Task, tagIds: Set<Long>) {
        dao.update(task)
        dao.clearTagsForTask(task.id)
        if (tagIds.isNotEmpty()) {
            dao.insertCrossRefs(tagIds.map { TaskTagCrossRef(taskId = task.id, tagId = it) })
        }
    }

    /**
     * Alterna a conclusão da tarefa. Se ela tem uma regra de recorrência e
     * está sendo marcada como concluída, gera a próxima ocorrência (mesmas
     * tags, sem o prazo da instância anterior) em vez de repetir a mesma
     * linha. Concluir registra o dia em que a tarefa estava aparecendo, então
     * uma atrasada concluída hoje fica em hoje, e não no dia em que atrasou.
     */
    suspend fun toggleCompleted(
        task: Task,
        tagIds: Set<Long>,
        today: LocalDate = LocalDate.now()
    ): Task {
        val newCompleted = !task.completed
        val updated = task.copy(
            completed = newCompleted,
            date = if (newCompleted) task.effectiveDate(today) else task.date
        )
        dao.update(updated)

        if (newCompleted) {
            val rule = RecurrenceRule.decode(task.recurrence)
            if (rule != null) {
                val nextDate = rule.nextOccurrenceAfter(updated.date)
                val nextTaskId = dao.insert(
                    Task(
                        title = task.title,
                        description = task.description,
                        date = nextDate,
                        originalDate = nextDate,
                        deadline = null,
                        priority = task.priority,
                        recurrence = task.recurrence
                    )
                )
                if (tagIds.isNotEmpty()) {
                    dao.insertCrossRefs(tagIds.map { TaskTagCrossRef(taskId = nextTaskId, tagId = it) })
                }
            }
        }
        return updated
    }

    suspend fun deleteTask(task: Task) {
        dao.delete(task)
    }

    suspend fun addTag(name: String, colorHex: String) {
        tagDao.insert(Tag(name = name.trim(), colorHex = colorHex))
    }

    suspend fun updateTag(tag: Tag) {
        tagDao.update(tag)
    }

    suspend fun deleteTag(tag: Tag) {
        tagDao.delete(tag)
    }
}
