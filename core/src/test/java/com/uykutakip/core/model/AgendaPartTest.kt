package com.uykutakip.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AgendaPartTest {
    @Test fun calculatesProgressFromPartsAndValidatesBounds() {
        val parts = listOf(AgendaPart("front", "Frontend", 50), AgendaPart("back", "Backend", 0))
        assertEquals(25, parts.progressPercent)
        assertEquals(100, parts.map { it.copy(progress = 100) }.progressPercent)
        assertEquals(0, emptyList<AgendaPart>().progressPercent)
        assertEquals(50, parts.take(1).progressPercent)
        assertThrows(IllegalArgumentException::class.java) { parts[0].copy(progress = 101) }
        assertThrows(IllegalArgumentException::class.java) { parts[0].copy(title = " ") }
    }
}
