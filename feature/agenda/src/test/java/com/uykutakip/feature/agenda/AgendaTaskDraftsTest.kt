package com.uykutakip.feature.agenda

import com.uykutakip.data.AgendaTaskSuggestion
import org.junit.Assert.*
import org.junit.Test

class AgendaTaskDraftsTest {
    @Test fun batchPreservesSeparateLinesAndInfersCategories() {
        val previous = listOf(AgendaTaskSuggestion("Diş fırçala", "Sağlık", "Bakım"))
        val tasks = taskLines("  DİŞ   FIRÇALA \n\n Yeni iş ", previous)
        assertEquals(2, tasks.size)
        assertEquals("Sağlık", tasks[0].category)
        assertEquals("Bakım", tasks[0].subcategory)
        assertEquals("Yeni iş", tasks[1].text)
        assertEquals("", tasks[1].category)
    }

    @Test fun ambiguousPartialTextDoesNotAssignCategory() {
        assertNull(matchingTask("Kitap", listOf(AgendaTaskSuggestion("Kitap oku", "Kişisel", "Okuma"))))
        assertTrue(taskLines("\n  \n", emptyList()).isEmpty())
    }
}
