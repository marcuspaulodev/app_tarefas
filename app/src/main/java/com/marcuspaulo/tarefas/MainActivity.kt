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
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marcuspaulo.tarefas.backup.BackupManager
import com.marcuspaulo.tarefas.data.AppDatabase
import com.marcuspaulo.tarefas.data.TaskRepository
import com.marcuspaulo.tarefas.notifications.ReminderScheduler
import com.marcuspaulo.tarefas.ui.TarefasApp
import com.marcuspaulo.tarefas.ui.theme.TarefasTheme
import com.marcuspaulo.tarefas.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val repository by lazy { TaskRepository(database.taskDao(), database.tagDao()) }
    private val reminderScheduler by lazy { ReminderScheduler(applicationContext) }
    private val backupManager by lazy { BackupManager(database) }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val json = backupManager.exportToJson()
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(this@MainActivity, "Backup salvo", Toast.LENGTH_SHORT).show()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (json != null) {
                backupManager.importFromJson(json)
                Toast.makeText(this@MainActivity, "Backup restaurado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            TarefasTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: TaskViewModel = viewModel(
                        factory = TaskViewModel.Factory(repository, reminderScheduler)
                    )
                    TarefasApp(
                        viewModel = viewModel,
                        onExportBackup = { exportLauncher.launch("tarefas_backup_${LocalDate.now()}.json") },
                        onImportBackup = { importLauncher.launch(arrayOf("application/json")) }
                    )
                }
            }
        }
    }
}
