package com.uykutakip.data

import com.uykutakip.data.local.IdeaDao
import com.uykutakip.data.local.IdeaEntity
import com.uykutakip.data.local.AgendaEntity
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IdeasRepositoryTest {
    @Test fun savesAndEditsWithoutLosingCreationTime() = runBlocking {
        val dao = MemoryIdeaDao()
        val repo = OfflineIdeasRepository(dao)
        repo.save(0, "  A future idea\nMore detail  ")
        val idea = OfflineIdeasRepository(dao).ideas.first().single()
        assertEquals("A future idea\nMore detail", idea.text)
        repo.save(idea.id, "Changed idea")
        val edited = repo.ideas.first().single()
        assertEquals(idea.id, edited.id)
        assertEquals(idea.createdAtMillis, edited.createdAtMillis)
        assertEquals("Changed idea", edited.text)
        repo.delete(idea.id)
        assertTrue(repo.ideas.first().isEmpty())
    }

    @Test fun rejectsBlankOversizedAndMissingIdeas() = runBlocking {
        val repo = OfflineIdeasRepository(MemoryIdeaDao())
        for (text in listOf("  ", "x".repeat(20_001))) {
            try { repo.save(0, text); fail("Invalid idea accepted") } catch (_: IllegalArgumentException) { }
        }
        try { repo.save(99, "Missing"); fail("Missing idea recreated") } catch (_: IllegalStateException) { }
        assertTrue(repo.ideas.first().isEmpty())
    }

    @Test fun finishingIdeaAddsOneCompletedTaskOnCorrectDayAndKeepsOriginalDate() = runBlocking {
        val dao = MemoryIdeaDao()
        val repo = OfflineIdeasRepository(dao)
        repo.save(0, "Build feature\nimplementation notes")
        val before = repo.ideas.first().single()
        repo.finish(before.id)
        repo.finish(before.id)
        val finished = repo.ideas.first().single()
        assertEquals(before.createdAtMillis, finished.createdAtMillis)
        assertNotNull(finished.completedAtMillis)
        assertEquals(1, dao.agendaRows.size)
        val agenda = dao.agendaRows.single()
        assertEquals("Build feature", agenda.text)
        assertTrue(agenda.isTask && agenda.completed)
        assertEquals(Instant.ofEpochMilli(finished.completedAtMillis!!).atZone(ZoneId.systemDefault()).toLocalDate().toString(), agenda.day)
    }
}

private class MemoryIdeaDao : IdeaDao {
    private val rows = MutableStateFlow<List<IdeaEntity>>(emptyList())
    val agendaRows = mutableListOf<AgendaEntity>()
    private var nextId = 1L
    override fun observe() = rows
    override suspend fun snapshotRecords() = rows.value
    override suspend fun insert(idea: IdeaEntity) { rows.value += idea.copy(id = nextId++) }
    override suspend fun find(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun insertCompletedAgenda(entry: AgendaEntity) { agendaRows += entry }
    override suspend fun setCompleted(id: Long, completedAtMillis: Long): Int {
        if (rows.value.none { it.id == id && it.completedAtMillis == null }) return 0
        rows.value = rows.value.map { if (it.id == id) it.copy(completedAtMillis = completedAtMillis) else it }
        return 1
    }
    override suspend fun update(id: Long, text: String, updatedAtMillis: Long): Int {
        if (rows.value.none { it.id == id }) return 0
        rows.value = rows.value.map { if (it.id == id) it.copy(text = text, updatedAtMillis = updatedAtMillis) else it }
        return 1
    }
    override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
}
