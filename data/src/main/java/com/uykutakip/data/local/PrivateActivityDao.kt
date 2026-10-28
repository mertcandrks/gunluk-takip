package com.uykutakip.data.local

import androidx.room3.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "private_activity_records", indices = [Index("startMillis")])
internal data class PrivateActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startMillis: Long,
    val endMillis: Long?,
    @ColumnInfo(defaultValue = "0") val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Dao
internal interface PrivateActivityDao {
    @Query("SELECT * FROM private_activity_records ORDER BY startMillis DESC") fun observeAll(): Flow<List<PrivateActivityEntity>>
    @Query("SELECT * FROM private_activity_records ORDER BY startMillis DESC") suspend fun snapshotRecords(): List<PrivateActivityEntity>
    @Query("SELECT * FROM private_activity_records WHERE endMillis IS NULL ORDER BY startMillis DESC LIMIT 1") suspend fun active(): PrivateActivityEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(record: PrivateActivityEntity): Long
    @Query("UPDATE private_activity_records SET endMillis = :endMillis, updatedAtMillis = :updatedAtMillis WHERE id = :id AND endMillis IS NULL") suspend fun finish(id: Long, endMillis: Long, updatedAtMillis: Long): Int
    @Update suspend fun update(record: PrivateActivityEntity)
    @Query("DELETE FROM private_activity_records WHERE id = :id") suspend fun delete(id: Long)
}
