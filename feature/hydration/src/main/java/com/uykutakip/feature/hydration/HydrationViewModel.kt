package com.uykutakip.feature.hydration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uykutakip.core.model.WaterIntake
import com.uykutakip.data.HydrationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HydrationUiState(
    val records: List<WaterIntake> = emptyList(),
    val dailyGoalMl: Int = 2_500,
)

class HydrationViewModel(
    private val repository: HydrationRepository,
) : ViewModel() {
    val uiState: StateFlow<HydrationUiState> = combine(
        repository.records,
        repository.dailyGoalMl,
        ::HydrationUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HydrationUiState(),
    )

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun add(amountMl: Int) = execute {
        if (repository.add(amountMl) != null) "$amountMl ml su eklendi." else "Miktar 10-5000 ml arasında olmalı."
    }

    fun updateGoal(goalMl: Int) = execute {
        if (repository.updateGoal(goalMl)) "Günlük hedef güncellendi." else "Hedef 500-10000 ml arasında olmalı."
    }

    fun delete(id: Long) = execute {
        repository.delete(id)
        "Su kaydı silindi."
    }

    fun clearRecords() = execute {
        repository.clearRecords()
        "Su geçmişi temizlendi."
    }

    private fun execute(block: suspend () -> String) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { messageChannel.send(it) }
                .onFailure { messageChannel.send("İşlem tamamlanamadı. Tekrar dene.") }
        }
    }
}

class HydrationViewModelFactory(
    private val repository: HydrationRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HydrationViewModel(repository) as T
}
