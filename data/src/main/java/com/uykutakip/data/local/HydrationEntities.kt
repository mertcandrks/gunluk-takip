package com.uykutakip.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "water_intakes",
    indices = [Index("timestampMillis")],
)
internal data class WaterIntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val amountMl: Int,
    val timestampMillis: Long,
    @androidx.room3.ColumnInfo(defaultValue = "0") val updatedAtMillis: Long = timestampMillis,
)

@Entity(tableName = "hydration_settings")
internal data class HydrationSettingsEntity(
    @PrimaryKey val singletonId: Int = 1,
    val dailyGoalMl: Int,
    @androidx.room3.ColumnInfo(defaultValue = "0") val updatedAtMillis: Long = System.currentTimeMillis(),
)
