package com.marcuspaulo.tarefas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name")
    fun getAllTags(): Flow<List<Tag>>

    @Insert
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsOnce(): List<Tag>

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Insert
    suspend fun insertAllTags(tags: List<Tag>)
}
