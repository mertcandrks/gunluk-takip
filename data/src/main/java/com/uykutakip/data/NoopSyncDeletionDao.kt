package com.uykutakip.data

import com.uykutakip.data.local.SyncDeletionDao
import com.uykutakip.data.local.SyncDeletionEntity

internal object NoopSyncDeletionDao : SyncDeletionDao {
    override suspend fun all(): List<SyncDeletionEntity> = emptyList()
    override suspend fun upsert(entity: SyncDeletionEntity) = Unit
    override suspend fun remove(kind: String, recordId: Long) = Unit
}
