package com.marcuspaulo.tarefas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.marcuspaulo.tarefas.data.Checklist
import com.marcuspaulo.tarefas.data.ChecklistItem
import com.marcuspaulo.tarefas.data.DateCount
import com.marcuspaulo.tarefas.data.Priority
import com.marcuspaulo.tarefas.data.Tag
import com.marcuspaulo.tarefas.data.Task
import com.marcuspaulo.tarefas.data.TaskRepository
import com.marcuspaulo.tarefas.data.TaskWithTags
import com.marcuspaulo.tarefas.notifications.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class TaskViewModel(
    private val repository: TaskRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth

    private val _today = MutableStateFlow(LocalDate.now())

    val tasksForSelectedDate: StateFlow<List<TaskWithTags>> = combine(_selectedDate, _today, ::Pair)
        .flatMapLatest { (date, today) -> repository.tasksForDate(date, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCountsForVisibleMonth: StateFlow<List<DateCount>> = combine(_visibleMonth, _today, ::Pair)
        .flatMapLatest { (month, today) ->
            repository.pendingCountsBetween(month.atDay(1), month.atEndOfMonth(), today)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<Tag>> = repository.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editingTask = MutableStateFlow<TaskWithTags?>(null)
    val editingTask: StateFlow<TaskWithTags?> = _editingTask

    private val _tagFilter = MutableStateFlow<Set<String>>(emptySet())
    val tagFilter: StateFlow<Set<String>> = _tagFilter

    val visibleTasks: StateFlow<List<TaskWithTags>> = combine(
        tasksForSelectedDate,
        _tagFilter
    ) { tasks, filter ->
        if (filter.isEmpty()) tasks
        else tasks.filter { it.tags.any { tag -> tag.id in filter } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val searchResults: StateFlow<List<TaskWithTags>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else repository.searchTasks(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Com o app aberto na virada do dia, as atrasadas passam para o novo "hoje".
        viewModelScope.launch {
            while (true) {
                refreshToday()
                val nextDay = LocalDate.now().plusDays(1).atStartOfDay()
                delay(Duration.between(LocalDateTime.now(), nextDay).toMillis() + 1)
            }
        }
    }

    /** O timer acima pode atrasar com o celular em repouso, então a tela também chama ao voltar. */
    fun refreshToday() {
        _today.value = LocalDate.now()
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun changeMonth(delta: Long) {
        _visibleMonth.value = _visibleMonth.value.plusMonths(delta)
    }

    fun goToToday() {
        val today = LocalDate.now()
        _selectedDate.value = today
        _visibleMonth.value = YearMonth.from(today)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addTask(
        title: String,
        description: String,
        date: LocalDate,
        deadline: LocalDate?,
        priority: Priority,
        recurrence: String?,
        tagIds: Set<String>,
        checklist: List<ChecklistItem> = emptyList()
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val task = repository.addTask(title.trim(), description.trim(), date, deadline, priority, recurrence, tagIds, checklist)
            reminderScheduler.scheduleForTask(task)
        }
    }

    fun toggleTagFilter(tagId: String) {
        _tagFilter.value = if (tagId in _tagFilter.value) {
            _tagFilter.value - tagId
        } else {
            _tagFilter.value + tagId
        }
    }

    fun startEditing(task: TaskWithTags) {
        _editingTask.value = task
    }

    fun cancelEditing() {
        _editingTask.value = null
    }

    fun saveEdit(
        title: String,
        description: String,
        date: LocalDate,
        deadline: LocalDate?,
        priority: Priority,
        recurrence: String?,
        tagIds: Set<String>,
        checklist: List<ChecklistItem> = emptyList()
    ) {
        val original = _editingTask.value?.task ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            val edited = original.copy(
                title = title.trim(),
                description = description.trim(),
                deadline = deadline,
                priority = priority,
                recurrence = recurrence,
                checklist = checklist
            )
            val updated = repository.updateTask(edited, tagIds, selectedDate = date) ?: return@launch
            if (deadline != null) {
                reminderScheduler.scheduleForTask(updated)
            } else {
                reminderScheduler.cancelForTask(updated.id)
            }
        }
        _editingTask.value = null
    }

    fun toggleCompleted(taskWithTags: TaskWithTags) {
        viewModelScope.launch {
            val task = taskWithTags.task
            val tagIds = taskWithTags.tags.map { it.id }.toSet()
            val updated = repository.toggleCompleted(task, tagIds)
            if (updated.completed) {
                reminderScheduler.cancelForTask(task.id)
            } else if (task.deadline != null) {
                reminderScheduler.scheduleForTask(updated)
            }
        }
    }

    /** Tarefa cujo checklist acabou de ser completado, para perguntar se ela também deve ser concluída. */
    private val _completionPrompt = MutableStateFlow<TaskWithTags?>(null)
    val completionPrompt: StateFlow<TaskWithTags?> = _completionPrompt

    fun toggleChecklistItem(taskWithTags: TaskWithTags, itemId: String) {
        viewModelScope.launch {
            val completes = Checklist.completesChecklist(taskWithTags.task, itemId)
            val updated = repository.toggleChecklistItem(taskWithTags.task.id, itemId) ?: return@launch
            if (completes && updated.checklist.all { it.done } && !updated.completed) {
                _completionPrompt.value = taskWithTags.copy(task = updated)
            }
        }
    }

    fun confirmCompletion() {
        val taskWithTags = _completionPrompt.value ?: return
        _completionPrompt.value = null
        viewModelScope.launch {
            val current = repository.taskWithTagsById(taskWithTags.task.id) ?: return@launch
            if (!current.task.completed) toggleCompleted(current)
        }
    }

    fun dismissCompletionPrompt() {
        _completionPrompt.value = null
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            reminderScheduler.cancelForTask(task.id)
            repository.deleteTask(task)
        }
    }

    fun addTag(name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addTag(name, colorHex)
        }
    }

    fun updateTag(tag: Tag) {
        viewModelScope.launch {
            repository.updateTag(tag)
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }

    class Factory(
        private val repository: TaskRepository,
        private val reminderScheduler: ReminderScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TaskViewModel(repository, reminderScheduler) as T
        }
    }
}
