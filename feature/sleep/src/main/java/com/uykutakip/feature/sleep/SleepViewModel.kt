package com.uykutakip.feature.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uykutakip.core.model.SleepRecord
import com.uykutakip.data.SleepRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SleepUiState(
    val activeStartMillis: Long? = null,
    val records: List<SleepRecord> = emptyList(),
)

class SleepViewModel(
    private val repository: SleepRepository,
) : ViewModel() {
    val uiState: StateFlow<SleepUiState> = combine(
        repository.activeStartMillis,
        repository.records,
        ::SleepUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SleepUiState(),
    )

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun start() = execute {
        if (repository.start()) "İyi uykular." else "Uyku takibi zaten devam ediyor."
    }

    fun finish() = finishAt(System.currentTimeMillis())

    fun finishAt(endMillis: Long) = execute {
        repository.finish(endMillis)?.let { "Kaydedildi." } ?: "Uyanma saati başlangıçtan sonra olmalı."
    }

    fun updateActiveStart(startMillis: Long) = execute {
        if (repository.updateActiveStart(startMillis)) "Uyku başlangıcı düzeltildi." else "Başlangıç saati geçerli değil."
    }

    fun addRecord(startMillis: Long, endMillis: Long) = execute {
        if (repository.addRecord(startMillis, endMillis) != null) "Eksik uyku kaydı eklendi." else "Uyku saatleri geçerli değil."
    }

    fun updateRecord(record: SleepRecord, startMillis: Long, endMillis: Long) = execute {
        if (repository.updateRecord(record.id, startMillis, endMillis) != null) "Uyku kaydı güncellendi." else "Uyku saatleri geçerli değil."
    }

    fun deleteRecord(record: SleepRecord) = execute {
        repository.deleteRecord(record.id)
        "Uyku kaydı silindi."
    }

    fun clearRecords() = execute {
        repository.clearRecords()
        "Uyku geçmişi temizlendi."
    }

    private fun execute(block: suspend () -> String) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { messageChannel.send(it) }
                .onFailure { messageChannel.send("İşlem tamamlanamadı. Tekrar dene.") }
        }
    }
}

class SleepViewModelFactory(
    private val repository: SleepRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SleepViewModel(repository) as T
}
