package com.marcuspaulo.tarefas.data

import org.json.JSONArray
import org.json.JSONObject

/** Item do checklist de uma tarefa. Guardado como JSON na própria linha da tarefa. */
data class ChecklistItem(
    val id: String = Ids.newId(),
    val text: String,
    val done: Boolean = false
)

/** Mesmas regras de app/Support/Checklist.php no servidor e de shared/fixtures/rules.json. */
object Checklist {
    const val MAX_ITEMS = 50
    const val MAX_TEXT = 255

    fun encode(items: List<ChecklistItem>): String = toJson(items).toString()

    fun decode(value: String?): List<ChecklistItem> =
        if (value.isNullOrBlank()) emptyList() else fromJson(JSONArray(value))

    fun toJson(items: List<ChecklistItem>): JSONArray = JSONArray(items.map {
        JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)
    })

    fun fromJson(array: JSONArray?): List<ChecklistItem> = if (array == null) emptyList() else
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ChecklistItem(item.getString("id"), item.getString("text"), item.getBoolean("done"))
        }

    /**
     * Checklist da próxima ocorrência de uma recorrente: só os itens que ficaram pendentes,
     * com os mesmos IDs, para o app e o servidor gerarem a mesma lista.
     */
    fun forNextOccurrence(items: List<ChecklistItem>): List<ChecklistItem> =
        items.filter { !it.done }.map { it.copy(done = false) }

    /** Marcar este item completa o checklist de uma tarefa aberta: hora de perguntar se quer concluir. */
    fun completesChecklist(task: Task, itemId: String): Boolean {
        if (task.completed) return false
        val item = task.checklist.firstOrNull { it.id == itemId } ?: return false
        return !item.done && task.checklist.all { it.done || it.id == itemId }
    }

    /** Textos sem espaços nas pontas e sem itens vazios, como o servidor guarda. */
    fun cleaned(items: List<ChecklistItem>): List<ChecklistItem> =
        items.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }.take(MAX_ITEMS)
}
