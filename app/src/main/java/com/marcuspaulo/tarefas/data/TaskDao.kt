package com.marcuspaulo.tarefas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class DateCount(val date: LocalDate, val count: Int)

@Dao
interface TaskDao {

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE CASE WHEN completed = 0 THEN MAX(date, :today) ELSE date END = :date
        ORDER BY completed ASC, createdAt ASC
        """
    )
    fun getTasksForDate(date: LocalDate, today: LocalDate): Flow<List<TaskWithTags>>

    @Query(
        """
        SELECT shownOn AS date, COUNT(*) AS count
        FROM (SELECT MAX(date, :today) AS shownOn FROM tasks WHERE completed = 0)
        WHERE shownOn BETWEEN :start AND :end
        GROUP BY shownOn
        """
    )
    fun getPendingCountsBetween(start: LocalDate, end: LocalDate, today: LocalDate): Flow<List<DateCount>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskWithTagsById(id: Long): TaskWithTags?

    /** Pendentes até hoje, incluindo as atrasadas (usado pelo widget e pela checagem diária). */
    @Query("SELECT * FROM tasks WHERE completed = 0 AND date <= :today ORDER BY date ASC, createdAt ASC")
    fun getPendingTasksUpTo(today: LocalDate): Flow<List<Task>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchTasks(query: String): Flow<List<TaskWithTags>>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Insert
    suspend fun insertCrossRefs(refs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId")
    suspend fun clearTagsForTask(taskId: Long)

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<Task>

    @Query("SELECT * FROM task_tag_cross_ref")
    suspend fun getAllCrossRefsOnce(): List<TaskTagCrossRef>

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Insert
    suspend fun insertAllTasks(tasks: List<Task>)
}
