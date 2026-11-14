package com.marcuspaulo.tarefas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.marcuspaulo.tarefas.data.ChecklistItem
import com.marcuspaulo.tarefas.data.Tag
import com.marcuspaulo.tarefas.data.Task
import com.marcuspaulo.tarefas.ui.theme.priorityColor
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM")
private val FULL_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun TaskItem(
    task: Task,
    tags: List<Tag>,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleItem: (String) -> Unit = {},
    showDate: Boolean = false
) {
    var checklistOpen by rememberSaveable(task.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(priorityColor(task.priority))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null
                )
                if (task.recurrence != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Tarefa recorrente",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showDate) {
                Text(
                    text = task.effectiveDate().format(FULL_DATE_FMT),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (task.isOverdueRollover) {
                Text(
                    text = "Atrasada desde ${task.originalDate.format(DATE_FMT)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (task.deadline != null) {
                Text(
                    text = if (task.isPastDeadline) "Prazo vencido em ${task.deadline.format(DATE_FMT)}"
                    else "Prazo: ${task.deadline.format(DATE_FMT)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.isPastDeadline) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag -> TagChip(tag = tag) }
                }
            }
            if (task.checklist.isNotEmpty()) {
                ChecklistProgress(
                    items = task.checklist,
                    open = checklistOpen,
                    onClick = { checklistOpen = !checklistOpen }
                )
                if (checklistOpen) {
                    task.checklist.forEach { item ->
                        ChecklistRow(item = item, onToggle = { onToggleItem(item.id) })
                    }
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Excluir tarefa")
        }
    }
}

@Composable
private fun ChecklistProgress(items: List<ChecklistItem>, open: Boolean, onClick: () -> Unit) {
    val done = items.count { it.done }
    val color = if (done == items.size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Checklist: $done de ${items.size} itens marcados" }
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "$done/${items.size}", style = MaterialTheme.typography.labelMedium, color = color)
        Icon(
            if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color
        )
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItem, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
    ) {
        Checkbox(checked = item.done, onCheckedChange = { onToggle() }, modifier = Modifier.size(36.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (item.done) TextDecoration.LineThrough else null
        )
    }
}
