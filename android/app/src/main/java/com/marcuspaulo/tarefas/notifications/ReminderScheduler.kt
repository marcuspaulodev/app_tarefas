package com.marcuspaulo.tarefas.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.marcuspaulo.tarefas.data.Task
import com.marcuspaulo.tarefas.sync.SessionStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private val REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

class ReminderScheduler(private val context: Context) {
    private val workspace = SessionStore.get(context).workspaceKey

    fun scheduleForTask(task: Task) {
        if (workspace == null || workspace != SessionStore.get(context).workspaceKey || task.completed || task.deletedAt != null) return
        val deadline = task.deadline ?: return
        cancelForTask(task.id)

        val reminderInstant = LocalDateTime.of(deadline, REMINDER_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        val delayMillis = ChronoUnit.MILLIS.between(Instant.now(), reminderInstant)
        if (delayMillis <= 0) return

        val request = OneTimeWorkRequestBuilder<DeadlineReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(DeadlineReminderWorker.KEY_TASK_ID, task.id)
                .putString(DeadlineReminderWorker.KEY_WORKSPACE, workspace).build())
            .addTag("deadline_reminder")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueWorkName(task.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelForTask(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(taskId))
    }

    private fun uniqueWorkName(taskId: String) = "deadline_reminder_${workspace}_$taskId"
}
