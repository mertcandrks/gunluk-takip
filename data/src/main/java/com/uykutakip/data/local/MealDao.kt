package com.uykutakip.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class MealDao {
    @Query("SELECT * FROM meal_records ORDER BY startMillis DESC")
    abstract fun observeRecords(): Flow<List<MealRecordEntity>>

    @Query("SELECT * FROM active_meal WHERE singletonId = 1")
    abstract fun observeActive(): Flow<ActiveMealEntity?>

    @Query("SELECT * FROM meal_records ORDER BY startMillis DESC")
    abstract suspend fun snapshotRecords(): List<MealRecordEntity>

    @Query("SELECT * FROM active_meal WHERE singletonId = 1")
    abstract suspend fun snapshotActive(): ActiveMealEntity?

    @Query("SELECT * FROM active_meal WHERE singletonId = 1")
    abstract suspend fun currentActive(): ActiveMealEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun start(entity: ActiveMealEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun save(entity: MealRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun importRecords(entities: List<MealRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun importActive(entity: ActiveMealEntity)

    @Query("UPDATE active_meal SET itemsJson = :itemsJson, updatedAtMillis = :updatedAtMillis WHERE singletonId = 1")
    abstract suspend fun updateItems(itemsJson: String, updatedAtMillis: Long): Int

    @Query("DELETE FROM active_meal WHERE singletonId = 1")
    protected abstract suspend fun deleteActive()

    @Transaction
    open suspend fun replaceActive(entity: ActiveMealEntity?) {
        deleteActive()
        if (entity != null) importActive(entity)
    }

    @Query("DELETE FROM meal_records WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM meal_records")
    abstract suspend fun clearRecords()

    @Transaction
    open suspend fun finish(endMillis: Long, updatedAtMillis: Long): MealRecordEntity? {
        val active = currentActive() ?: return null
        if (endMillis < active.startMillis) return null
        val entity = MealRecordEntity(
            id = active.id,
            type = active.type,
            startMillis = active.startMillis,
            endMillis = endMillis,
            itemsJson = active.itemsJson,
            isQuickRecord = false,
            createdAtMillis = active.startMillis,
            updatedAtMillis = updatedAtMillis,
        )
        val id = save(entity)
        deleteActive()
        return entity.copy(id = id)
    }
}
