package com.marcuspaulo.tarefas.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Tags de cada tarefa. Não tem horário próprio: as tags fazem parte da tarefa, então
 * mudá-las atualiza o `updatedAt` da tarefa.
 */
@Entity(
    tableName = "task_tag_cross_ref",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId"), Index("tagId")]
)
data class TaskTagCrossRef(
    val taskId: String,
    val tagId: String
)
