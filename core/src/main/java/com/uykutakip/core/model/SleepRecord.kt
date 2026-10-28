package com.uykutakip.core.model

data class SleepRecord(
    val startMillis: Long,
    val endMillis: Long,
    val id: Long = 0L,
) {
    val durationMillis: Long = (endMillis - startMillis).coerceAtLeast(0L)
}

