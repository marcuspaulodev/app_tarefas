package com.marcuspaulo.tarefas.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Toda escrita passa por aqui e atualiza o `updatedAt` do que mudou. */
class TaskRepository(
    private val dao: TaskDao,
    private val tagDao: TagDao,
    private val clock: () -> Long = System::currentTimeMillis
) {

    fun tasksForDate(date: LocalDate, today: LocalDate): Flow<List<TaskWithTags>> =
        dao.getTasksForDate(date, today)

    fun pendingCountsBetween(start: LocalDate, end: LocalDate, today: LocalDate): Flow<List<DateCount>> =
        dao.getPendingCountsBetween(start, end, today)

    fun searchTasks(query: String): Flow<List<TaskWithTags>> = dao.searchTasks(query)

    /** Pendentes até hoje, da prioridade mais alta pra mais baixa. */
    fun pendingTasksUpTo(today: LocalDate): Flow<List<Task>> =
        dao.getPendingTasksUpTo(today).map { tasks -> tasks.sortedByDescending { it.priority.ordinal } }

    suspend fun pendingWithDeadlineFrom(today: LocalDate): List<Task> = dao.getPendingWithDeadlineFrom(today)

    suspend fun taskWithTagsById(id: String): TaskWithTags? = dao.getTaskWithTagsById(id)

    val allTags: Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun addTask(
        title: String,
        description: String,
        date: LocalDate,
        deadline: LocalDate?,
        priority: Priority,
        recurrence: String?,
        tagIds: Set<String>
    ): Task {
        val now = clock()
        val task = Task(
            id = Ids.newId(now),
            title = title,
            description = description,
            date = date,
            originalDate = date,
            deadline = deadline,
            priority = priority,
            recurrence = recurrence,
            createdAt = now
        )
        dao.insert(task)
        setTags(task.id, tagIds)
        return task
    }

    suspend fun updateTask(task: Task, tagIds: Set<String>) {
        dao.update(task.copy(updatedAt = clock()))
        dao.clearTagsForTask(task.id)
        setTags(task.id, tagIds)
    }

    /**
     * Alterna a conclusão da tarefa. Concluir registra o dia em que a tarefa estava
     * aparecendo, então uma atrasada concluída hoje fica em hoje, e não no dia em que atrasou.
     *
     * Se ela é recorrente, concluir também cria a próxima ocorrência, com as mesmas tags e
     * sem o prazo da anterior. O ID dessa ocorrência vem da série e da data, então concluir,
     * desmarcar e concluir de novo não a duplica, e uma ocorrência excluída não volta.
     */
    suspend fun toggleCompleted(
        task: Task,
        tagIds: Set<String>,
        today: LocalDate = LocalDate.now()
    ): Task {
        val now = clock()
        val newCompleted = !task.completed
        val rule = if (newCompleted) RecurrenceRule.decode(task.recurrence) else null
        val updated = task.copy(
            completed = newCompleted,
            date = if (newCompleted) task.effectiveDate(today) else task.date,
            seriesId = if (rule != null) task.seriesId ?: task.id else task.seriesId,
            updatedAt = now
        )
        dao.update(updated)

        if (rule != null) {
            val seriesId = updated.seriesId!!
            val nextDate = rule.nextOccurrenceAfter(updated.date)
            val nextId = Ids.occurrenceId(seriesId, nextDate)
            if (!dao.exists(nextId)) {
                dao.insert(
                    Task(
                        id = nextId,
                        title = task.title,
                        description = task.description,
                        date = nextDate,
                        originalDate = nextDate,
                        deadline = null,
                        priority = task.priority,
                        recurrence = task.recurrence,
                        seriesId = seriesId,
                        createdAt = now
                    )
                )
                setTags(nextId, tagIds)
            }
        }
        return updated
    }

    suspend fun deleteTask(task: Task) {
        dao.softDelete(task.id, clock())
    }

    suspend fun addTag(name: String, colorHex: String) {
        val now = clock()
        tagDao.insert(Tag(id = Ids.newId(now), name = name.trim(), colorHex = colorHex, updatedAt = now))
    }

    suspend fun updateTag(tag: Tag) {
        tagDao.update(tag.copy(updatedAt = clock()))
    }

    suspend fun deleteTag(tag: Tag) {
        tagDao.softDelete(tag.id, clock())
    }

    private suspend fun setTags(taskId: String, tagIds: Set<String>) {
        if (tagIds.isNotEmpty()) {
            dao.insertCrossRefs(tagIds.map { TaskTagCrossRef(taskId = taskId, tagId = it) })
        }
    }
}
