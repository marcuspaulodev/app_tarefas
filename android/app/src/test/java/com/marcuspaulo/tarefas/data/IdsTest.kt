package com.marcuspaulo.tarefas.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class IdsTest {

    @Test
    fun `novo id e um UUID versao 7 com o horario no comeco`() {
        val id = Ids.newId(1_700_000_000_000)
        val uuid = UUID.fromString(id)

        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
        assertEquals(1_700_000_000_000, uuid.mostSignificantBits ushr 16)
    }

    @Test
    fun `ids de horarios diferentes ficam em ordem de criacao`() {
        val earlier = Ids.newId(1_700_000_000_000)
        val later = Ids.newId(1_700_000_000_001)

        assertTrue(earlier < later)
    }

    @Test
    fun `ids gerados no mesmo milissegundo sao diferentes`() {
        val ids = (1..1000).map { Ids.newId(1_700_000_000_000) }.toSet()

        assertEquals(1000, ids.size)
    }

    @Test
    fun `ocorrencia da mesma serie e dia tem sempre o mesmo id`() {
        val day = LocalDate.of(2026, 11, 10)

        assertEquals(Ids.occurrenceId("serie", day), Ids.occurrenceId("serie", day))
        assertNotEquals(Ids.occurrenceId("serie", day), Ids.occurrenceId("serie", day.plusDays(1)))
        assertNotEquals(Ids.occurrenceId("serie", day), Ids.occurrenceId("outra", day))
    }

    @Test
    fun `chave do widget nunca cai na faixa reservada do Glance`() {
        val ids = (1..10_000).map { Ids.newId() } +
            (1..1_000).map { Ids.occurrenceId(Ids.newId(), LocalDate.of(2026, 11, 10)) }

        ids.forEach { id ->
            val key = Ids.stableLong(id)
            assertTrue("$id -> $key", key >= 0)
            assertEquals(key, Ids.stableLong(id))
        }
    }

    @Test
    fun `id de notificacao nunca e negativo`() {
        repeat(1000) { assertTrue(Ids.notificationId(Ids.newId()) >= 0) }
    }
}
