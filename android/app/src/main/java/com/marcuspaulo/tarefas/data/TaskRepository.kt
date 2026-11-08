package com.marcuspaulo.tarefas.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Cada edição e suas ligações são gravadas juntas, antes de poderem ser sincronizadas. */
class TaskRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.taskDao()
    private val tagDao = database.tagDao()

    fun tasksForDate(date: LocalDate, today: LocalDate): Flow<List<TaskWithTags>> = dao.getTasksForDate(date, today)
    fun pendingCountsBetween(start: LocalDate, end: LocalDate, today: LocalDate): Flow<List<DateCount>> =
        dao.getPendingCountsBetween(start, end, today)
    fun searchTasks(query: String): Flow<List<TaskWithTags>> = dao.searchTasks(query)
    fun pendingTasksUpTo(today: LocalDate): Flow<List<Task>> =
        dao.getPendingTasksUpTo(today).map { tasks -> tasks.sortedByDescending { it.priority.ordinal } }
    suspend fun pendingWithDeadlineFrom(today: LocalDate): List<Task> = dao.getPendingWithDeadlineFrom(today)
    suspend fun taskWithTagsById(id: String): TaskWithTags? = dao.getTaskWithTagsById(id)
    val allTags: Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun addTask(
        title: String, description: String, date: LocalDate, deadline: LocalDate?,
        priority: Priority, recurrence: String?, tagIds: Set<String>
    ): Task = database.withTransaction {
        val now = clock()
        val task = Task(
            id = Ids.newId(now), title = title, description = description, date = date,
            deadline = deadline, priority = priority, recurrence = recurrence, createdAt = now
        )
        dao.insert(task)
        setTags(task.id, tagIds)
        task
    }

    suspend fun updateTask(
        task: Task,
        tagIds: Set<String>,
        selectedDate: LocalDate? = null,
        today: LocalDate = LocalDate.now()
    ): Task? = database.withTransaction {
        val current = dao.getById(task.id) ?: return@withTransaction null
        val scheduled = if (selectedDate == null) current else current.rescheduledTo(selectedDate, today)
        val updated = scheduled.copy(
            title = task.title, description = task.description, deadline = task.deadline,
            priority = task.priority, recurrence = task.recurrence,
            updatedAt = nextTimestamp(current.updatedAt), needsSync = true
        )
        dao.update(updated)
        dao.clearTagsForTask(task.id)
        setTags(task.id, tagIds)
        updated
    }

    /** Conclusão e próxima ocorrência entram na mesma transação e no mesmo lote de envio. */
    suspend fun toggleCompleted(task: Task, tagIds: Set<String>, today: LocalDate = LocalDate.now()): Task =
        database.withTransaction {
            val current = dao.getById(task.id) ?: return@withTransaction task
            val now = nextTimestamp(current.updatedAt)
            val completing = !current.completed
            val rule = if (completing) RecurrenceRule.decode(current.recurrence) else null
            val currentTags = dao.getTaskWithTagsById(task.id)!!.tags.map { it.id }.toSet()
            val updated = current.copy(
                completed = completing,
                date = if (completing) current.effectiveDate(today) else current.date,
                seriesId = if (rule != null) current.seriesId ?: current.id else current.seriesId,
                updatedAt = now, needsSync = true
            )
            dao.update(updated)
            if (rule != null) {
                val seriesId = updated.seriesId!!
                val nextDate = rule.nextOccurrenceAfter(updated.date)
                val nextId = Ids.occurrenceId(seriesId, nextDate)
                if (!dao.exists(nextId)) {
                    dao.insert(Task(
                        id = nextId, title = current.title, description = current.description,
                        date = nextDate, priority = current.priority, recurrence = current.recurrence,
                        seriesId = seriesId, createdAt = now
                    ))
                    setTags(nextId, currentTags)
                }
            }
            updated
        }

    suspend fun deleteTask(task: Task) = database.withTransaction {
        val current = dao.getById(task.id) ?: return@withTransaction
        dao.softDelete(task.id, nextTimestamp(current.updatedAt))
    }

    suspend fun addTag(name: String, colorHex: String) = database.withTransaction {
        val now = clock()
        tagDao.insert(Tag(id = Ids.newId(now), name = name.trim(), colorHex = colorHex, updatedAt = now))
    }

    suspend fun updateTag(tag: Tag) = database.withTransaction {
        val current = tagDao.getIncludingDeleted(tag.id) ?: return@withTransaction
        if (current.deletedAt == null) {
            tagDao.update(tag.copy(updatedAt = nextTimestamp(current.updatedAt), needsSync = true))
        }
    }

    suspend fun deleteTag(tag: Tag) = database.withTransaction {
        val current = tagDao.getIncludingDeleted(tag.id) ?: return@withTransaction
        if (current.deletedAt == null) tagDao.softDelete(tag.id, nextTimestamp(current.updatedAt))
    }

    private fun nextTimestamp(previous: Long): Long = maxOf(clock(), previous + 1)

    private suspend fun setTags(taskId: String, tagIds: Set<String>) {
        val activeIds = tagDao.getAllTagsOnce().map { it.id }.toSet()
        val refs = tagIds.filter { it in activeIds }.map { TaskTagCrossRef(taskId, it) }
        if (refs.isNotEmpty()) dao.insertCrossRefs(refs)
    }
}
