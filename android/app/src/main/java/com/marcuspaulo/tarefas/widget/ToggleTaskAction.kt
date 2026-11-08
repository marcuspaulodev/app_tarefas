package com.marcuspaulo.tarefas.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.sync.SessionStore
import com.marcuspaulo.tarefas.data.TaskRepository
import com.marcuspaulo.tarefas.notifications.ReminderScheduler

/**
 * Checkbox do widget: conclui a tarefa com a mesma lógica do app
 * (gera a próxima ocorrência se for recorrente e cancela o lembrete de prazo).
 */
class ToggleTaskAction : ActionCallback {

    companion object {
        // Nome novo: widgets desenhados antes da versão 5 guardavam um Long em "taskId".
        val TaskIdKey = ActionParameters.Key<String>("taskUuid")
        val WorkspaceKey = ActionParameters.Key<String>("workspace")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        val store = SessionStore.get(context)
        val workspace = parameters[WorkspaceKey] ?: return
        if (workspace != store.workspaceKey) return
        val database = AppDatabase.activeOrNull(context) ?: return
        val repository = TaskRepository(database)

        val taskWithTags = repository.taskWithTagsById(taskId) ?: return
        // Evita "desconcluir" se o toque chegou depois de a tarefa já ter sido concluída no app.
        if (taskWithTags.task.completed || workspace != store.workspaceKey) return

        repository.toggleCompleted(taskWithTags.task, taskWithTags.tags.map { it.id }.toSet())
        if (workspace != store.workspaceKey) return
        ReminderScheduler(context).cancelForTask(taskId)
        TarefasWidget().update(context, glanceId)
    }
}
