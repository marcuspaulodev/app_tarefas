package com.marcuspaulo.tarefas.ui.theme

import androidx.compose.ui.graphics.Color
import com.marcuspaulo.tarefas.data.Priority

fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.HIGH -> Color(0xFFE53935)
    Priority.MEDIUM -> Color(0xFFFFA726)
    Priority.LOW -> Color(0xFF43A047)
}

fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.HIGH -> "Alta"
    Priority.MEDIUM -> "Média"
    Priority.LOW -> "Baixa"
}
