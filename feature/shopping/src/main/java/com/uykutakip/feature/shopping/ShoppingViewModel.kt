package com.uykutakip.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uykutakip.core.model.ShoppingItem
import com.uykutakip.data.ShoppingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ShoppingState(val items: List<ShoppingItem> = emptyList(), val loading: Boolean = true, val saving: Boolean = false)

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {
    private val saving = MutableStateFlow(false)
    val state = combine(repository.items, saving) { items, busy -> ShoppingState(items, false, busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShoppingState())
    private val channel = Channel<String>(Channel.BUFFERED)
    val messages = channel.receiveAsFlow()
    fun save(id: Long, text: String, longTerm: Boolean, done: () -> Unit) = execute { repository.save(id, text, longTerm); done() }
    fun toggle(id: Long) = execute { repository.toggle(id) }
    fun delete(id: Long, done: () -> Unit) = execute { repository.delete(id); done() }
    private fun execute(block: suspend () -> Unit) {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { channel.send("Liste güncellenemedi. Tekrar dene.") }
            finally { saving.value = false }
        }
    }
}

class ShoppingViewModelFactory(private val repository: ShoppingRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ShoppingViewModel(repository) as T
}
