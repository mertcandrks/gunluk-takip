package com.uykutakip.data

import com.uykutakip.core.model.AgendaEntry
import com.uykutakip.core.model.AgendaPart
import com.uykutakip.data.local.AgendaDao
import com.uykutakip.data.local.AgendaCategoryEntity
import com.uykutakip.data.local.AgendaEntity
import com.uykutakip.data.local.AgendaHiddenTaskSuggestionEntity
import com.uykutakip.data.local.AgendaTaskTemplateEntity
import com.uykutakip.data.local.SyncDeletionDao
import com.uykutakip.data.local.SyncDeletionEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.UUID

interface AgendaRepository {
    fun observe(day: LocalDate): Flow<List<AgendaEntry>>
    fun observeTaskSuggestions(): Flow<List<AgendaTaskSuggestion>>
    fun observeTaskCatalog(): Flow<AgendaTaskCatalog>
    suspend fun add(day: LocalDate, text: String, isTask: Boolean, repeatDaily: Boolean = false, notifyEnabled: Boolean = false, notifyHour: Int = 20, notifyMinute: Int = 0, completed: Boolean = false, isGeneralGoal: Boolean = false, category: String = "", subcategory: String = "")
    suspend fun edit(id: Long, text: String, category: String = "", subcategory: String = "")
    suspend fun addTasks(day: LocalDate, tasks: List<AgendaTaskSuggestion>)
    suspend fun setTimer(id: Long, running: Boolean)
    suspend fun finishTask(id: Long, day: LocalDate)
    suspend fun mergeTasks(sourceId: Long, targetId: Long)
    suspend fun saveParts(id: Long, parts: List<AgendaPart>)
    suspend fun adjustDailyCount(id: Long, day: LocalDate, delta: Int)
    suspend fun complete(id: Long, completed: Boolean, day: LocalDate)
    suspend fun markNotDone(id: Long, notDone: Boolean)
    suspend fun setPriority(id: Long, priority: Int)
    suspend fun promoteToGeneralGoal(id: Long)
    suspend fun moveGeneralGoalToDay(id: Long, day: LocalDate)
    suspend fun postponeToDay(id: Long, day: LocalDate)
    suspend fun delete(id: Long)
    suspend fun saveTaskTemplate(text: String, category: String, subcategory: String, favorite: Boolean = false)
    suspend fun setTaskTemplateFavorite(id: String, favorite: Boolean)
    suspend fun removeTaskTemplate(id: String)
    suspend fun saveCategory(category: String, subcategory: String)
    suspend fun removeCategory(id: Long)
    suspend fun hideTaskSuggestion(text: String, category: String, subcategory: String)
}

