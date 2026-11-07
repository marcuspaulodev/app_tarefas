package com.marcuspaulo.tarefas.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * O Android apaga os alarmes ao reiniciar o celular; também é preciso
 * recalculá-los quando a hora ou o fuso mudam.
 */
class RescheduleAlarmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyCheckScheduler.scheduleAll(context)
    }
}
