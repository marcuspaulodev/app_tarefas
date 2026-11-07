package com.marcuspaulo.tarefas.data

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.LocalDate
import java.util.UUID

/** IDs gerados no próprio aparelho, para tarefas e tags criadas offline não colidirem entre aparelhos. */
object Ids {

    private val random = SecureRandom()

    /**
     * UUID v7: os primeiros 48 bits são o horário em milissegundos, então IDs novos
     * ficam em ordem de criação no índice do MySQL em vez de espalhados.
     */
    fun newId(timestampMillis: Long = System.currentTimeMillis()): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        for (i in 0..5) bytes[i] = (timestampMillis ushr (8 * (5 - i))).toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte() // versão 7
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte() // variante RFC 4122
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long).toString()
    }

    /**
     * ID da ocorrência de uma série recorrente em um dia. É o mesmo em qualquer aparelho,
     * então concluir a mesma tarefa duas vezes, aqui ou em outro aparelho, não duplica a próxima.
     */
    fun occurrenceId(seriesId: String, date: LocalDate): String =
        UUID.nameUUIDFromBytes("$seriesId/$date".toByteArray()).toString()

    /**
     * Número estável e não negativo para APIs que só aceitam Long como chave, como a
     * lista do widget. O Glance recusa chaves abaixo de -0x4000000000000000 (faixa
     * reservada) e, se uma única tarefa cair ali, o widget inteiro mostra erro.
     */
    fun stableLong(id: String): Long =
        UUID.fromString(id).let { it.mostSignificantBits xor it.leastSignificantBits } and Long.MAX_VALUE

    /** ID de notificação não negativo: os negativos ficam para avisos fixos, como a checagem diária. */
    fun notificationId(id: String): Int = id.hashCode() and Int.MAX_VALUE
}
