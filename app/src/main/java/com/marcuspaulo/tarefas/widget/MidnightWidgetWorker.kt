package com.marcuspaulo.tarefas.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.TaskRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Roda uma vez por dia logo após a meia-noite: faz o rollover das tarefas
 * não concluídas e redesenha o widget pro novo "hoje", mesmo sem o app ser aberto.
 */
class MidnightWidgetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(applicationContext)
        TaskRepository(database.taskDao(), database.tagDao()).rolloverOverdueTasks()
        TarefasWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "widget_midnight_refresh"
        private val RUN_TIME: LocalTime = LocalTime.of(0, 1)

        fun schedule(context: Context) {
            val nextRun = LocalDateTime.of(LocalDate.now().plusDays(1), RUN_TIME)
            val delay = Duration.between(LocalDateTime.now(), nextRun)

            val request = PeriodicWorkRequestBuilder<MidnightWidgetWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()

            // KEEP: não reinicia o ciclo a cada abertura do app.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
