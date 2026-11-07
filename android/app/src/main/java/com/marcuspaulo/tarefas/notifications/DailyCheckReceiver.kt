package com.marcuspaulo.tarefas.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.marcuspaulo.tarefas.MainActivity
import com.marcuspaulo.tarefas.R
import com.marcuspaulo.tarefas.TarefasApplication
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private const val NOTIFICATION_ID = -1 // ids positivos são dos lembretes de prazo (id da tarefa)
private const val MAX_LISTED_TASKS = 5

/** Dispara nos horários de [DailyCheckScheduler]: avisa das pendentes e reagenda o próximo dia. */
class DailyCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val time = LocalTime.of(
            intent.getIntExtra(DailyCheckScheduler.EXTRA_HOUR, 8),
            intent.getIntExtra(DailyCheckScheduler.EXTRA_MINUTE, 0)
        )
        DailyCheckScheduler.schedule(context, time)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notifyPendingTasks(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun notifyPendingTasks(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return

        val database = AppDatabase.getInstance(context)
        val tasks = TaskRepository(database.taskDao(), database.tagDao())
            .pendingTasksUpTo(LocalDate.now())
            .first()
        // Nada pendente: não incomoda.
        if (tasks.isEmpty()) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = if (tasks.size == 1) "1 tarefa pendente hoje" else "${tasks.size} tarefas pendentes hoje"
        val style = NotificationCompat.InboxStyle().setSummaryText(summary)
        tasks.take(MAX_LISTED_TASKS).forEach { style.addLine(it.title) }
        if (tasks.size > MAX_LISTED_TASKS) style.addLine("+ ${tasks.size - MAX_LISTED_TASKS} mais")

        val notification = NotificationCompat.Builder(context, TarefasApplication.DAILY_CHECK_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Hora de checar suas tarefas")
            .setContentText(summary)
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Mesmo id: o aviso das 13h substitui o das 8h, em vez de empilhar.
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
