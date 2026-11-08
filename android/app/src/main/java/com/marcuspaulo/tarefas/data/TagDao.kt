package com.marcuspaulo.tarefas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
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

    @Query("UPDATE tasks SET updatedAt = MAX(updatedAt + 1, :now), needsSync = 1 WHERE id IN (SELECT taskId FROM task_tag_cross_ref WHERE tagId = :tagId)")
    suspend fun touchTasksWithTag(tagId: String, now: Long)

    @Query("DELETE FROM task_tag_cross_ref WHERE tagId = :tagId")
    suspend fun clearTagFromTasks(tagId: String)

    @Query("UPDATE tags SET deletedAt = :now, updatedAt = :now, needsSync = 1 WHERE id = :tagId")
    suspend fun markDeleted(tagId: String, now: Long)

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL")
    suspend fun getAllTagsOnce(): List<Tag>

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Insert
    suspend fun insertAllTags(tags: List<Tag>)

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getIncludingDeleted(id: String): Tag?

    @Query("SELECT * FROM tags")
    suspend fun getIncludingDeleted(): List<Tag>

    @Query("SELECT * FROM tags WHERE needsSync = 1 ORDER BY updatedAt, id LIMIT 100")
    suspend fun pendingSync(): List<Tag>

    @Query("SELECT COUNT(*) FROM tags WHERE needsSync = 1")
    fun pendingSyncCount(): Flow<Int>

    @Upsert
    suspend fun upsert(tag: Tag)

    @Query("INSERT OR IGNORE INTO task_tag_cross_ref (taskId, tagId) SELECT taskId, :newId FROM task_tag_cross_ref WHERE tagId = :oldId")
    suspend fun copyReferences(oldId: String, newId: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun removeImportedDefault(id: String)
}
