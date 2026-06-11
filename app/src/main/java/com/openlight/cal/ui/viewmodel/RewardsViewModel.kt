package com.openlight.cal.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.model.Person
import com.openlight.cal.data.model.Reward
import com.openlight.cal.data.model.RedeemedReward
import com.openlight.cal.data.repository.RewardRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// Rewards ViewModel
// ─────────────────────────────────────────────────────────────
class RewardsViewModel(app: Application) : AndroidViewModel(app) {

    private val rewardRepo = (app as HearthboardApp).rewardRepository
    private val personRepo = (app as HearthboardApp).personRepository

    val allRewards: StateFlow<List<Reward>> = rewardRepo.allRewardsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val enabledRewards: StateFlow<List<Reward>> = rewardRepo.enabledRewardsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val people: StateFlow<List<Person>> = personRepo.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<RedeemedReward>> = rewardRepo.historyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun balanceFlow(personId: Long): Flow<Int> = rewardRepo.balanceFlow(personId)

    fun saveReward(reward: Reward) = viewModelScope.launch {
        rewardRepo.saveReward(reward)
    }

    fun deleteReward(reward: Reward) = viewModelScope.launch {
        rewardRepo.deleteReward(reward)
    }

    private val _lastRedeemResult = MutableStateFlow<RewardRepository.RedeemResult?>(null)
    val lastRedeemResult: StateFlow<RewardRepository.RedeemResult?> = _lastRedeemResult.asStateFlow()

    fun redeem(rewardId: Long, personId: Long, note: String = "") = viewModelScope.launch {
        _lastRedeemResult.value = rewardRepo.redeem(rewardId, personId, note)
    }

    fun clearRedeemResult() { _lastRedeemResult.value = null }

    fun undoRedemption(redeemed: RedeemedReward) = viewModelScope.launch {
        rewardRepo.undoRedemption(redeemed)
    }

    fun giveStars(personId: Long, amount: Int) = viewModelScope.launch {
        rewardRepo.giveStars(personId, amount)
    }

    fun removeStars(personId: Long, amount: Int) = viewModelScope.launch {
        rewardRepo.removeStars(personId, amount)
    }
}
