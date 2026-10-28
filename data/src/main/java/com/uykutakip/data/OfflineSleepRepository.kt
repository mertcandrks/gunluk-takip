package com.uykutakip.data

import android.database.sqlite.SQLiteConstraintException
import com.uykutakip.core.model.SleepRecord
import com.uykutakip.data.local.ActiveSleepEntity
import com.uykutakip.data.local.SleepDao
import com.uykutakip.data.local.SleepRecordEntity
import com.uykutakip.data.local.SyncDeletionDao
import com.uykutakip.data.local.SyncDeletionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class OfflineSleepRepository(
    private val dao: SleepDao,
    private val deletions: SyncDeletionDao = NoopSyncDeletionDao,
) : SleepRepository {
    override val records: Flow<List<SleepRecord>> = dao.observeRecords().map { rows -> rows.map { it.toModel() } }
    override val activeStartMillis: Flow<Long?> = dao.observeActive().map { it?.startMillis }

    override suspend fun start(now: Long): Boolean {
        if (now <= 0L) return false
        return try {
            dao.start(ActiveSleepEntity(startMillis = now, updatedAtMillis = now))
            true
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    override suspend fun finish(now: Long): SleepRecord? {
        if (now > System.currentTimeMillis() + FUTURE_TOLERANCE_MILLIS) return null
        return dao.finish(now, System.currentTimeMillis())?.toModel()
    }

    override suspend fun updateActiveStart(startMillis: Long, now: Long): Boolean {
        if (startMillis <= 0L || startMillis >= now) return false
        return dao.updateActiveStart(startMillis, now) > 0
    }

    override suspend fun addRecord(startMillis: Long, endMillis: Long): SleepRecord? {
        if (!isValidRange(startMillis, endMillis)) return null
        val record = SleepRecord(startMillis, endMillis)
        val id = dao.save(record.toEntity())
        return record.copy(id = id)
    }

    override suspend fun updateRecord(id: Long, startMillis: Long, endMillis: Long): SleepRecord? {
        if (!isValidRange(startMillis, endMillis)) return null
        val record = SleepRecord(startMillis, endMillis, id)
        dao.save(record.toEntity())
        return record
    }

    override suspend fun deleteRecord(id: Long) {
        dao.delete(id)
        deletions.upsert(SyncDeletionEntity("sleep", id, System.currentTimeMillis()))
    }
    override suspend fun clearRecords() {
        val now = System.currentTimeMillis()
        dao.snapshotRecords().forEach { deletions.upsert(SyncDeletionEntity("sleep", it.id, now)) }
        dao.clearRecords()
    }

    private fun isValidRange(startMillis: Long, endMillis: Long): Boolean =
        startMillis > 0L && endMillis > startMillis && endMillis <= System.currentTimeMillis() + FUTURE_TOLERANCE_MILLIS

    private fun SleepRecordEntity.toModel() = SleepRecord(startMillis, endMillis, id)

    private fun SleepRecord.toEntity() = SleepRecordEntity(
        id = id,
        startMillis = startMillis,
        endMillis = endMillis,
        createdAtMillis = startMillis,
        updatedAtMillis = System.currentTimeMillis(),
    )

    private companion object {
        const val FUTURE_TOLERANCE_MILLIS = 60_000L
    }
}
