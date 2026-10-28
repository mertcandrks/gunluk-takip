package com.uykutakip.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class SleepDao {
    @Query("SELECT * FROM sleep_records ORDER BY endMillis DESC")
    abstract fun observeRecords(): Flow<List<SleepRecordEntity>>

    @Query("SELECT * FROM active_sleep WHERE singletonId = 1")
    abstract fun observeActive(): Flow<ActiveSleepEntity?>

    @Query("SELECT * FROM sleep_records ORDER BY endMillis DESC")
    abstract suspend fun snapshotRecords(): List<SleepRecordEntity>

    @Query("SELECT * FROM active_sleep WHERE singletonId = 1")
    abstract suspend fun snapshotActive(): ActiveSleepEntity?

    @Query("SELECT * FROM active_sleep WHERE singletonId = 1")
    protected abstract suspend fun active(): ActiveSleepEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun start(entity: ActiveSleepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun save(entity: SleepRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun importRecords(entities: List<SleepRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun importActive(entity: ActiveSleepEntity)

    @Query("UPDATE active_sleep SET startMillis = :startMillis, updatedAtMillis = :updatedAtMillis WHERE singletonId = 1")
    abstract suspend fun updateActiveStart(startMillis: Long, updatedAtMillis: Long): Int

    @Query("DELETE FROM active_sleep WHERE singletonId = 1")
    protected abstract suspend fun deleteActive()

    @Transaction
    open suspend fun replaceActive(entity: ActiveSleepEntity?) {
        deleteActive()
        if (entity != null) importActive(entity)
    }

    @Query("DELETE FROM sleep_records WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM sleep_records")
    abstract suspend fun clearRecords()

    @Transaction
    open suspend fun finish(endMillis: Long, updatedAtMillis: Long): SleepRecordEntity? {
        val active = active() ?: return null
        if (endMillis <= active.startMillis) return null
        val entity = SleepRecordEntity(
            id = 0L,
            startMillis = active.startMillis,
            endMillis = endMillis,
            createdAtMillis = active.startMillis,
            updatedAtMillis = updatedAtMillis,
        )
        val id = save(entity)
        deleteActive()
        return entity.copy(id = id)
    }
}
