package com.marcuspaulo.tarefas.data

import java.time.DayOfWeek
import java.time.LocalDate

sealed class RecurrenceRule {

    abstract fun encode(): String
    abstract fun nextOccurrenceAfter(date: LocalDate): LocalDate

    object Daily : RecurrenceRule() {
        override fun encode() = "DAILY"
        override fun nextOccurrenceAfter(date: LocalDate): LocalDate = date.plusDays(1)
    }

    data class Weekly(val days: Set<DayOfWeek>) : RecurrenceRule() {
        override fun encode() = "WEEKLY:" + days.sortedBy { it.value }.joinToString(",") { it.value.toString() }

        override fun nextOccurrenceAfter(date: LocalDate): LocalDate {
            var next = date.plusDays(1)
            repeat(7) {
                if (next.dayOfWeek in days) return next
                next = next.plusDays(1)
            }
            return date.plusWeeks(1)
        }
    }

    companion object {
        fun decode(raw: String?): RecurrenceRule? {
            if (raw == null) return null
            if (raw == "DAILY") return Daily
            if (raw.startsWith("WEEKLY:")) {
                val days = raw.removePrefix("WEEKLY:")
                    .split(",")
                    .mapNotNull { it.toIntOrNull() }
                    .map { DayOfWeek.of(it) }
                    .toSet()
                return if (days.isEmpty()) null else Weekly(days)
            }
            return null
        }
    }
}
