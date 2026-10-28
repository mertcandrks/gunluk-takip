package com.uykutakip.core.model

data class PrivateActivityRecord(
    val id: Long = 0L,
    val startMillis: Long,
    val endMillis: Long? = null,
) {
    val isActive: Boolean get() = endMillis == null
    val durationMillis: Long get() = ((endMillis ?: System.currentTimeMillis()) - startMillis).coerceAtLeast(0L)
}
