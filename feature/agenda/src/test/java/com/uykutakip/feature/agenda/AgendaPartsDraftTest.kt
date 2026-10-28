package com.uykutakip.feature.agenda

import com.uykutakip.core.model.AgendaPart
import org.junit.Assert.*
import org.junit.Test

class AgendaPartsDraftTest {
    @Test fun savesPendingTitleAndPreservesExistingProgress() {
        val existing = listOf(AgendaPart("first", "First", 100))
        val saved = appendPendingPart(existing, "  Second  ")
        assertEquals(existing.single(), saved.first())
        assertEquals("Second", saved.last().title)
        assertEquals(0, saved.last().progress)
        assertEquals(1, existing.size)
        assertSame(saved, appendPendingPart(saved, "  "))
        assertEquals(1, appendPendingPart(emptyList(), "First").size)
    }

    @Test fun enforcesLimitsWithoutDroppingTheDraft() {
        val full = List(100) { AgendaPart("$it", "Step") }
        assertSame(full, appendPendingPart(full, ""))
        for ((parts, title) in listOf(full to "Extra", emptyList<AgendaPart>() to "x".repeat(201))) {
            try {
                appendPendingPart(parts, title)
                fail("Invalid draft accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
