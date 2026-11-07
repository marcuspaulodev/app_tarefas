package com.marcuspaulo.tarefas.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey val id: String = Ids.newId(),
    val name: String,
    val colorHex: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
