package com.marcuspaulo.tarefas.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.notifications.ReminderScheduler
import com.marcuspaulo.tarefas.widget.TarefasWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class SyncProgress(val account: String? = null, val running: Boolean = false, val message: String? = null)
object SyncStatus {
    internal val mutable = MutableStateFlow(SyncProgress())
    val state = mutable.asStateFlow()
}

object SyncScheduler {
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun request(context: Context) {
        val session = SessionStore.get(context).session.value ?: return
        if (session.expired) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connected).setInputData(workDataOf("account" to session.key))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("account_sync").build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync_${session.key}", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun schedulePeriodic(context: Context) {
        val session = SessionStore.get(context).session.value ?: return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connected).setInputData(workDataOf("account" to session.key))
            .addTag("account_sync").build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync_periodic_${session.key}", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) { WorkManager.getInstance(context).cancelAllWorkByTag("account_sync") }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SessionStore.get(applicationContext)
        val session = store.session.value ?: return Result.success()
        if (session.key != inputData.getString("account") || session.expired) return Result.success()
        val database = AppDatabase.forSession(applicationContext, session) ?: return Result.success()
        SyncStatus.mutable.value = SyncProgress(session.key, running = true)
        try {
            val complete = SyncEngine(database, HttpSyncApi()).synchronize(session) { store.isCurrent(session) }
            if (!store.isCurrent(session)) return Result.success()
            val scheduler = ReminderScheduler(applicationContext)
            database.taskDao().getIncludingDeleted().forEach { task ->
                if (task.completed || task.deletedAt != null || task.deadline == null) scheduler.cancelForTask(task.id)
                else if (!task.deadline.isBefore(LocalDate.now())) scheduler.scheduleForTask(task)
            }
            TarefasWidget().updateAll(applicationContext)
            SyncStatus.mutable.value = SyncProgress(session.key)
            return if (complete) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ApiException) {
            if (store.isCurrent(session)) {
                if (error.status == 401) store.expire(session)
                SyncStatus.mutable.value = SyncProgress(session.key, message = error.message)
            }
            return if (error.status == 429 || error.status >= 500) Result.retry() else Result.failure()
        } catch (error: IOException) {
            if (store.isCurrent(session)) SyncStatus.mutable.value = SyncProgress(session.key,
                message = "Sem conexão com o servidor. Suas alterações estão salvas neste aparelho.")
            return Result.retry()
        } catch (error: Exception) {
            if (store.isCurrent(session)) SyncStatus.mutable.value = SyncProgress(session.key,
                message = "Não foi possível sincronizar. Confira o servidor e tente novamente.")
            return Result.failure()
        } finally {
            if (SyncStatus.mutable.value.account == session.key && SyncStatus.mutable.value.running)
                SyncStatus.mutable.value = SyncProgress(session.key)
        }
    }
}
