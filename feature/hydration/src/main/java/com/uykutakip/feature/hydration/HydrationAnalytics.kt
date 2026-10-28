package com.uykutakip.feature.hydration

import com.uykutakip.core.model.WaterIntake
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HydrationSummary(
    val totalMl: Int,
    val remainingMl: Int,
    val progress: Float,
    val servingCount: Int,
)

class HydrationAnalytics(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun recordsForDay(
        records: List<WaterIntake>,
        date: LocalDate = LocalDate.now(zoneId),
    ): List<WaterIntake> = records.filter {
        Instant.ofEpochMilli(it.timestampMillis).atZone(zoneId).toLocalDate() == date
    }

    fun todaySummary(records: List<WaterIntake>, goalMl: Int): HydrationSummary {
        val today = recordsForDay(records)
        val total = today.sumOf { it.amountMl }
        return HydrationSummary(
            totalMl = total,
            remainingMl = (goalMl - total).coerceAtLeast(0),
            progress = if (goalMl > 0) (total.toFloat() / goalMl).coerceIn(0f, 1f) else 0f,
            servingCount = today.size,
        )
    }
}
