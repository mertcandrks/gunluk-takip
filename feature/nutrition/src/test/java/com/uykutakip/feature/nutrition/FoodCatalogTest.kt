package com.uykutakip.feature.nutrition

import com.uykutakip.core.model.FoodCategory
import com.uykutakip.core.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodCatalogTest {
    @Test
    fun catalogHasBroadCategoryCoverageAndUniqueNames() {
        assertTrue(FoodCatalog.items.size >= 120)
        assertTrue(FoodCatalog.items.map { it.category }.toSet().containsAll(FoodCategory.entries - FoodCategory.OTHER))
        assertEquals(FoodCatalog.items.size, FoodCatalog.items.map { it.name }.distinct().size)
    }

    @Test
    fun mealSuggestionsMatchTheMealContext() {
        assertTrue(FoodCatalog.suggestions(MealType.BREAKFAST).all {
            it.category in setOf(FoodCategory.BREAKFAST, FoodCategory.BAKERY, FoodCategory.DRINK)
        })
        assertTrue(FoodCatalog.suggestions(MealType.DRINK).all { it.category == FoodCategory.DRINK })
    }
}
