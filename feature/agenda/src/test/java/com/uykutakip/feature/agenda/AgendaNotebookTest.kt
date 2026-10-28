package com.uykutakip.feature.agenda

import java.time.LocalDate
import com.uykutakip.core.model.AgendaEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaNotebookTest {
    @Test fun recurringEntriesUsePageDateWithoutAppearingBeforeCreation() {
        val recurring = AgendaEntry(id = 1, day = "2026-10-27", text = "Daily", isTask = true, repeatDaily = true, dailyCount = 2)
        val goal = AgendaEntry(id = 2, day = "2026-10-27", text = "Goal", isTask = true, isGeneralGoal = true)
        val note = AgendaEntry(id = 3, day = "2026-10-28", text = "Note", isTask = false)
        assertEquals(emptyList<AgendaEntry>(), notebookDay(LocalDate.parse("2026-10-26"), listOf(recurring, goal)).entries)
        val result = notebookDay(LocalDate.parse("2026-10-28"), listOf(recurring, goal, note)).entries
        assertEquals(listOf(1L, 3L), result.map { it.id })
        assertEquals("2026-10-28", result.first().day)
        assertEquals(2, result.first().dailyCount)
        assertEquals(note, result.last())
        assertEquals("2026-10-27", recurring.day)
    }

    @Test fun weekContainsSelectedDayAndCrossesYearBoundary() {
        val dates = notebookDates(LocalDate.parse("2027-01-01"))
        assertEquals(LocalDate.parse("2026-12-28"), dates.first())
        assertEquals(LocalDate.parse("2027-01-03"), dates.last())
        assertEquals(7, dates.distinct().size)
    }

    @Test fun sundayAndMondayUseTheirOwnWeeks() {
        assertEquals(LocalDate.parse("2026-10-26"), notebookDates(LocalDate.parse("2026-11-01")).first())
        assertEquals(LocalDate.parse("2026-11-02"), notebookDates(LocalDate.parse("2026-11-02")).first())
    }
}
