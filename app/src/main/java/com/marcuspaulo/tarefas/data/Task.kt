package com.marcuspaulo.tarefas.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val date: LocalDate,
    val originalDate: LocalDate = date,
    val deadline: LocalDate? = null,
    val priority: Priority = Priority.MEDIUM,
    val recurrence: String? = null,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isOverdueRollover: Boolean
        get() = !completed && date != originalDate

    val isPastDeadline: Boolean
        get() = !completed && deadline != null && deadline.isBefore(LocalDate.now())
}
