package com.marcuspaulo.tarefas.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey val id: Int = 1,
    val cursor: Long = 0,
    val initialized: Boolean = false,
    val lastSyncedAt: Long? = null
)

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncState?

    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observe(): Flow<SyncState?>

    @Upsert
    suspend fun save(state: SyncState)
}
