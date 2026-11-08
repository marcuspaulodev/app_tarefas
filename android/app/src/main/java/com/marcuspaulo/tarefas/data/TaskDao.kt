package com.marcuspaulo.tarefas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class DateCount(val date: LocalDate, val count: Int)

/** Toda leitura exibida ao usuário ignora as tarefas com `deletedAt` preenchido. */
@Dao
interface TaskDao {

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE deletedAt IS NULL
          AND CASE WHEN completed = 0 THEN MAX(date, :today) ELSE date END = :date
        ORDER BY completed ASC, createdAt ASC
        """
    )
    fun getTasksForDate(date: LocalDate, today: LocalDate): Flow<List<TaskWithTags>>

    @Query(
        """
        SELECT shownOn AS date, COUNT(*) AS count
        FROM (SELECT MAX(date, :today) AS shownOn FROM tasks WHERE completed = 0 AND deletedAt IS NULL)
        WHERE shownOn BETWEEN :start AND :end
        GROUP BY shownOn
        """
    )
    fun getPendingCountsBetween(start: LocalDate, end: LocalDate, today: LocalDate): Flow<List<DateCount>>

    @Query("SELECT * FROM tasks WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: String): Task?

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id AND deletedAt IS NULL")
    suspend fun getTaskWithTagsById(id: String): TaskWithTags?

    /** Inclui as excluídas: uma ocorrência que o usuário apagou não deve ser recriada. */
    @Query("SELECT EXISTS(SELECT 1 FROM tasks WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    /** Pendentes até hoje, incluindo as atrasadas (usado pelo widget e pela checagem diária). */
    @Query(
        """
        SELECT * FROM tasks
        WHERE completed = 0 AND deletedAt IS NULL AND date <= :today
        ORDER BY date ASC, createdAt ASC
        """
    )
    fun getPendingTasksUpTo(today: LocalDate): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE completed = 0 AND deletedAt IS NULL AND deadline >= :today")
    suspend fun getPendingWithDeadlineFrom(today: LocalDate): List<Task>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE deletedAt IS NULL
          AND (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        ORDER BY date DESC
        """
    )
    fun searchTasks(query: String): Flow<List<TaskWithTags>>

    @Insert
    suspend fun insert(task: Task)

    @Update
    suspend fun update(task: Task)

    @Query("UPDATE tasks SET deletedAt = :now, updatedAt = :now, needsSync = 1 WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Insert
    suspend fun insertCrossRefs(refs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId")
    suspend fun clearTagsForTask(taskId: String)

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    suspend fun getAllTasksOnce(): List<Task>

    @Query(
        """
        SELECT ref.* FROM task_tag_cross_ref ref
        JOIN tasks ON tasks.id = ref.taskId
        WHERE tasks.deletedAt IS NULL
        """
    )
    suspend fun getAllCrossRefsOnce(): List<TaskTagCrossRef>

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Insert
    suspend fun insertAllTasks(tasks: List<Task>)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getIncludingDeleted(id: String): Task?

    @Transaction
    @Query("SELECT * FROM tasks WHERE needsSync = 1 ORDER BY updatedAt, id")
    suspend fun pendingSync(): List<TaskWithTags>

    @Query("SELECT COUNT(*) FROM tasks WHERE needsSync = 1")
    fun pendingSyncCount(): Flow<Int>

    @Query("SELECT * FROM tasks")
    suspend fun getIncludingDeleted(): List<Task>

    @Upsert
    suspend fun upsert(task: Task)
}
