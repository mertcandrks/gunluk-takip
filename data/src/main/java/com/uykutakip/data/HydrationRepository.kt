package com.uykutakip.data

import com.uykutakip.core.model.WaterIntake
import kotlinx.coroutines.flow.Flow

interface HydrationRepository {
    val records: Flow<List<WaterIntake>>
    val dailyGoalMl: Flow<Int>

    suspend fun add(amountMl: Int, now: Long = System.currentTimeMillis()): WaterIntake?
    suspend fun updateGoal(goalMl: Int): Boolean
    suspend fun delete(id: Long)
    suspend fun clearRecords()
}
