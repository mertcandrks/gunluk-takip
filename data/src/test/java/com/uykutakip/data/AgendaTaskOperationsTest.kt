package com.uykutakip.data

import com.uykutakip.core.model.AgendaPart
import com.uykutakip.data.local.AgendaEntity
import org.junit.Assert.*
import org.junit.Test

class AgendaTaskOperationsTest {
    private fun task(id: Long, text: String) = AgendaEntity(id, "2026-10-24", text, true, false,
        false, 0, null, false, 20, 0, false, false)

    @Test fun mergingKeepsTextCategoryAndSumsRunningTime() {
        val source = task(1, "Source").copy(category = "Health", subcategory = "Exercise", elapsedMillis = 2000, timerStartedAtMillis = 1000)
        val target = task(2, "Target").copy(category = "Personal", elapsedMillis = 3000, timerStartedAtMillis = 2000)
        val merged = mergeAgendaTasks(source, target, 5000)
        assertEquals(2L, merged.id)
        assertTrue(merged.text.contains("Source"))
        assertTrue(merged.text.contains("Target"))
        assertTrue(merged.text.contains("Health / Exercise"))
        assertEquals("Personal", merged.category)
        assertEquals(12000L, merged.elapsedMillis)
        assertEquals(0L, merged.timerStartedAtMillis)
    }

    @Test fun duplicatePartIdsAreReassignedWithoutLosingProgress() {
        val merged = mergeAgendaParts(listOf(AgendaPart("same", "Source part", 60)), listOf(AgendaPart("same", "Target part", 25)))
        assertEquals(2, merged.size)
        assertEquals(2, merged.map { it.id }.distinct().size)
        assertEquals(listOf(25, 60), merged.map { it.progress })
    }

    @Test fun refusesSelfMergeAndCompletedOrOversizedTasks() {
        val source = task(1, "Source")
        for (target in listOf(source, task(2, "Done").copy(completed = true), task(2, "x".repeat(4000)))) {
            try { mergeAgendaTasks(source, target, 5000); fail("Unsafe merge accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun backwardsClockDoesNotSubtractRecordedTime() {
        assertEquals(3000L, agendaElapsed(task(1, "Work").copy(elapsedMillis = 3000, timerStartedAtMillis = 5000), 4000))
    }
}
