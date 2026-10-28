package com.uykutakip.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "sleep_records",
    indices = [Index("startMillis"), Index("endMillis")],
)
internal data class SleepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startMillis: Long,
    val endMillis: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(tableName = "active_sleep")
internal data class ActiveSleepEntity(
    @PrimaryKey val singletonId: Int = 1,
    val startMillis: Long,
    val updatedAtMillis: Long,
)
