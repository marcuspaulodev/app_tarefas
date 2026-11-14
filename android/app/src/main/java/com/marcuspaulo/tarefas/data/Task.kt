package com.marcuspaulo.tarefas.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey val id: String = Ids.newId(),
    val title: String,
    val description: String = "",
    val date: LocalDate,
    val originalDate: LocalDate = date,
    val deadline: LocalDate? = null,
    val priority: Priority = Priority.MEDIUM,
    val recurrence: String? = null,
    /** Liga as ocorrências de uma tarefa recorrente. Fica nula até a primeira conclusão. */
    val seriesId: String? = null,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Horário da última alteração, usado para decidir conflitos na sincronização. */
    val updatedAt: Long = createdAt,
    /** Exclusão lógica: a linha fica para a sincronização avisar os outros aparelhos. */
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "1") val needsSync: Boolean = true,
    /** Itens do checklist, na ordem em que aparecem. */
    @ColumnInfo(defaultValue = "[]") val checklist: List<ChecklistItem> = emptyList()
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
