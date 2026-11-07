package com.marcuspaulo.tarefas.data

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * Roda os casos de shared/fixtures/rules.json, os mesmos que o backend roda.
 * Se este teste falhar, as regras do app e do servidor divergiram.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SharedRulesTest {

    // Os testes do Gradle rodam a partir de android/app.
    private val fixtures = JSONObject(File("../../shared/fixtures/rules.json").readText())

    private fun cases(name: String) = fixtures.getJSONArray(name).let { array ->
        (0 until array.length()).map { array.getJSONObject(it) }
    }

    @Test
    fun `proxima ocorrencia`() {
        cases("next_occurrence").forEach { case ->
            val rule = RecurrenceRule.decode(case.getString("rule"))!!
            assertEquals(
                case.toString(),
                LocalDate.parse(case.getString("next")),
                rule.nextOccurrenceAfter(LocalDate.parse(case.getString("date")))
            )
        }
    }

    @Test
    fun `regras invalidas`() {
        val array = fixtures.getJSONArray("invalid_rules")
        (0 until array.length()).forEach { i ->
            val raw = if (array.isNull(i)) null else array.getString(i)
            assertNull("regra: $raw", RecurrenceRule.decode(raw))
        }
    }

    @Test
    fun `id da ocorrencia`() {
        cases("occurrence_id").forEach { case ->
            assertEquals(
                case.toString(),
                case.getString("id"),
                Ids.occurrenceId(case.getString("series_id"), LocalDate.parse(case.getString("date")))
            )
        }
    }

    @Test
    fun `dia em que a tarefa aparece`() {
        cases("shown_on").forEach { case ->
            val task = Task(
                title = "caso",
                date = LocalDate.parse(case.getString("date")),
                completed = case.getBoolean("completed")
            )
            assertEquals(
                case.toString(),
                LocalDate.parse(case.getString("shown_on")),
                task.effectiveDate(LocalDate.parse(case.getString("today")))
            )
        }
    }
}