internal class OfflineAgendaRepository(private val dao: AgendaDao, private val deletions: SyncDeletionDao = NoopSyncDeletionDao) : AgendaRepository {
    override fun observeTaskSuggestions() = dao.observeTaskSuggestions()
    override fun observeTaskCatalog(): Flow<AgendaTaskCatalog> = combine(
        dao.observeTaskTemplates().onStart { dao.insertTemplates(BuiltInTemplates) },
        dao.observeCategories().onStart { BuiltInTemplates.forEach { dao.insertCategory(AgendaCategoryEntity(category = it.category, subcategory = it.subcategory)) } },
    ) { templates, categories -> AgendaTaskCatalog(templates, categories) }
    override fun observe(day: LocalDate) = dao.observe(day.toString()).map { rows ->
        rows.map {
            val dailyCounts = if (it.repeatDaily) decodeDailyCounts(it.dailyCountsJson) else emptyMap()
            val dailyCount = dailyCounts[day.toString()] ?: 0
            AgendaEntry(
                id = it.id,
                day = it.day,
                text = it.text,
                isTask = it.isTask,
                completed = if (it.repeatDaily) dailyCount > 0 else it.completed,
                notDone = !it.repeatDaily && it.notDone,
                repeatDaily = it.repeatDaily,
                streak = it.streak,
                notifyEnabled = it.notifyEnabled,
                notifyHour = it.notifyHour,
                notifyMinute = it.notifyMinute,
                isGeneralGoal = it.isGeneralGoal,
                category = it.category,
                subcategory = it.subcategory,
                parts = decodeAgendaParts(it.projectPartsJson),
                dailyCount = dailyCount,
                totalCompletedDays = dailyCounts.values.count { count -> count > 0 },
                elapsedMillis = it.elapsedMillis,
                timerStartedAtMillis = it.timerStartedAtMillis,
                priority = it.priority,
            )
        }
    }
    override suspend fun add(day: LocalDate, text: String, isTask: Boolean, repeatDaily: Boolean, notifyEnabled: Boolean, notifyHour: Int, notifyMinute: Int, completed: Boolean, isGeneralGoal: Boolean, category: String, subcategory: String) {
        require(text.isNotBlank() && text.length <= 4000)
        require(!isGeneralGoal || isTask)
        val normalizedCategory = category.trim().take(60)
        val normalizedSubcategory = subcategory.trim().take(60)
        dao.insert(AgendaEntity(day = day.toString(), text = text.trim(), isTask = isTask, completed = isTask && completed, repeatDaily = repeatDaily && !isGeneralGoal, streak = 0, lastCompletedDay = if (isTask && completed) day.toString() else null, notifyEnabled = notifyEnabled && !isGeneralGoal, notifyHour = notifyHour, notifyMinute = notifyMinute, notDone = false, isGeneralGoal = isGeneralGoal, category = normalizedCategory, subcategory = normalizedSubcategory))
        saveCategory(normalizedCategory, normalizedSubcategory)
    }
    override suspend fun edit(id: Long, text: String, category: String, subcategory: String) {
        require(text.isNotBlank() && text.length <= 4000)
        val normalizedCategory = category.trim().take(60)
        val normalizedSubcategory = subcategory.trim().take(60)
        dao.edit(id, text.trim(), normalizedCategory, normalizedSubcategory, System.currentTimeMillis())
        saveCategory(normalizedCategory, normalizedSubcategory)
    }
    override suspend fun addTasks(day: LocalDate, tasks: List<AgendaTaskSuggestion>) {
        require(tasks.isNotEmpty() && tasks.size <= 100)
        val entries = tasks.map { task ->
            require(task.text.isNotBlank() && task.text.length <= 4000)
            require(task.category.length <= 60 && task.subcategory.length <= 60)
            AgendaEntity(day = day.toString(), text = task.text.trim(), isTask = true, completed = false,
                repeatDaily = false, streak = 0, lastCompletedDay = null, notifyEnabled = false,
                notifyHour = 20, notifyMinute = 0, notDone = false, isGeneralGoal = false,
                category = task.category.trim(), subcategory = task.subcategory.trim())
        }
        dao.insertBatch(entries)
    }
    override suspend fun setTimer(id: Long, running: Boolean) = dao.setTimer(id, running, System.currentTimeMillis())
    override suspend fun finishTask(id: Long, day: LocalDate) = dao.finishTask(id, day.toString(), System.currentTimeMillis())
    override suspend fun mergeTasks(sourceId: Long, targetId: Long) = dao.mergeTasks(sourceId, targetId, System.currentTimeMillis())
    override suspend fun saveParts(id: Long, parts: List<AgendaPart>) = dao.saveTrackedParts(id, encodeAgendaParts(parts), parts.isNotEmpty() && parts.all { it.progress == 100 }, System.currentTimeMillis())
    override suspend fun adjustDailyCount(id: Long, day: LocalDate, delta: Int) = dao.adjustDailyCount(id, day.toString(), delta)
    override suspend fun complete(id: Long, completed: Boolean, day: LocalDate) = dao.finishTracked(id, completed, day.toString(), System.currentTimeMillis())
    override suspend fun markNotDone(id: Long, notDone: Boolean) = dao.markTrackedNotDone(id, notDone, System.currentTimeMillis())
    override suspend fun setPriority(id: Long, priority: Int) {
        require(priority in -1..1)
        dao.setPriority(id, priority, System.currentTimeMillis())
    }
    override suspend fun promoteToGeneralGoal(id: Long) = dao.promoteToGeneralGoal(id, System.currentTimeMillis())
    override suspend fun moveGeneralGoalToDay(id: Long, day: LocalDate) = dao.moveGeneralGoalToDay(id, day.toString(), System.currentTimeMillis())
    override suspend fun postponeToDay(id: Long, day: LocalDate) = dao.postponeToDay(id, day.toString(), System.currentTimeMillis())
    override suspend fun delete(id: Long) {
        dao.delete(id)
        deletions.upsert(SyncDeletionEntity("agenda", id, System.currentTimeMillis()))
    }
    override suspend fun saveTaskTemplate(text: String, category: String, subcategory: String, favorite: Boolean) {
        require(text.isNotBlank() && text.length <= 4000)
        val normalizedCategory = category.trim().take(60)
        val normalizedSubcategory = subcategory.trim().take(60)
        dao.upsertTemplate(AgendaTaskTemplateEntity(UUID.randomUUID().toString(), text.trim(), normalizedCategory, normalizedSubcategory, favorite = favorite))
        saveCategory(normalizedCategory, normalizedSubcategory)
    }
    override suspend fun setTaskTemplateFavorite(id: String, favorite: Boolean) = dao.setTemplateFavorite(id, favorite, System.currentTimeMillis())
    override suspend fun removeTaskTemplate(id: String) = dao.removeTemplate(id)
    override suspend fun saveCategory(category: String, subcategory: String) {
        val normalizedCategory = category.trim().take(60)
        val normalizedSubcategory = subcategory.trim().take(60)
        if (normalizedCategory.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (dao.restoreCategory(normalizedCategory, normalizedSubcategory, now) == 0) {
                dao.insertCategory(AgendaCategoryEntity(category = normalizedCategory, subcategory = normalizedSubcategory, updatedAtMillis = now))
            }
        }
    }
    override suspend fun removeCategory(id: Long) = dao.hideCategory(id, System.currentTimeMillis())
    override suspend fun hideTaskSuggestion(text: String, category: String, subcategory: String) {
        dao.hideTaskSuggestion(AgendaHiddenTaskSuggestionEntity(suggestionKey(text, category, subcategory)))
    }

