package com.marcuspaulo.tarefas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name")
    fun getAllTags(): Flow<List<Tag>>

    @Insert
    suspend fun insert(tag: Tag)

    @Update
    suspend fun update(tag: Tag)

    /**
     * Exclusão lógica da tag. Ela sai das tarefas que a usavam, e essas tarefas
     * ganham um `updatedAt` novo, já que as tags fazem parte da tarefa.
     */
    @Transaction
    suspend fun softDelete(tagId: String, now: Long) {
        touchTasksWithTag(tagId, now)
        clearTagFromTasks(tagId)
        markDeleted(tagId, now)
    }

    @Query("UPDATE tasks SET updatedAt = :now WHERE id IN (SELECT taskId FROM task_tag_cross_ref WHERE tagId = :tagId)")
    suspend fun touchTasksWithTag(tagId: String, now: Long)

    @Query("DELETE FROM task_tag_cross_ref WHERE tagId = :tagId")
    suspend fun clearTagFromTasks(tagId: String)

    @Query("UPDATE tags SET deletedAt = :now, updatedAt = :now WHERE id = :tagId")
    suspend fun markDeleted(tagId: String, now: Long)

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL")
    suspend fun getAllTagsOnce(): List<Tag>

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Insert
    suspend fun insertAllTags(tags: List<Tag>)
}
