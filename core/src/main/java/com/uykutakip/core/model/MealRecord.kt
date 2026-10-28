package com.uykutakip.core.model

data class MealRecord(
    val id: Long,
    val type: MealType,
    val startMillis: Long,
    val endMillis: Long? = null,
    val items: List<FoodEntry> = emptyList(),
    val isQuickRecord: Boolean = false,
) {
    val durationMillis: Long
        get() = ((endMillis ?: startMillis) - startMillis).coerceAtLeast(0L)
}