    private fun suggestionKey(text: String, category: String, subcategory: String): String =
        listOf(text, category, subcategory).joinToString("\u001f") { it.trim() }

    private companion object {
        val BuiltInTemplates = listOf(
            AgendaTaskTemplateEntity("builtin-walk", "Yürüyüş yap", "Sağlık", "Hareket", builtIn = true),
            AgendaTaskTemplateEntity("builtin-exercise", "Egzersiz yap", "Sağlık", "Antrenman", builtIn = true),
            AgendaTaskTemplateEntity("builtin-water", "Su hedefini tamamla", "Sağlık", "Su", builtIn = true),
            AgendaTaskTemplateEntity("builtin-read", "Kitap oku", "Kişisel", "Öğrenme", builtIn = true),
            AgendaTaskTemplateEntity("builtin-plan", "Günün planını hazırla", "Kişisel", "Planlama", builtIn = true),
            AgendaTaskTemplateEntity("builtin-shopping", "Alışveriş yap", "Ev", "Alışveriş", builtIn = true),
            AgendaTaskTemplateEntity("builtin-tidy", "Evi toparla", "Ev", "Düzen", builtIn = true),
            AgendaTaskTemplateEntity("builtin-bills", "Faturaları kontrol et", "Finans", "Ödemeler", builtIn = true),
        )
    }
}
