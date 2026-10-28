package com.uykutakip.data

import com.uykutakip.core.model.WaterIntake
import com.uykutakip.data.local.HydrationDao
import com.uykutakip.data.local.HydrationSettingsEntity
import com.uykutakip.data.local.WaterIntakeEntity
import com.uykutakip.data.local.SyncDeletionDao
import com.uykutakip.data.local.SyncDeletionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class OfflineHydrationRepository(
    private val dao: HydrationDao,
    private val deletions: SyncDeletionDao = NoopSyncDeletionDao,
) : HydrationRepository {
    override val records: Flow<List<WaterIntake>> = dao.observeRecords().map { rows ->
        rows.map { WaterIntake(it.id, it.amountMl, it.timestampMillis) }
    }
    override val dailyGoalMl: Flow<Int> = dao.observeGoal().map { it ?: DEFAULT_GOAL_ML }

    override suspend fun add(amountMl: Int, now: Long): WaterIntake? {
        if (amountMl !in MIN_AMOUNT_ML..MAX_AMOUNT_ML) return null
        val record = WaterIntake(amountMl = amountMl, timestampMillis = now)
        val id = dao.add(WaterIntakeEntity(amountMl = amountMl, timestampMillis = now))
        return record.copy(id = id)
    }

    override suspend fun updateGoal(goalMl: Int): Boolean {
        if (goalMl !in MIN_GOAL_ML..MAX_GOAL_ML) return false
        dao.saveSettings(HydrationSettingsEntity(dailyGoalMl = goalMl))
        return true
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
        deletions.upsert(SyncDeletionEntity("water", id, System.currentTimeMillis()))
    }
    override suspend fun clearRecords() {
        val now = System.currentTimeMillis()
        dao.snapshotRecords().forEach { deletions.upsert(SyncDeletionEntity("water", it.id, now)) }
        dao.clearRecords()
    }

    private companion object {
        const val DEFAULT_GOAL_ML = 2_500
        const val MIN_AMOUNT_ML = 10
        const val MAX_AMOUNT_ML = 5_000
        const val MIN_GOAL_ML = 500
        const val MAX_GOAL_ML = 10_000
    }
}
