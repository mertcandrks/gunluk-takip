package com.uykutakip.data.local

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query

@Entity(tableName = "sync_deletions", primaryKeys = ["kind", "recordId"])
internal data class SyncDeletionEntity(
    val kind: String,
    val recordId: Long,
    val updatedAtMillis: Long,
)

@Dao
internal interface SyncDeletionDao {
    @Query("SELECT * FROM sync_deletions")
    suspend fun all(): List<SyncDeletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncDeletionEntity)

    @Query("DELETE FROM sync_deletions WHERE kind = :kind AND recordId = :recordId")
    suspend fun remove(kind: String, recordId: Long)
}
