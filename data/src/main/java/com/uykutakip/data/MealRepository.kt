package com.uykutakip.data

import com.uykutakip.core.model.FoodEntry
import com.uykutakip.core.model.MealRecord
import com.uykutakip.core.model.MealType
import kotlinx.coroutines.flow.Flow

interface MealRepository {
    val records: Flow<List<MealRecord>>
    val activeMeal: Flow<MealRecord?>

    suspend fun start(type: MealType, now: Long = System.currentTimeMillis()): Boolean
    suspend fun addItem(item: FoodEntry): Boolean
    suspend fun removeItem(index: Int): Boolean
    suspend fun finish(now: Long = System.currentTimeMillis()): MealRecord?
    suspend fun addQuick(type: MealType, item: FoodEntry, now: Long = System.currentTimeMillis()): MealRecord?
    suspend fun addRecord(record: MealRecord): MealRecord?
    suspend fun delete(id: Long)
    suspend fun clearRecords()
}
