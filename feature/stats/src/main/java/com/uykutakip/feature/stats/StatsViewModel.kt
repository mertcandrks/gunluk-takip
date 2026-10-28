package com.uykutakip.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uykutakip.core.model.SleepRecord
import com.uykutakip.data.*
import kotlinx.coroutines.flow.*

data class StatsState(
    val sleep: List<SleepRecord> = emptyList(),
    val sleepGoalMinutes: Int = 8 * 60,
)

class StatsViewModel(
    sleep: SleepRepository,
    settings: TrackerSettingsRepository,
) : ViewModel() {
    val state = combine(sleep.records, settings.settings) { records, config -> StatsState(records, config.goalMinutes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())
}

class StatsViewModelFactory(
    private val sleep: SleepRepository,
    private val settings: TrackerSettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = StatsViewModel(sleep, settings) as T
}
