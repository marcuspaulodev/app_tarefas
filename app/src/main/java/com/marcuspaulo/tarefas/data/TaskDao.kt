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
    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY completed ASC, createdAt ASC")
    fun getTasksForDate(date: LocalDate): Flow<List<TaskWithTags>>

    @Query("SELECT date, COUNT(*) as count FROM tasks WHERE date BETWEEN :start AND :end AND completed = 0 GROUP BY date")
    fun getPendingCountsBetween(start: LocalDate, end: LocalDate): Flow<List<DateCount>>

    @Query("SELECT * FROM tasks WHERE completed = 0 AND date < :today")
    suspend fun getIncompleteTasksBefore(today: LocalDate): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Transaction
    @Query("SELECT * FROM tasks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchTasks(query: String): Flow<List<TaskWithTags>>

    @Update
    suspend fun updateTasks(tasks: List<Task>)

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
