package com.uykutakip.core.model

data class Idea(
    val id: Long = 0,
    val text: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val completedAtMillis: Long? = null,
)
