package com.openlight.cal.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.model.Person
import com.openlight.cal.data.model.Task
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// Task ViewModel
// ─────────────────────────────────────────────────────────────
enum class TaskTypeFilter(val label: String) { ALL("All"), TASKS("Tasks"), CHORES("Chores") }

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = (app as HearthboardApp).taskRepository
    private val personR = (app as HearthboardApp).personRepository

    val activeTasks: StateFlow<List<Task>> = repo.getActiveFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<Task>> = repo.getAllTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val people: StateFlow<List<Person>> = personR.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPersonFilter = MutableStateFlow(0L) // 0 = all
    val selectedPersonFilter: StateFlow<Long> = _selectedPersonFilter

    private val _selectedTypeFilter = MutableStateFlow(TaskTypeFilter.ALL)
    val selectedTypeFilter: StateFlow<TaskTypeFilter> = _selectedTypeFilter

    val filteredTasks: StateFlow<List<Task>> = combine(
        allTasks, _selectedPersonFilter, _selectedTypeFilter
    ) { tasks, personId, typeFilter ->
        val byPerson = if (personId == 0L) tasks
                       else tasks.filter { it.assignedPersonId == personId }
        when (typeFilter) {
            TaskTypeFilter.ALL    -> byPerson
            TaskTypeFilter.TASKS  -> byPerson.filter { !it.isChore }
            TaskTypeFilter.CHORES -> byPerson.filter { it.isChore }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPersonFilter(personId: Long) { _selectedPersonFilter.value = personId }
    fun setTypeFilter(filter: TaskTypeFilter) { _selectedTypeFilter.value = filter }

    fun saveTask(task: Task, accountId: Long? = null) {
        viewModelScope.launch { repo.saveTask(task, accountId) }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch { repo.setCompleted(task.id, !task.isCompleted) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { repo.deleteTask(task) }
    }

    /** Push due time forward by 1 hour. No-op if task has no dueMs. */
    fun snoozeTask(task: Task) {
        viewModelScope.launch {
            val newDue = (task.dueMs ?: System.currentTimeMillis()) + 3_600_000L
            repo.saveTask(task.copy(dueMs = newDue))
        }
    }

    /** Push due date forward by 1 day. No-op if task has no dueMs. */
    fun postponeTask(task: Task) {
        viewModelScope.launch {
            val newDue = (task.dueMs ?: System.currentTimeMillis()) + 86_400_000L
            repo.saveTask(task.copy(dueMs = newDue))
        }
    }
}
