package com.uykutakip.feature.private_tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uykutakip.core.model.PrivateActivityRecord
import com.uykutakip.data.PrivateActivityRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PrivateTrackingState(val records: List<PrivateActivityRecord> = emptyList(), val active: PrivateActivityRecord? = null, val now: Long = System.currentTimeMillis())

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PrivateTrackingViewModel(private val repository: PrivateActivityRepository) : ViewModel() {
    val state = repository.records.flatMapLatest { records ->
        val active = records.firstOrNull { it.isActive }
        flow {
            while (true) {
                val now = System.currentTimeMillis()
                emit(PrivateTrackingState(records, active, now))
                delay(if (active != null) 1_000L else 60_000L - now % 60_000L)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), PrivateTrackingState())
    private val messagesChannel = Channel<String>(Channel.BUFFERED)
    val messages = messagesChannel.receiveAsFlow()
    fun start() = execute { if (repository.start()) "Takip başladı." else "Zaten devam eden bir kayıt var." }
    fun finish() = execute { if (repository.finish()) "Kayıt tamamlandı." else "Devam eden kayıt bulunamadı." }
    fun add(start: Long, end: Long, done: () -> Unit) = execute { if (repository.addRecord(start, end)) { done(); "Manuel kayıt eklendi." } else "Bitiş saati başlangıçtan sonra olmalı." }
    fun update(id: Long, start: Long, end: Long, done: () -> Unit) = execute { if (repository.updateRecord(id, start, end)) { done(); "Kayıt güncellendi." } else "Bitiş saati başlangıçtan sonra olmalı." }
    fun delete(id: Long) = execute { repository.deleteRecord(id); "Kayıt silindi." }
    private fun execute(block: suspend () -> String) = viewModelScope.launch { runCatching { block() }.onSuccess { messagesChannel.send(it) }.onFailure { messagesChannel.send("İşlem tamamlanamadı. Tekrar dene.") } }
}

class PrivateTrackingViewModelFactory(private val repository: PrivateActivityRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = PrivateTrackingViewModel(repository) as T
}
