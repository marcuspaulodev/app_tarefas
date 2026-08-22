package com.marcuspaulo.tarefas.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val PT_BR = Locale("pt", "BR")
private val WEEKDAY_LABELS = listOf("dom", "seg", "ter", "qua", "qui", "sex", "sáb")

@Composable
fun CalendarView(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    pendingCounts: Map<LocalDate, Int>,
    onMonthChange: (Long) -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onMonthChange(-1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Mês anterior")
            }
            Text(
                text = visibleMonth.month.getDisplayName(TextStyle.FULL, PT_BR)
                    .replaceFirstChar { it.uppercase() } + " ${visibleMonth.year}",
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { onMonthChange(1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Próximo mês")
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val days = buildCalendarDays(visibleMonth)
        val today = LocalDate.now()
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            val isSelected = date == selectedDate
                            val isToday = date == today
                            val hasPending = (pendingCounts[date] ?: 0) > 0

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .then(
                                        if (isToday && !isSelected) Modifier.border(
                                            1.dp, MaterialTheme.colorScheme.primary, CircleShape
                                        ) else Modifier
                                    )
                                    .clickable { onDateSelected(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                                if (hasPending) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.error
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildCalendarDays(month: YearMonth): List<LocalDate?> {
    val firstOfMonth = month.atDay(1)
    val leadingBlanks = firstOfMonth.dayOfWeek.value % 7 // Sunday = 0
    val daysInMonth = month.lengthOfMonth()

    val cells = mutableListOf<LocalDate?>()
    repeat(leadingBlanks) { cells.add(null) }
    for (day in 1..daysInMonth) {
        cells.add(month.atDay(day))
    }
    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return cells
}
