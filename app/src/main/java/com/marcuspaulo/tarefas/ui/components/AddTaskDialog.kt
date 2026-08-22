package com.marcuspaulo.tarefas.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marcuspaulo.tarefas.data.Priority
import com.marcuspaulo.tarefas.data.RecurrenceRule
import com.marcuspaulo.tarefas.data.Tag
import com.marcuspaulo.tarefas.data.Task
import com.marcuspaulo.tarefas.ui.theme.priorityLabel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("dd 'de' MMMM", Locale("pt", "BR"))
private val DEADLINE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private enum class RecurrenceType { NONE, DAILY, WEEKLY }

private val WEEKDAY_OPTIONS = listOf(
    DayOfWeek.SUNDAY to "dom",
    DayOfWeek.MONDAY to "seg",
    DayOfWeek.TUESDAY to "ter",
    DayOfWeek.WEDNESDAY to "qua",
    DayOfWeek.THURSDAY to "qui",
    DayOfWeek.FRIDAY to "sex",
    DayOfWeek.SATURDAY to "sáb"
)

data class TaskFormResult(
    val title: String,
    val description: String,
    val date: LocalDate,
    val deadline: LocalDate?,
    val priority: Priority,
    val recurrence: String?,
    val tagIds: Set<Long>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    date: LocalDate,
    availableTags: List<Tag>,
    initial: Task? = null,
    initialTagIds: Set<Long> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (TaskFormResult) -> Unit
) {
    val isEditing = initial != null
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var taskDate by remember { mutableStateOf(initial?.date ?: date) }
    var deadline by remember { mutableStateOf(initial?.deadline) }
    var priority by remember { mutableStateOf(initial?.priority ?: Priority.MEDIUM) }
    var showDayPicker by remember { mutableStateOf(false) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var selectedTagIds by remember { mutableStateOf(initialTagIds) }

    val initialRule = remember { RecurrenceRule.decode(initial?.recurrence) }
    var recurrenceType by remember {
        mutableStateOf(
            when (initialRule) {
                null -> RecurrenceType.NONE
                is RecurrenceRule.Daily -> RecurrenceType.DAILY
                is RecurrenceRule.Weekly -> RecurrenceType.WEEKLY
            }
        )
    }
    var weeklyDays by remember {
        mutableStateOf((initialRule as? RecurrenceRule.Weekly)?.days ?: emptySet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditing) "Editar tarefa · ${taskDate.format(DATE_FMT)}"
                else "Nova tarefa · ${taskDate.format(DATE_FMT)}"
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { showDayPicker = true },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                            label = { Text("Dia: ${taskDate.format(DEADLINE_FMT)}") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AssistChip(
                        onClick = { showDeadlinePicker = true },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                        label = {
                            Text(
                                if (deadline != null) "Prazo: ${deadline!!.format(DEADLINE_FMT)}"
                                else "Definir prazo (opcional)"
                            )
                        }
                    )
                    if (deadline != null) {
                        IconButton(onClick = { deadline = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Remover prazo")
                        }
                    }
                }

                Text(
                    text = "Prioridade",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Priority.entries.forEach { option ->
                        FilterChip(
                            selected = priority == option,
                            onClick = { priority = option },
                            label = { Text(priorityLabel(option)) }
                        )
                    }
                }

                Text(
                    text = "Repetir",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = recurrenceType == RecurrenceType.NONE,
                        onClick = { recurrenceType = RecurrenceType.NONE },
                        label = { Text("Não repete") }
                    )
                    FilterChip(
                        selected = recurrenceType == RecurrenceType.DAILY,
                        onClick = { recurrenceType = RecurrenceType.DAILY },
                        label = { Text("Diariamente") }
                    )
                    FilterChip(
                        selected = recurrenceType == RecurrenceType.WEEKLY,
                        onClick = { recurrenceType = RecurrenceType.WEEKLY },
                        label = { Text("Semanalmente") }
                    )
                }

                if (recurrenceType == RecurrenceType.WEEKLY) {
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WEEKDAY_OPTIONS.forEach { (day, label) ->
                            FilterChip(
                                selected = day in weeklyDays,
                                onClick = {
                                    weeklyDays = if (day in weeklyDays) weeklyDays - day else weeklyDays + day
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                if (availableTags.isNotEmpty()) {
                    Text(
                        text = "Tags",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableTags.forEach { tag ->
                            val isSelected = tag.id in selectedTagIds
                            TagChip(
                                tag = tag,
                                selected = isSelected,
                                onClick = {
                                    selectedTagIds = if (isSelected) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rule = when (recurrenceType) {
                        RecurrenceType.NONE -> null
                        RecurrenceType.DAILY -> RecurrenceRule.Daily
                        RecurrenceType.WEEKLY -> if (weeklyDays.isNotEmpty()) RecurrenceRule.Weekly(weeklyDays) else null
                    }
                    onConfirm(
                        TaskFormResult(
                            title = title,
                            description = description,
                            date = taskDate,
                            deadline = deadline,
                            priority = priority,
                            recurrence = rule?.encode(),
                            tagIds = selectedTagIds
                        )
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text(if (isEditing) "Salvar" else "Adicionar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )

    if (showDayPicker) {
        val dayPickerState = rememberDatePickerState(
            initialSelectedDateMillis = taskDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDayPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dayPickerState.selectedDateMillis?.let { millis ->
                        taskDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDayPicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDayPicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = dayPickerState)
        }
    }

    if (showDeadlinePicker) {
        val deadlinePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (deadline ?: taskDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    deadlinePickerState.selectedDateMillis?.let { millis ->
                        deadline = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDeadlinePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeadlinePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = deadlinePickerState)
        }
    }
}
