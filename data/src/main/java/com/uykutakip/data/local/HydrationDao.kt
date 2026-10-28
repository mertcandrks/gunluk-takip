package com.uykutakip.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface HydrationDao {
    @Query("SELECT * FROM water_intakes ORDER BY timestampMillis DESC")
    fun observeRecords(): Flow<List<WaterIntakeEntity>>

    @Query("SELECT dailyGoalMl FROM hydration_settings WHERE singletonId = 1")
    fun observeGoal(): Flow<Int?>

    @Query("SELECT * FROM water_intakes ORDER BY timestampMillis DESC")
    suspend fun snapshotRecords(): List<WaterIntakeEntity>

    @Query("SELECT dailyGoalMl FROM hydration_settings WHERE singletonId = 1")
    suspend fun snapshotGoal(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: WaterIntakeEntity): Long

    @Query("SELECT * FROM hydration_settings WHERE singletonId = 1")
    suspend fun snapshotSettings(): HydrationSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(entity: HydrationSettingsEntity)

    @Query("DELETE FROM water_intakes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM water_intakes")
    suspend fun clearRecords()
}
