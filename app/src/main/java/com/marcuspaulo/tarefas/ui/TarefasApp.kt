package com.marcuspaulo.tarefas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marcuspaulo.tarefas.ui.calendar.CalendarView
import com.marcuspaulo.tarefas.ui.components.AddTaskDialog
import com.marcuspaulo.tarefas.ui.components.TagChip
import com.marcuspaulo.tarefas.ui.components.TaskItem
import com.marcuspaulo.tarefas.ui.tags.TagsScreen
import com.marcuspaulo.tarefas.viewmodel.TaskViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HEADER_FMT = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale("pt", "BR"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarefasApp(
    viewModel: TaskViewModel,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val tasks by viewModel.visibleTasks.collectAsStateWithLifecycle()
    val pendingCounts by viewModel.pendingCountsForVisibleMonth.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val editingTask by viewModel.editingTask.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showTagsScreen by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    if (showTagsScreen) {
        TagsScreen(
            tags = tags,
            onBack = { showTagsScreen = false },
            onAddTag = { name, colorHex -> viewModel.addTag(name, colorHex) },
            onUpdateTag = { viewModel.updateTag(it) },
            onDeleteTag = { viewModel.deleteTag(it) }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minhas Tarefas") },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.setSearchQuery("")
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar tarefas")
                    }
                    IconButton(onClick = { showTagsScreen = true }) {
                        Icon(Icons.Default.Label, contentDescription = "Gerenciar tags")
                    }
                    IconButton(onClick = { viewModel.goToToday() }) {
                        Icon(Icons.Default.Today, contentDescription = "Ir para hoje")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Fazer backup") },
                            onClick = {
                                showMenu = false
                                onExportBackup()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Restaurar backup") },
                            onClick = {
                                showMenu = false
                                showRestoreConfirm = true
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showSearch) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar tarefa")
                }
            }
        }
    ) { padding ->
        if (showSearch) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("Buscar por título ou descrição") },
                    singleLine = true
                )
                when {
                    searchQuery.isBlank() -> EmptyState("Digite algo para buscar.")
                    searchResults.isEmpty() -> EmptyState("Nenhuma tarefa encontrada.")
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(searchResults, key = { it.task.id }) { taskWithTags ->
                            TaskItem(
                                task = taskWithTags.task,
                                tags = taskWithTags.tags,
                                onToggle = { viewModel.toggleCompleted(taskWithTags) },
                                onDelete = { viewModel.deleteTask(taskWithTags.task) },
                                onEdit = {
                                    viewModel.startEditing(taskWithTags)
                                    showSearch = false
                                },
                                showDate = true
                            )
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                CalendarView(
                    visibleMonth = visibleMonth,
                    selectedDate = selectedDate,
                    pendingCounts = pendingCounts.associate { it.date to it.count },
                    onMonthChange = { viewModel.changeMonth(it) },
                    onDateSelected = { viewModel.selectDate(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = selectedDate.format(HEADER_FMT).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )

                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            TagChip(
                                tag = tag,
                                selected = tag.id in tagFilter,
                                onClick = { viewModel.toggleTagFilter(tag.id) }
                            )
                        }
                    }
                }

                if (tasks.isEmpty()) {
                    EmptyState("Nenhuma tarefa para este dia.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(tasks, key = { it.task.id }) { taskWithTags ->
                            TaskItem(
                                task = taskWithTags.task,
                                tags = taskWithTags.tags,
                                onToggle = { viewModel.toggleCompleted(taskWithTags) },
                                onDelete = { viewModel.deleteTask(taskWithTags.task) },
                                onEdit = { viewModel.startEditing(taskWithTags) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            date = selectedDate,
            availableTags = tags,
            onDismiss = { showAddDialog = false },
            onConfirm = { result ->
                viewModel.addTask(
                    result.title, result.description, result.date, result.deadline,
                    result.priority, result.recurrence, result.tagIds
                )
                showAddDialog = false
            }
        )
    }

    editingTask?.let { taskWithTags ->
        AddTaskDialog(
            date = taskWithTags.task.date,
            availableTags = tags,
            initial = taskWithTags.task,
            initialTagIds = taskWithTags.tags.map { it.id }.toSet(),
            onDismiss = { viewModel.cancelEditing() },
            onConfirm = { result ->
                viewModel.saveEdit(
                    result.title, result.description, result.date, result.deadline,
                    result.priority, result.recurrence, result.tagIds
                )
            }
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restaurar backup") },
            text = {
                Text(
                    "Isso vai substituir todas as tarefas e tags atuais pelas do arquivo de " +
                        "backup escolhido. Essa ação não pode ser desfeita. Continuar?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    onImportBackup()
                }) {
                    Text("Restaurar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ColumnScope.EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
