package com.marcuspaulo.tarefas

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.glance.appwidget.updateAll
import androidx.room.InvalidationTracker
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.TaskRepository
import com.marcuspaulo.tarefas.notifications.DailyCheckScheduler
import com.marcuspaulo.tarefas.notifications.ReminderScheduler
import com.marcuspaulo.tarefas.widget.MidnightWidgetWorker
import com.marcuspaulo.tarefas.widget.TarefasWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import com.marcuspaulo.tarefas.sync.SessionStore
import com.marcuspaulo.tarefas.sync.SyncScheduler
import androidx.work.WorkManager
import java.time.LocalDate

class TarefasApplication : Application() {

    companion object {
        const val DEADLINE_CHANNEL_ID = "deadline_reminders"
        const val DAILY_CHECK_CHANNEL_ID = "daily_checks"
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            DEADLINE_CHANNEL_ID,
            "Lembretes de prazo",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisa quando uma tarefa vence no dia"
        }
        val dailyCheckChannel = NotificationChannel(
            DAILY_CHECK_CHANNEL_ID,
            "Checagem diária",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Lembra de checar as tarefas pendentes às 8h, 13h e 18h"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(channel, dailyCheckChannel))
        DailyCheckScheduler.scheduleAll(this)

        MidnightWidgetWorker.schedule(this)
        val store = SessionStore.get(this)
        var previousKey = store.workspaceKey
        appScope.launch {
            store.session.collectLatest { session ->
                val key = store.workspaceKey
                if (previousKey != key || key == null) {
                    SyncScheduler.cancel(this@TarefasApplication)
                    WorkManager.getInstance(this@TarefasApplication).cancelAllWorkByTag("deadline_reminder")
                    getSystemService(NotificationManager::class.java).cancelAll()
                }
                previousKey = key
                TarefasWidget().updateAll(this@TarefasApplication)
                val database = AppDatabase.activeOrNull(this@TarefasApplication) ?: return@collectLatest
                val observer = object : InvalidationTracker.Observer("tasks", "tags") {
                    override fun onInvalidated(tables: Set<String>) {
                        appScope.launch {
                            if (store.workspaceKey != key) return@launch
                            TarefasWidget().updateAll(this@TarefasApplication)
                            if (database.taskDao().pendingSync().isNotEmpty() || database.tagDao().pendingSync().isNotEmpty())
                                SyncScheduler.request(this@TarefasApplication)
                        }
                    }
                }
                database.invalidationTracker.addObserver(observer)
                try {
                    val scheduler = ReminderScheduler(this@TarefasApplication)
                    TaskRepository(database).pendingWithDeadlineFrom(LocalDate.now()).forEach(scheduler::scheduleForTask)
                    if (session != null && !session.expired) {
                        SyncScheduler.schedulePeriodic(this@TarefasApplication)
                        SyncScheduler.request(this@TarefasApplication)
                    }
                    awaitCancellation()
                } finally {
                    database.invalidationTracker.removeObserver(observer)
                }
            }
        }
    }
}
