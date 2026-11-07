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

        // Qualquer escrita na tabela de tarefas (criar, editar, concluir, excluir,
        // restaurar backup) redesenha o widget — sem precisar chamar nada no ViewModel.
        AppDatabase.getInstance(this).invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("tasks") {
                override fun onInvalidated(tables: Set<String>) {
                    appScope.launch { TarefasWidget().updateAll(this@TarefasApplication) }
                }
            }
        )
        MidnightWidgetWorker.schedule(this)
        rescheduleDeadlineRemindersOnce()
    }

    /**
     * Os lembretes agendados antes do banco versão 5 apontam para IDs numéricos que não
     * existem mais. Na primeira abertura depois da atualização, agenda de novo os prazos
     * pendentes com os IDs novos.
     */
    private fun rescheduleDeadlineRemindersOnce() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REMINDERS_USE_UUID, false)) return
        appScope.launch {
            val database = AppDatabase.getInstance(this@TarefasApplication)
            val scheduler = ReminderScheduler(this@TarefasApplication)
            TaskRepository(database.taskDao(), database.tagDao())
                .pendingWithDeadlineFrom(LocalDate.now())
                .forEach(scheduler::scheduleForTask)
            prefs.edit().putBoolean(KEY_REMINDERS_USE_UUID, true).apply()
        }
    }
}

private const val PREFS_NAME = "app_state"
private const val KEY_REMINDERS_USE_UUID = "reminders_use_uuid"
