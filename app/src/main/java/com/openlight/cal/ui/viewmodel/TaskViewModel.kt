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

    val filteredTasks: StateFlow<List<Task>> = combine(allTasks, _selectedPersonFilter) { tasks, personId ->
        if (personId == 0L) tasks
        else tasks.filter { it.assignedPersonId == personId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPersonFilter(personId: Long) { _selectedPersonFilter.value = personId }

    fun saveTask(task: Task, accountId: Long? = null) {
        viewModelScope.launch { repo.saveTask(task, accountId) }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch { repo.setCompleted(task.id, !task.isCompleted) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { repo.deleteTask(task) }
    }
}
