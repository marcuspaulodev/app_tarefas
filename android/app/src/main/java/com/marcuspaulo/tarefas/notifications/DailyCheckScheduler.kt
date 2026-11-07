package com.marcuspaulo.tarefas.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Lembretes diários pra checar as tarefas em horários fixos. Usa alarmes
 * exatos (o WorkManager pode atrasar bastante); cada horário tem seu próprio
 * alarme, que se reagenda pro dia seguinte quando dispara.
 */
object DailyCheckScheduler {

    val CHECK_TIMES: List<LocalTime> = listOf(
        LocalTime.of(8, 0),
        LocalTime.of(13, 0),
        LocalTime.of(18, 0)
    )

    const val EXTRA_HOUR = "hour"
    const val EXTRA_MINUTE = "minute"

    fun scheduleAll(context: Context) {
        CHECK_TIMES.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, time: LocalTime) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(LocalDate.now(), time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, DailyCheckReceiver::class.java)
            .putExtra(EXTRA_HOUR, time.hour)
            .putExtra(EXTRA_MINUTE, time.minute)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(time),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun requestCode(time: LocalTime) = 10_000 + time.hour * 60 + time.minute
}
