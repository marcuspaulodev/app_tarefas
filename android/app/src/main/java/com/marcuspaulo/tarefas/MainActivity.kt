package com.marcuspaulo.tarefas

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marcuspaulo.tarefas.backup.BackupManager
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.TaskRepository
import com.marcuspaulo.tarefas.notifications.ReminderScheduler
import com.marcuspaulo.tarefas.sync.SessionStore
import com.marcuspaulo.tarefas.sync.SessionValidator
import com.marcuspaulo.tarefas.sync.HttpSyncApi
import com.marcuspaulo.tarefas.sync.SyncScheduler
import com.marcuspaulo.tarefas.sync.SyncStatus
import com.marcuspaulo.tarefas.ui.AccountScreen
import com.marcuspaulo.tarefas.ui.TarefasApp
import com.marcuspaulo.tarefas.ui.theme.TarefasTheme
import com.marcuspaulo.tarefas.viewmodel.TaskViewModel
import com.marcuspaulo.tarefas.viewmodel.WorkspaceViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val sessions by lazy { SessionStore.get(applicationContext) }
    private var backupWorkspace: String? = null
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null || backupWorkspace != sessions.workspaceKey) return@registerForActivityResult
        val database = AppDatabase.activeOrNull(this) ?: return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val json = BackupManager(database, mergeIntoAccount = sessions.session.value != null).exportToJson()
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(this@MainActivity, "Backup salvo", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { Toast.makeText(this@MainActivity, "Não foi possível salvar o backup", Toast.LENGTH_LONG).show() }
        }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || backupWorkspace != sessions.workspaceKey) return@registerForActivityResult
        val database = AppDatabase.activeOrNull(this) ?: return@registerForActivityResult
        val connected = sessions.session.value != null
        lifecycleScope.launch {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Arquivo indisponível")
                BackupManager(database, mergeIntoAccount = connected).importFromJson(json)
                Toast.makeText(this@MainActivity, if (connected) "Backup importado" else "Backup restaurado", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { Toast.makeText(this@MainActivity, "Não foi possível ler o backup. As tarefas atuais foram preservadas.", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val session by sessions.session.collectAsStateWithLifecycle()
            val workspace = sessions.workspaceKey
            val owner: WorkspaceViewModel = viewModel()
            owner.select(workspace)
            var accountScreen by remember(session?.key, session?.expired) { mutableStateOf(false) }
            val validator = remember(sessions) { SessionValidator(sessions, HttpSyncApi()::validateSession) }
            if (session != null && !session!!.expired) {
                LifecycleResumeEffect(session) {
                    val refresh = lifecycleScope.launch {
                        while (true) {
                            withContext(Dispatchers.IO) { validator.check() }
                            SyncScheduler.request(applicationContext)
                            delay(30000)
                        }
                    }
                    onPauseOrDispose { refresh.cancel() }
                }
            }
            TarefasTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (workspace == null || accountScreen) {
                        AccountScreen(sessions, if (workspace != null) ({ accountScreen = false }) else null)
                    } else {
                        val database = remember(workspace) { AppDatabase.getInstance(applicationContext) }
                        val repository = remember(database) { TaskRepository(database) }
                        val viewModel: TaskViewModel = viewModel(viewModelStoreOwner = owner,
                            factory = TaskViewModel.Factory(repository, ReminderScheduler(applicationContext)))
                        val state by remember(database) { database.syncStateDao().observe() }.collectAsStateWithLifecycle(null)
                        val pending by remember(database) { combine(database.taskDao().pendingSyncCount(), database.tagDao().pendingSyncCount()) { a, b -> a + b } }
                            .collectAsStateWithLifecycle(0)
                        val progress by SyncStatus.state.collectAsStateWithLifecycle()
                        val label = when {
                            session == null -> "Somente neste aparelho"
                            progress.account == workspace && progress.running -> "Sincronizando…"
                            pending > 0 -> "$pending alterações aguardando envio"
                            progress.account == workspace && progress.message != null -> "Sincronização indisponível"
                            state?.lastSyncedAt != null -> "Sincronizado"
                            else -> "Aguardando sincronização"
                        }
                        LifecycleResumeEffect(viewModel) {
                            viewModel.refreshToday()
                            onPauseOrDispose { }
                        }
                        TarefasApp(viewModel = viewModel, syncLabel = label, connected = session != null,
                            onAccount = { accountScreen = true },
                            onExportBackup = {
                                backupWorkspace = workspace
                                exportLauncher.launch("tarefas_backup_${LocalDate.now()}.json")
                            },
                            onImportBackup = { backupWorkspace = workspace; importLauncher.launch(arrayOf("application/json")) })
                    }
                }
            }
        }
    }
}
