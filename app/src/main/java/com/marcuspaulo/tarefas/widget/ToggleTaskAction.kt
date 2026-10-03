package com.marcuspaulo.tarefas.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.TaskRepository
import com.marcuspaulo.tarefas.notifications.ReminderScheduler

/**
 * Checkbox do widget: conclui a tarefa com a mesma lógica do app
 * (gera a próxima ocorrência se for recorrente e cancela o lembrete de prazo).
 */
class ToggleTaskAction : ActionCallback {

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("taskId")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        val database = AppDatabase.getInstance(context)
        val repository = TaskRepository(database.taskDao(), database.tagDao())

        val taskWithTags = repository.taskWithTagsById(taskId) ?: return
        // Evita "desconcluir" se o toque chegou depois de a tarefa já ter sido concluída no app.
        if (taskWithTags.task.completed) return

        repository.toggleCompleted(taskWithTags.task, taskWithTags.tags.map { it.id }.toSet())
        ReminderScheduler(context).cancelForTask(taskId)
        TarefasWidget().update(context, glanceId)
    }
}
