package com.uykutakip.feature.settings

import com.uykutakip.core.model.PrivateActivityRecord
import com.uykutakip.core.model.SleepRecord
import com.uykutakip.core.model.WaterIntake
import org.junit.Assert.*
import org.junit.Test

class SettingsExportTest {
    @Test fun exportsEntireHistoryAndPreservesExistingFormats() {
        val sleep = List(53) { SleepRecord(1_000L + it * 100_000L, 61_000L + it * 100_000L, it.toLong()) }
        val water = listOf(WaterIntake(amountMl = 125, timestampMillis = 123))
        val privateRecords = listOf(PrivateActivityRecord(startMillis = 100, endMillis = 60100), PrivateActivityRecord(startMillis = 200))
        val csv = exportCsv(sleep, water, privateRecords)
        assertEquals(53, csv.lineSequence().count { it.startsWith("sleep,") })
        assertTrue(csv.contains("sleep,1000,1000,61000,1"))
        assertTrue(csv.contains("water,123,,,125"))
        assertFalse(csv.contains("private,200,"))
        val json = exportJson(sleep, water, privateRecords)
        assertTrue(json.contains("{\"startMillis\":1000,\"endMillis\":61000}"))
        assertTrue(json.contains("{\"startMillis\":200,\"endMillis\":null}"))
        assertEquals("{\"sleep\":[],\"water\":[],\"private\":[]}", exportJson(emptyList(), emptyList(), emptyList()))
    }
}
