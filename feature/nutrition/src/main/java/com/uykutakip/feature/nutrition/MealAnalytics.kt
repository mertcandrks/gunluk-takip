package com.uykutakip.feature.nutrition

import com.uykutakip.core.model.MealRecord
import com.uykutakip.core.model.MealType
import com.uykutakip.core.time.TimeFormatter

data class DailyMealSummary(
    val mainMealCount: Int,
    val snackCount: Int,
    val latestGapMillis: Long?,
)

class MealAnalytics(private val timeFormatter: TimeFormatter) {
    fun todaySummary(records: List<MealRecord>): DailyMealSummary {
        val todayRecords = records.filter { timeFormatter.localDate(it.startMillis) == timeFormatter.today() }
        val eatingRecords = todayRecords.filter { it.type.isEating }
        val gap = if (eatingRecords.size >= 2) {
            (eatingRecords[0].startMillis - (eatingRecords[1].endMillis ?: eatingRecords[1].startMillis)).coerceAtLeast(0L)
        } else null
        return DailyMealSummary(
            mainMealCount = todayRecords.count { it.type.isMainMeal },
            snackCount = todayRecords.count { it.type == MealType.SNACK },
            latestGapMillis = gap,
        )
    }

    fun dailySequenceNumber(record: MealRecord, records: List<MealRecord>, index: Int): Int {
        val date = timeFormatter.localDate(record.startMillis)
        return records.drop(index).count {
            it.type == record.type && timeFormatter.localDate(it.startMillis) == date
        }.coerceAtLeast(1)
    }

    fun previousEatingRecord(records: List<MealRecord>, index: Int): MealRecord? {
        return records.drop(index + 1).firstOrNull { it.type.isEating }
    }
}

