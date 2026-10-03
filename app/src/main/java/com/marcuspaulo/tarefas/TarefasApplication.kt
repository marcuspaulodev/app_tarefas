package com.marcuspaulo.tarefas

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.glance.appwidget.updateAll
import androidx.room.InvalidationTracker
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.notifications.DailyCheckScheduler
import com.marcuspaulo.tarefas.widget.MidnightWidgetWorker
import com.marcuspaulo.tarefas.widget.TarefasWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    }
}
