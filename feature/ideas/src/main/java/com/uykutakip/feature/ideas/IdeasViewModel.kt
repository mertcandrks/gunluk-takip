package com.uykutakip.feature.ideas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uykutakip.core.model.Idea
import com.uykutakip.data.IdeasRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class IdeasState(val ideas: List<Idea> = emptyList(), val loading: Boolean = true, val saving: Boolean = false)

class IdeasViewModel(private val repository: IdeasRepository) : ViewModel() {
    private val saving = MutableStateFlow(false)
    val state = combine(repository.ideas, saving) { ideas, busy -> IdeasState(ideas, false, busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IdeasState())
    private val channel = Channel<String>(Channel.BUFFERED)
    val messages = channel.receiveAsFlow()
    fun save(id: Long, text: String, done: () -> Unit) = execute { repository.save(id, text); done() }
    fun delete(id: Long, done: () -> Unit) = execute { repository.delete(id); done() }
    fun finish(id: Long, done: () -> Unit) = execute { repository.finish(id); done() }
    private fun execute(block: suspend () -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { channel.send("Fikir kaydedilemedi. Tekrar dene.") }
            finally { saving.value = false }
        }
    }
}

class IdeasViewModelFactory(private val repository: IdeasRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = IdeasViewModel(repository) as T
}
