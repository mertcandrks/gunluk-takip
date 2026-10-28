package com.uykutakip.data

import com.uykutakip.core.model.SleepRecord
import kotlinx.coroutines.flow.Flow

interface SleepRepository {
    val records: Flow<List<SleepRecord>>
    val activeStartMillis: Flow<Long?>

    suspend fun start(now: Long = System.currentTimeMillis()): Boolean
    suspend fun finish(now: Long = System.currentTimeMillis()): SleepRecord?
    suspend fun updateActiveStart(startMillis: Long, now: Long = System.currentTimeMillis()): Boolean
    suspend fun addRecord(startMillis: Long, endMillis: Long): SleepRecord?
    suspend fun updateRecord(id: Long, startMillis: Long, endMillis: Long): SleepRecord?
    suspend fun deleteRecord(id: Long)
    suspend fun clearRecords()
}
