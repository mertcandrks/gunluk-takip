package com.uykutakip.feature.agenda

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAgendaParserTest {
    private val today = LocalDate.of(2026, 9, 6)

    @Test
    fun separatesCompletedAndFutureActions() {
        val result = SmartAgendaParser.parse(
            "Bugün yürüyüş yaptım ve faturayı ödedim. Yarın saat 14:30 dişçiye gideceğim.",
            today,
        )

        assertEquals(3, result.size)
        assertTrue(result[0].completed)
        assertTrue(result[1].completed)
        assertEquals(today.plusDays(1), result[2].day)
        assertEquals(14, result[2].notifyHour)
        assertEquals(30, result[2].notifyMinute)
        assertTrue(result[2].notifyEnabled)
    }

    @Test
    fun keepsNarrativeAsNoteAndRecognizesAsciiTurkish() {
        val result = SmartAgendaParser.parse("Bugun hava güzeldi. Yarin kitap okuyacagim.", today)

        assertFalse(result[0].isTask)
        assertTrue(result[1].isTask)
        assertEquals(today.plusDays(1), result[1].day)
    }

    @Test
    fun keepsShoppingListInOneTask() {
        val result = SmartAgendaParser.parse("Akşam marketten yumurta ve süt alacağım.", today)

        assertEquals(1, result.size)
        assertEquals("Marketten yumurta ve süt alacağım", result.single().text)
        assertEquals(19, result.single().notifyHour)
    }

    @Test
    fun understandsBrokenTurkishWithoutPunctuation() {
        val result = SmartAgendaParser.parse(
            "bgun yurus yaptm yarin sat 14.30 doktora gitcem",
            today,
        )

        assertEquals(2, result.size)
        assertTrue(result[0].completed)
        assertEquals(today, result[0].day)
        assertFalse(result[1].completed)
        assertEquals(today.plusDays(1), result[1].day)
        assertEquals(14, result[1].notifyHour)
        assertEquals(30, result[1].notifyMinute)
        assertEquals("Yurus yaptm", result[0].text)
        assertEquals("Doktora gitcem", result[1].text)
    }

    @Test
    fun forcedSectionsTurnPlainListsIntoTasks() {
        val completed = SmartAgendaParser.parse("kahvaltı\nyürüyüş", today, AgendaIntent.COMPLETED)
        val planned = SmartAgendaParser.parse("ekmek ve süt\nfaturalar", today, AgendaIntent.PLANNED)

        assertTrue(completed.all { it.isTask && it.completed })
        assertTrue(planned.all { it.isTask && !it.completed })
    }

    @Test
    fun recognizesMisspelledSectionHeaders() {
        val result = SmartAgendaParser.parse(
            "Yaptklarim:\nodayı topladım\nYapcaklarim:\nmarket\nkitap",
            today,
        )

        assertEquals(3, result.size)
        assertTrue(result.first().completed)
        assertTrue(result.drop(1).all { it.isTask && !it.completed })
    }

    @Test
    fun doesNotTreatEveryGunWordAsYesterday() {
        val result = SmartAgendaParser.parse("Her gün biraz kitap okuyacağım", today)

        assertEquals(today, result.single().day)
    }
}
