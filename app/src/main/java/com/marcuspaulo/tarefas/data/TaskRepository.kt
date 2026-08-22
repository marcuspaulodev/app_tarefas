package com.marcuspaulo.tarefas.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TaskRepository(private val dao: TaskDao, private val tagDao: TagDao) {

    fun tasksForDate(date: LocalDate): Flow<List<TaskWithTags>> = dao.getTasksForDate(date)

    fun pendingCountsBetween(start: LocalDate, end: LocalDate): Flow<List<DateCount>> =
        dao.getPendingCountsBetween(start, end)

    fun searchTasks(query: String): Flow<List<TaskWithTags>> = dao.searchTasks(query)

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
     * linha — o avanço de dias de tarefas não concluídas continua sendo
     * responsabilidade do rollover.
     */
    suspend fun toggleCompleted(task: Task, tagIds: Set<Long>): Task {
        val newCompleted = !task.completed
        val updated = task.copy(completed = newCompleted)
        dao.update(updated)

        if (newCompleted) {
            val rule = RecurrenceRule.decode(task.recurrence)
            if (rule != null) {
                val nextDate = rule.nextOccurrenceAfter(task.date)
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

    /**
     * Empurra para "hoje" toda tarefa não concluída cuja data já passou,
     * preservando originalDate para exibir "atrasada desde".
     */
    suspend fun rolloverOverdueTasks(today: LocalDate = LocalDate.now()) {
        val overdue = dao.getIncompleteTasksBefore(today)
        if (overdue.isNotEmpty()) {
            dao.updateTasks(overdue.map { it.copy(date = today) })
        }
    }
}
