package com.uykutakip.core.model

data class ShoppingItem(
    val id: Long = 0,
    val text: String,
    val longTerm: Boolean = false,
    val purchased: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
