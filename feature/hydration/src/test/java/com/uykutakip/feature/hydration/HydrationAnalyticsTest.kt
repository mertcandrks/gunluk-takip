package com.uykutakip.feature.hydration

import com.uykutakip.core.model.WaterIntake
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HydrationAnalyticsTest {
    private val zone = ZoneId.of("Europe/Istanbul")
    private val analytics = HydrationAnalytics(zone)

    @Test
    fun todaySummaryCountsOnlyTodaysRecords() {
        val today = LocalDate.of(2026, 8, 16)
        val records = listOf(
            WaterIntake(1, 250, today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()),
            WaterIntake(2, 500, today.atTime(13, 0).atZone(zone).toInstant().toEpochMilli()),
            WaterIntake(3, 330, today.minusDays(1).atTime(22, 0).atZone(zone).toInstant().toEpochMilli()),
        )

        val dayRecords = analytics.recordsForDay(records, today)
        val total = dayRecords.sumOf { it.amountMl }

        assertEquals(2, dayRecords.size)
        assertEquals(750, total)
    }

    @Test
    fun summaryClampsProgressAndRemainingAtGoal() {
        val today = LocalDate.now(zone)
        val timestamp = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val summary = analytics.todaySummary(listOf(WaterIntake(1, 3_000, timestamp)), 2_500)

        assertEquals(3_000, summary.totalMl)
        assertEquals(0, summary.remainingMl)
        assertEquals(1f, summary.progress)
    }
}
