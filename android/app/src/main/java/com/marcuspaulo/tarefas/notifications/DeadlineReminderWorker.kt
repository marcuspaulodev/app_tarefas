package com.marcuspaulo.tarefas.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marcuspaulo.tarefas.MainActivity
import com.marcuspaulo.tarefas.R
import com.marcuspaulo.tarefas.TarefasApplication
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.sync.SessionStore
import com.marcuspaulo.tarefas.data.Ids

class DeadlineReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Lembretes agendados antes da versão 5 guardavam um número: não há mais tarefa com esse
        // ID, e o TarefasApplication reagenda esses prazos com os IDs novos.
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.success()

        val store = SessionStore.get(applicationContext)
        val workspace = inputData.getString(KEY_WORKSPACE) ?: return Result.success()
        if (workspace != store.workspaceKey) return Result.success()
        val task = AppDatabase.activeOrNull(applicationContext)?.taskDao()?.getById(taskId)
        if (task == null || task.completed || task.deadline == null) return Result.success()

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (!notificationManager.areNotificationsEnabled()) return Result.success()

        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            Ids.notificationId(taskId),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, TarefasApplication.DEADLINE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Prazo vence hoje")
            .setContentText(task.title)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (workspace != store.workspaceKey) return Result.success()
        notificationManager.notify(Ids.notificationId(taskId), notification)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
        const val KEY_WORKSPACE = "workspace"
    }
}
