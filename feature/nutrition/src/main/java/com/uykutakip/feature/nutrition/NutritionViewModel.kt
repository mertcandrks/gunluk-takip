package com.uykutakip.feature.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uykutakip.core.model.FoodEntry
import com.uykutakip.core.model.MealRecord
import com.uykutakip.core.model.MealType
import com.uykutakip.data.MealRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NutritionUiState(
    val activeMeal: MealRecord? = null,
    val records: List<MealRecord> = emptyList(),
)

class NutritionViewModel(
    private val repository: MealRepository,
) : ViewModel() {
    val uiState: StateFlow<NutritionUiState> = combine(
        repository.activeMeal,
        repository.records,
        ::NutritionUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = NutritionUiState(),
    )

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun start(type: MealType) = execute {
        if (repository.start(type)) "${type.label} başladı." else "Önce devam eden öğünü bitir."
    }

    fun addItem(item: FoodEntry) = execute {
        if (repository.addItem(item)) "${item.name} eklendi." else "Önce bir öğün başlat."
    }

    fun removeItem(index: Int) = execute {
        if (repository.removeItem(index)) "Yiyecek kaldırıldı." else "Yiyecek kaldırılamadı."
    }

    fun finish() = execute {
        val active = uiState.value.activeMeal ?: return@execute "Önce bir öğün başlat."
        if (active.items.isEmpty()) return@execute "Öğünü bitirmeden önce ne yediğini ekle."
        repository.finish()
        "Öğün kaydedildi."
    }

    fun quickAdd(type: MealType, item: FoodEntry) = execute {
        if (repository.addQuick(type, item) != null) "${item.name} kaydedildi." else "Kayıt eklenemedi."
    }

    fun addPastMeal(type: MealType, startMillis: Long, endMillis: Long, items: List<FoodEntry>) = execute {
        val record = MealRecord(
            id = 0L,
            type = type,
            startMillis = startMillis,
            endMillis = endMillis,
            items = items,
        )
        if (repository.addRecord(record) != null) "Unutulan ${type.label.lowercase()} kaydedildi." else "Öğün kaydedilemedi."
    }

    fun delete(id: Long) = execute {
        repository.delete(id)
        "Beslenme kaydı silindi."
    }

    fun clearRecords() = execute {
        repository.clearRecords()
        "Beslenme geçmişi temizlendi."
    }

    private fun execute(block: suspend () -> String) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { messageChannel.send(it) }
                .onFailure { messageChannel.send("İşlem tamamlanamadı. Tekrar dene.") }
        }
    }
}

class NutritionViewModelFactory(
    private val repository: MealRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = NutritionViewModel(repository) as T
}
