package com.uykutakip.data

import com.uykutakip.data.local.*
import java.time.LocalDate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AgendaRepositoryTest {
    @Test fun timerSurvivesRepositoryRecreationAndCompletionStopsIt() = runBlocking {
        val dao = MemoryAgendaDao()
        val day = LocalDate.of(2026, 10, 24)
        val repo = OfflineAgendaRepository(dao)
        repo.add(day, "Work", true)
        val id = repo.observe(day).first().single().id
        dao.setTimer(id, true, 1000)
        dao.setTimer(id, true, 2000)
        assertEquals(1000L, OfflineAgendaRepository(dao).observe(day).first().single().timerStartedAtMillis)
        dao.setTimer(id, false, 6000)
        assertEquals(5000L, repo.observe(day).first().single().elapsedMillis)
        dao.setTimer(id, true, 7000)
        dao.finishTracked(id, true, day.toString(), 10000)
        val finished = repo.observe(day).first().single()
        assertTrue(finished.completed)
        assertEquals(8000L, finished.elapsedMillis)
        assertEquals(0L, finished.timerStartedAtMillis)
        assertEquals(8000L, agendaElapsed(requireNotNull(dao.find(id)), 20000))
    }

    @Test fun priorityClampsAndSurvivesObservation() = runBlocking {
        val dao = MemoryAgendaDao()
        val repo = OfflineAgendaRepository(dao)
        val day = LocalDate.of(2026, 10, 24)
        repo.add(day, "Prioritize me", true)
        val id = repo.observe(day).first().single().id
        repo.setPriority(id, 1)
        assertEquals(1, repo.observe(day).first().single().priority)
        repo.setPriority(id, -1)
        assertEquals(-1, repo.observe(day).first().single().priority)
        try { repo.setPriority(id, 2); fail("Out-of-range priority accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun batchValidationDoesNotSavePartialTasks() = runBlocking {
        val dao = MemoryAgendaDao()
        val repo = OfflineAgendaRepository(dao)
        val day = LocalDate.of(2026, 10, 24)
        try {
            repo.addTasks(day, listOf(AgendaTaskSuggestion("Valid", "", ""), AgendaTaskSuggestion(" ", "", "")))
            fail("Invalid batch accepted")
        } catch (_: IllegalArgumentException) { }
        assertTrue(repo.observe(day).first().isEmpty())
        repo.addTasks(day, listOf(AgendaTaskSuggestion("One", "Home", "Cleaning"), AgendaTaskSuggestion("Two", "", "")))
        assertEquals(2, repo.observe(day).first().size)
        assertEquals("Cleaning", repo.observe(day).first().first().subcategory)
    }

    @Test fun mergeKeepsOriginalTitlesInCatalogAndOnlyRemovesSourceRecord() = runBlocking {
        val dao = MemoryAgendaDao()
        val repo = OfflineAgendaRepository(dao)
        val day = LocalDate.of(2026, 10, 24)
        repo.addTasks(day, listOf(AgendaTaskSuggestion("First", "Home", "Cleaning"), AgendaTaskSuggestion("Second", "Personal", "Reading")))
        val tasks = repo.observe(day).first()
        repo.mergeTasks(tasks[0].id, tasks[1].id)
        val merged = repo.observe(day).first().single()
        assertEquals(tasks[1].id, merged.id)
        assertTrue(merged.text.contains("First"))
        assertTrue(merged.text.contains("Second"))
        val catalog = repo.observeTaskCatalog().first()
        assertTrue(catalog.templates.any { it.text == "First" && it.subcategory == "Cleaning" })
        assertTrue(catalog.templates.any { it.text == "Second" })
    }

    @Test fun separatesDaysAndPersistsEditsAndCompletion() = runBlocking {
        val dao = MemoryAgendaDao()
        val repo = OfflineAgendaRepository(dao)
        val day = LocalDate.of(2026, 9, 5)
        repo.add(day, "  Walk  ", true)
        repo.add(day.plusDays(1), "Note", false)
        val entry = repo.observe(day).first().single()
        assertEquals("Walk", entry.text)
        repo.complete(entry.id, true, day)
        repo.edit(entry.id, "Read")
        assertTrue(repo.observe(day).first().single().completed)
        assertEquals("Read", repo.observe(day).first().single().text)
        repo.delete(entry.id)
        assertTrue(repo.observe(day).first().isEmpty())
        assertEquals(1, repo.observe(day.plusDays(1)).first().size)
    }

    @Test fun rejectsBlankEntry() = runBlocking {
        val repo = OfflineAgendaRepository(MemoryAgendaDao())
        try {
            repo.add(LocalDate.of(2026, 9, 5), "  ", true)
            fail("Blank entry accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun insertsAlreadyCompletedTask() = runBlocking {
        val repo = OfflineAgendaRepository(MemoryAgendaDao())
        val day = LocalDate.of(2026, 9, 6)
        repo.add(day, "Walked", true, completed = true)
        assertTrue(repo.observe(day).first().single().completed)
    }

    @Test fun persistsNotDoneStatusAndCanClearIt() = runBlocking {
        val day = LocalDate.of(2026, 9, 9)
        val repo = OfflineAgendaRepository(MemoryAgendaDao())
        repo.add(day, "Test görevi", true)
        val entry = repo.observe(day).first().single()

        repo.markNotDone(entry.id, true)
        assertTrue(repo.observe(day).first().single().notDone)
        assertFalse(repo.observe(day).first().single().completed)

        repo.markNotDone(entry.id, false)
        assertFalse(repo.observe(day).first().single().notDone)
    }

    @Test fun generalGoalAppearsEveryDayUntilCompleted() = runBlocking {
        val createdDay = LocalDate.of(2026, 9, 19)
        val anotherDay = createdDay.plusDays(12)
        val repo = OfflineAgendaRepository(MemoryAgendaDao())
        repo.add(createdDay, "Valizi sil", isTask = true, isGeneralGoal = true)

        val goal = repo.observe(anotherDay).first().single()
        assertTrue(goal.isGeneralGoal)

        repo.complete(goal.id, true, anotherDay)
        assertTrue(repo.observe(createdDay).first().isEmpty())
        assertTrue(repo.observe(anotherDay).first().isEmpty())
    }

    @Test fun promotesDailyTaskAndPostponesOneOffTask() = runBlocking {
        val today = LocalDate.of(2026, 9, 22)
        val tomorrow = today.plusDays(1)
        val repo = OfflineAgendaRepository(MemoryAgendaDao())
        repo.add(today, "Valizi sil", isTask = true)
        repo.add(today, "Market alışverişi", isTask = true)
        val entries = repo.observe(today).first()

        repo.promoteToGeneralGoal(entries.first { it.text == "Valizi sil" }.id)
        repo.postponeToDay(entries.first { it.text == "Market alışverişi" }.id, tomorrow)

        val generalGoal = repo.observe(today).first().first { it.text == "Valizi sil" && it.isGeneralGoal }
        assertTrue(generalGoal.isGeneralGoal)
        assertFalse(repo.observe(today).first().any { it.text == "Market alışverişi" })
        assertTrue(repo.observe(tomorrow).first().any { it.text == "Market alışverişi" && !it.isGeneralGoal })

        repo.moveGeneralGoalToDay(generalGoal.id, tomorrow)
        assertTrue(repo.observe(tomorrow).first().any { it.text == "Valizi sil" && !it.isGeneralGoal })
    }

    @Test fun taskCatalogSupportsTemplatesFavoritesAndCategories() = runBlocking {
        val repo = OfflineAgendaRepository(MemoryAgendaDao())
        repo.saveTaskTemplate("Dişçiye git", "Sağlık", "Randevu", favorite = true)

        val template = repo.observeTaskCatalog().first().templates.first { it.text == "Dişçiye git" }
        assertTrue(template.favorite)
        assertEquals("Randevu", template.subcategory)

        repo.setTaskTemplateFavorite(template.id, false)
        assertFalse(repo.observeTaskCatalog().first().templates.first { it.id == template.id }.favorite)

        val category = repo.observeTaskCatalog().first().categories.first { it.category == "Sağlık" && it.subcategory == "Randevu" }
        repo.removeCategory(category.id)
        assertTrue(repo.observeTaskCatalog().first().categories.none { it.id == category.id })

        repo.removeTaskTemplate(template.id)
        assertTrue(repo.observeTaskCatalog().first().templates.none { it.id == template.id })

        repo.add(LocalDate.of(2026, 9, 20), "Eski görev", true, category = "Ev", subcategory = "Düzen")
        assertTrue(repo.observeTaskSuggestions().first().any { it.text == "Eski görev" })
        repo.hideTaskSuggestion("Eski görev", "Ev", "Düzen")
        assertTrue(repo.observeTaskSuggestions().first().none { it.text == "Eski görev" })
    }
}

private class MemoryAgendaDao : AgendaDao {
    override suspend fun find(id: Long) = rows.value.find { it.id == id }
    private val rows = MutableStateFlow<List<AgendaEntity>>(emptyList())
    private val templates = MutableStateFlow<List<AgendaTaskTemplateEntity>>(emptyList())
    private val categories = MutableStateFlow<List<AgendaCategoryEntity>>(emptyList())
    private val hiddenSuggestions = MutableStateFlow<Set<String>>(emptySet())
    private var nextId = 1L
    private var nextCategoryId = 1L
    override fun observe(day: String) = rows.map {
        it.filter { row ->
            (!row.isGeneralGoal && (row.day == day || row.repeatDaily)) ||
                (row.isGeneralGoal && !row.completed)
        }
    }
    override fun observeTaskSuggestions() = combine(rows, hiddenSuggestions) { entries, hidden ->
        entries.filter { it.isTask && it.text.isNotBlank() }
            .filterNot { suggestionKey(it.text, it.category, it.subcategory) in hidden }
            .map { AgendaTaskSuggestion(it.text, it.category, it.subcategory) }
            .distinct()
    }
    override fun observeTaskTemplates() = templates.map { entries ->
        entries.filterNot { it.hidden }.sortedWith(compareByDescending<AgendaTaskTemplateEntity> { it.favorite }.thenByDescending { it.updatedAtMillis })
            .map { AgendaTaskTemplate(it.id, it.text, it.category, it.subcategory, it.favorite, it.builtIn) }
    }
    override fun observeCategories() = categories.map { entries ->
        entries.filterNot { it.hidden }.sortedWith(compareBy<AgendaCategoryEntity> { it.category }.thenBy { it.subcategory })
            .map { AgendaCategoryOption(it.id, it.category, it.subcategory) }
    }
    override suspend fun snapshotRecords(): List<AgendaEntity> = rows.value
    override suspend fun snapshotTemplates(): List<AgendaTaskTemplateEntity> = templates.value
    override suspend fun snapshotCategories(): List<AgendaCategoryEntity> = categories.value
    override suspend fun snapshotHiddenSuggestions(): List<AgendaHiddenTaskSuggestionEntity> = hiddenSuggestions.value.map { AgendaHiddenTaskSuggestionEntity(it) }
    override suspend fun insert(entry: AgendaEntity) {
        val id = if (entry.id == 0L) nextId++ else entry.id
        rows.value = rows.value.filterNot { it.id == id } + entry.copy(id = id)
    }
    override suspend fun recordMergeDeletion(entry: SyncDeletionEntity) { }
    override suspend fun insertTemplates(entries: List<AgendaTaskTemplateEntity>) {
        val ids = templates.value.map { it.id }.toSet()
        templates.value += entries.filter { it.id !in ids }
    }
    override suspend fun insertCategory(entry: AgendaCategoryEntity) {
        if (categories.value.none { it.category == entry.category && it.subcategory == entry.subcategory }) {
            val id = if (entry.id == 0L) nextCategoryId++ else entry.id
            categories.value += entry.copy(id = id)
        }
    }
    override suspend fun upsertCategory(entry: AgendaCategoryEntity) {
        categories.value = categories.value.filterNot { it.id == entry.id || it.category == entry.category && it.subcategory == entry.subcategory } + entry
    }
    override suspend fun upsertTemplate(entry: AgendaTaskTemplateEntity) {
        templates.value = templates.value.filterNot { it.id == entry.id } + entry
    }
    override suspend fun setTemplateFavorite(id: String, favorite: Boolean, updatedAtMillis: Long) {
        templates.value = templates.value.map { if (it.id == id) it.copy(favorite = favorite, updatedAtMillis = updatedAtMillis) else it }
    }
    override suspend fun hideBuiltInTemplate(id: String, updatedAtMillis: Long): Int {
        val found = templates.value.firstOrNull { it.id == id && it.builtIn } ?: return 0
        templates.value = templates.value.map { if (it.id == id) found.copy(hidden = true, updatedAtMillis = updatedAtMillis) else it }
        return 1
    }
    override suspend fun hideTemplate(id: String, updatedAtMillis: Long) {
        templates.value = templates.value.map { if (it.id == id) it.copy(hidden = true, updatedAtMillis = updatedAtMillis) else it }
    }
    override suspend fun hideCategory(id: Long, updatedAtMillis: Long) { categories.value = categories.value.map { if (it.id == id) it.copy(hidden = true, updatedAtMillis = updatedAtMillis) else it } }
    override suspend fun restoreCategory(category: String, subcategory: String, updatedAtMillis: Long): Int {
        if (categories.value.none { it.category == category && it.subcategory == subcategory }) return 0
        categories.value = categories.value.map { if (it.category == category && it.subcategory == subcategory) it.copy(hidden = false, updatedAtMillis = updatedAtMillis) else it }
        return 1
    }
    override suspend fun hideTaskSuggestion(entry: AgendaHiddenTaskSuggestionEntity) {
        hiddenSuggestions.value += entry.suggestionKey
    }
    override suspend fun upsertHiddenSuggestion(entry: AgendaHiddenTaskSuggestionEntity) { hideTaskSuggestion(entry) }
    override suspend fun edit(id: Long, text: String, category: String, subcategory: String, updatedAtMillis: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(text = text, category = category, subcategory = subcategory, updatedAtMillis = updatedAtMillis) else it }
    }
    override suspend fun saveParts(id: Long, partsJson: String, completed: Boolean, updatedAtMillis: Long) {
        rows.value = rows.value.map { if (it.id == id && it.isTask) it.copy(projectPartsJson = partsJson, completed = completed, notDone = false, updatedAtMillis = updatedAtMillis) else it }
    }
    override suspend fun complete(id: Long, completed: Boolean, day: String, updatedAtMillis: Long) { rows.value = rows.value.map { if (it.id == id && it.isTask) it.copy(completed = completed, updatedAtMillis = updatedAtMillis) else it } }
    override suspend fun markNotDone(id: Long, notDone: Boolean, updatedAtMillis: Long) { rows.value = rows.value.map { if (it.id == id && it.isTask) it.copy(notDone = notDone, completed = false, updatedAtMillis = updatedAtMillis) else it } }
    override suspend fun setPriority(id: Long, priority: Int, updatedAtMillis: Long) {
        rows.value = rows.value.map {
            if (it.id == id && it.isTask && !it.isGeneralGoal && !it.repeatDaily) it.copy(priority = priority, updatedAtMillis = updatedAtMillis) else it
        }
    }
    override suspend fun promoteToGeneralGoal(id: Long, updatedAtMillis: Long) {
        rows.value = rows.value.map {
            if (it.id == id && it.isTask && !it.isGeneralGoal) it.copy(isGeneralGoal = true, repeatDaily = false, notifyEnabled = false, completed = false, notDone = false, updatedAtMillis = updatedAtMillis) else it
        }
    }
    override suspend fun moveGeneralGoalToDay(id: Long, day: String, updatedAtMillis: Long) {
        rows.value = rows.value.map {
            if (it.id == id && it.isTask && it.isGeneralGoal) it.copy(isGeneralGoal = false, day = day, repeatDaily = false, updatedAtMillis = updatedAtMillis) else it
        }
    }
    override suspend fun postponeToDay(id: Long, day: String, updatedAtMillis: Long) {
        rows.value = rows.value.map {
            if (it.id == id && it.isTask && !it.isGeneralGoal && !it.repeatDaily) it.copy(day = day, completed = false, notDone = false, updatedAtMillis = updatedAtMillis) else it
        }
    }
    override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }

    private fun suggestionKey(text: String, category: String, subcategory: String): String =
        listOf(text, category, subcategory).joinToString("\u001f") { it.trim() }
}
