package com.marcuspaulo.tarefas

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class TarefasApplication : Application() {

    companion object {
        const val DEADLINE_CHANNEL_ID = "deadline_reminders"
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            DEADLINE_CHANNEL_ID,
            "Lembretes de prazo",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisa quando uma tarefa vence no dia"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
