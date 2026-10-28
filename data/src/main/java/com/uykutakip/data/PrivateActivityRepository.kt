package com.uykutakip.data

import com.uykutakip.core.model.PrivateActivityRecord
import com.uykutakip.data.local.PrivateActivityDao
import com.uykutakip.data.local.PrivateActivityEntity
import com.uykutakip.data.local.SyncDeletionDao
import com.uykutakip.data.local.SyncDeletionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PrivateActivityRepository {
    val records: Flow<List<PrivateActivityRecord>>
    val activeRecord: Flow<PrivateActivityRecord?>
    suspend fun start(now: Long = System.currentTimeMillis()): Boolean
    suspend fun finish(now: Long = System.currentTimeMillis()): Boolean
    suspend fun addRecord(startMillis: Long, endMillis: Long): Boolean
    suspend fun updateRecord(id: Long, startMillis: Long, endMillis: Long): Boolean
    suspend fun deleteRecord(id: Long)
}

internal class OfflinePrivateActivityRepository(private val dao: PrivateActivityDao, private val deletions: SyncDeletionDao = NoopSyncDeletionDao) : PrivateActivityRepository {
    override val records = dao.observeAll().map { rows -> rows.map { PrivateActivityRecord(it.id, it.startMillis, it.endMillis) } }
    override val activeRecord = records.map { rows -> rows.firstOrNull { it.isActive } }
    override suspend fun start(now: Long): Boolean { if (dao.active() != null) return false; dao.insert(PrivateActivityEntity(startMillis = now, endMillis = null)); return true }
    override suspend fun finish(now: Long): Boolean = dao.active()?.let { dao.finish(it.id, now, System.currentTimeMillis()) > 0 } ?: false
    override suspend fun addRecord(startMillis: Long, endMillis: Long): Boolean { if (endMillis <= startMillis) return false; dao.insert(PrivateActivityEntity(startMillis = startMillis, endMillis = endMillis)); return true }
    override suspend fun updateRecord(id: Long, startMillis: Long, endMillis: Long): Boolean { if (endMillis <= startMillis) return false; dao.update(PrivateActivityEntity(id, startMillis, endMillis)); return true }
    override suspend fun deleteRecord(id: Long) {
        dao.delete(id)
        deletions.upsert(SyncDeletionEntity("private", id, System.currentTimeMillis()))
    }
}
