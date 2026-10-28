package com.uykutakip.feature.agenda

import androidx.lifecycle.*
import com.uykutakip.core.model.AgendaEntry
import com.uykutakip.core.model.AgendaPart
import com.uykutakip.data.AgendaTaskSuggestion
import com.uykutakip.data.AgendaTaskCatalog
import com.uykutakip.data.AgendaRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AgendaState(
    val day: LocalDate,
    val entries: List<AgendaEntry> = emptyList(),
    val suggestions: List<AgendaTaskSuggestion> = emptyList(),
    val catalog: AgendaTaskCatalog = AgendaTaskCatalog(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AgendaViewModel(private val repository: AgendaRepository) : ViewModel() {
    private val day = MutableStateFlow(LocalDate.now())
    val state = day.flatMapLatest { selected ->
        combine(repository.observe(selected), repository.observeTaskSuggestions(), repository.observeTaskCatalog()) { entries, suggestions, catalog ->
            AgendaState(selected, entries, suggestions, catalog, false)
        }
            .onStart { emit(AgendaState(selected)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgendaState(day.value))
    internal val notebook = day.flatMapLatest { selected ->
        val dates = notebookDates(selected)
        combine(dates.map(repository::observe)) { days ->
            dates.mapIndexed { index, date -> notebookDay(date, days[index]) }
        }.onStart { emit(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    private val channel = Channel<String>(Channel.BUFFERED)
    val messages = channel.receiveAsFlow()
    fun select(date: LocalDate) { day.value = date }
    fun add(date: LocalDate, text: String, task: Boolean, repeatDaily: Boolean, notifyEnabled: Boolean, notifyHour: Int, notifyMinute: Int, category: String, subcategory: String, done: () -> Unit) = execute { repository.add(date, text, task, repeatDaily, notifyEnabled, notifyHour, notifyMinute, category = category, subcategory = subcategory); done() }
    fun addGeneralGoal(date: LocalDate, text: String, done: () -> Unit) = execute {
        repository.add(day = date, text = text, isTask = true, isGeneralGoal = true)
        done()
    }
    fun addAll(entries: List<AgendaDraft>, done: () -> Unit) = execute {
        entries.filter { it.selected && it.text.isNotBlank() }.forEach { entry ->
            repository.add(
                day = entry.day,
                text = entry.text,
                isTask = entry.isTask,
                notifyEnabled = entry.notifyEnabled,
                notifyHour = entry.notifyHour,
                notifyMinute = entry.notifyMinute,
                completed = entry.completed,
            )
        }
        done()
    }
    fun edit(id: Long, text: String, category: String, subcategory: String, done: () -> Unit) = execute { repository.edit(id, text, category, subcategory); done() }
    fun addTasks(tasks: List<AgendaTaskSuggestion>, done: (Boolean) -> Unit) {
        val selectedDay = day.value
        viewModelScope.launch {
            try { repository.addTasks(selectedDay, tasks); done(true) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { channel.send("Görevler kaydedilemedi. Taslak korunuyor."); done(false) }
        }
    }
    fun setTimer(entry: AgendaEntry) = execute { repository.setTimer(entry.id, entry.timerStartedAtMillis == 0L) }
    fun finishTask(entry: AgendaEntry) = execute { repository.finishTask(entry.id, day.value) }
    fun mergeTasks(sourceId: Long, targetId: Long, done: () -> Unit) = execute { repository.mergeTasks(sourceId, targetId); done() }
    fun saveParts(id: Long, parts: List<AgendaPart>, done: () -> Unit) = execute { repository.saveParts(id, parts); done() }
    fun complete(entry: AgendaEntry, day: LocalDate) = execute {
        if (entry.repeatDaily) repository.adjustDailyCount(entry.id, day, 1)
        else repository.complete(entry.id, !entry.completed, day)
    }
    fun decrement(entry: AgendaEntry, day: LocalDate) = execute { repository.adjustDailyCount(entry.id, day, -1) }
    fun markNotDone(entry: AgendaEntry) = execute { repository.markNotDone(entry.id, !entry.notDone) }
    fun adjustPriority(entry: AgendaEntry, delta: Int) = execute { repository.setPriority(entry.id, (entry.priority + delta).coerceIn(-1, 1)) }
    fun promoteToGeneralGoal(entry: AgendaEntry) = execute { repository.promoteToGeneralGoal(entry.id) }
    fun moveGeneralGoalToDay(entry: AgendaEntry, day: LocalDate) = execute { repository.moveGeneralGoalToDay(entry.id, day) }
    fun postponeToTomorrow(entry: AgendaEntry, day: LocalDate) = execute { repository.postponeToDay(entry.id, day.plusDays(1)) }
    fun delete(id: Long) = execute { repository.delete(id) }
    fun saveTaskTemplate(text: String, category: String, subcategory: String, favorite: Boolean = false) = execute {
        repository.saveTaskTemplate(text, category, subcategory, favorite)
    }
    fun setTaskTemplateFavorite(id: String, favorite: Boolean) = execute { repository.setTaskTemplateFavorite(id, favorite) }
    fun removeTaskTemplate(id: String) = execute { repository.removeTaskTemplate(id) }
    fun saveCategory(category: String, subcategory: String) = execute { repository.saveCategory(category, subcategory) }
    fun removeCategory(id: Long) = execute { repository.removeCategory(id) }
    fun hideTaskSuggestion(text: String, category: String, subcategory: String) = execute {
        repository.hideTaskSuggestion(text, category, subcategory)
    }
    private fun execute(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (error: CancellationException) { throw error }
            catch (_: Exception) { channel.send("Kayıt işlemi tamamlanamadı. Tekrar dene.") }
        }
    }
}

class AgendaViewModelFactory(private val repository: AgendaRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AgendaViewModel(repository) as T
}
