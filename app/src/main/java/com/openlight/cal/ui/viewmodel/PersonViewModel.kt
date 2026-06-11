package com.openlight.cal.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.model.Label
import com.openlight.cal.data.model.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// Person ViewModel
// ─────────────────────────────────────────────────────────────
class PersonViewModel(app: Application) : AndroidViewModel(app) {

    private val repo       = (app as HearthboardApp).personRepository
    private val labelRepo  = (app as HearthboardApp).labelRepository
    private val taskRepo   = (app as HearthboardApp).taskRepository
    private val rewardRepo = (app as HearthboardApp).rewardRepository

    val people: StateFlow<List<Person>> = repo.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val labels: StateFlow<List<Label>> = labelRepo.labelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun savePerson(person: Person) {
        viewModelScope.launch { repo.save(person) }
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch { repo.update(person) }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch { repo.delete(person) }
    }

    // ── Label management ────────────────────────────────

    fun saveLabel(label: Label) {
        viewModelScope.launch { labelRepo.save(label) }
    }

    fun deleteLabel(label: Label) {
        viewModelScope.launch { labelRepo.delete(label) }
    }

    fun assignLabel(personId: Long, labelId: Long) {
        viewModelScope.launch { labelRepo.assignLabel(personId, labelId) }
    }

    fun unassignLabel(personId: Long, labelId: Long) {
        viewModelScope.launch { labelRepo.unassignLabel(personId, labelId) }
    }

    fun getLabelsForPersonFlow(personId: Long): Flow<List<Label>> =
        labelRepo.getLabelsForPersonFlow(personId)

    // ── Profile stats ────────────────────────────────────

    /** Number of incomplete tasks assigned to this person. */
    fun getOpenTasksCountFlow(personId: Long): Flow<Int> =
        taskRepo.getByPersonFlow(personId).map { tasks ->
            tasks.count { !it.isCompleted }
        }.flowOn(Dispatchers.Default)

    /** Net star balance (earned − redeemed) for this person. */
    fun getBalanceFlow(personId: Long): Flow<Int> =
        rewardRepo.balanceFlow(personId)

    /** All tasks for this person (for profile listing). */
    fun getTasksForPersonFlow(personId: Long): Flow<List<com.openlight.cal.data.model.Task>> =
        taskRepo.getByPersonFlow(personId)
}
