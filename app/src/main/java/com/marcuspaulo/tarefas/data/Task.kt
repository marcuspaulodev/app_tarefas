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
        get() = !completed && originalDate.isBefore(LocalDate.now())

    val isPastDeadline: Boolean
        get() = !completed && deadline != null && deadline.isBefore(LocalDate.now())

    /**
     * Dia em que a tarefa aparece: uma pendente cuja data já passou fica em "hoje".
     * É calculado na leitura, sem gravar no banco, para o simples passar dos dias
     * não contar como edição quando houver sincronização entre aparelhos.
     * Deve bater com o CASE das consultas em [TaskDao].
     */
    fun effectiveDate(today: LocalDate = LocalDate.now()): LocalDate =
        if (!completed && date.isBefore(today)) today else date

    /** Aplica a data escolhida na edição. Manter o dia exibido preserva o "atrasada desde". */
    fun rescheduledTo(newDate: LocalDate, today: LocalDate = LocalDate.now()): Task =
        if (newDate == effectiveDate(today)) this else copy(date = newDate, originalDate = newDate)
}
