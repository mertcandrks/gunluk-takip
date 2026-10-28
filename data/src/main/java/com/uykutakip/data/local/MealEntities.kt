package com.uykutakip.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "meal_records",
    indices = [Index("startMillis"), Index("type")],
)
internal data class MealRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val startMillis: Long,
    val endMillis: Long,
    val itemsJson: String,
    val isQuickRecord: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(tableName = "active_meal")
internal data class ActiveMealEntity(
    @PrimaryKey val singletonId: Int = 1,
    val id: Long = 0L,
    val type: String,
    val startMillis: Long,
    val itemsJson: String,
    val updatedAtMillis: Long,
)
