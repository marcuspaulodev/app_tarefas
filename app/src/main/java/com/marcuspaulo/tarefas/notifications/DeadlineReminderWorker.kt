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

class DeadlineReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId == -1L) return Result.failure()

        val task = AppDatabase.getInstance(applicationContext).taskDao().getById(taskId)
        if (task == null || task.completed || task.deadline == null) return Result.success()

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (!notificationManager.areNotificationsEnabled()) return Result.success()

        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            taskId.toInt(),
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

        notificationManager.notify(taskId.toInt(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "taskId"
    }
}
