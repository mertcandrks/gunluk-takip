package com.uykutakip.feature.nutrition

import com.uykutakip.core.model.FoodCategory
import com.uykutakip.core.model.FoodEntry
import com.uykutakip.core.model.FoodUnit
import com.uykutakip.core.model.MealRecord
import com.uykutakip.core.model.MealType
import com.uykutakip.core.time.TimeFormatter
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealAnalyticsTest {
    private val analytics = MealAnalytics(TimeFormatter())

    @Test
    fun todaySummary_separatesMainMealsAndSnacks() {
        val now = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val records = listOf(
            MealRecord(1L, MealType.LUNCH, now - 60_000L, now - 30_000L, listOf(food("Çorba"))),
            MealRecord(2L, MealType.BREAKFAST, now - 3_600_000L, now - 3_500_000L, listOf(food("Yumurta"))),
            MealRecord(3L, MealType.SNACK, now - 7_200_000L, now - 7_200_000L, listOf(food("Meyve")), true),
        )

        val summary = analytics.todaySummary(records)

        assertEquals(2, summary.mainMealCount)
        assertEquals(1, summary.snackCount)
        assertTrue((summary.latestGapMillis ?: 0L) > 0L)
    }

    private fun food(name: String) = FoodEntry(name, 1.0, FoodUnit.PORTION, FoodCategory.OTHER)
}
