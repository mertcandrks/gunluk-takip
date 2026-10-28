package com.uykutakip.data.local

import androidx.room3.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "shopping_items")
internal data class ShoppingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val longTerm: Boolean,
    val purchased: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Dao
internal interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY updatedAtMillis DESC, id DESC")
    suspend fun snapshotRecords(): List<ShoppingEntity>
    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun find(id: Long): ShoppingEntity?
    @Query("SELECT * FROM shopping_items ORDER BY updatedAtMillis DESC, id DESC")
    fun observe(): Flow<List<ShoppingEntity>>
    @Insert
    suspend fun insert(item: ShoppingEntity)
    @Query("UPDATE shopping_items SET text = :text, longTerm = :longTerm, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun update(id: Long, text: String, longTerm: Boolean, updatedAtMillis: Long): Int
    @Query("UPDATE shopping_items SET purchased = NOT purchased, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun toggle(id: Long, updatedAtMillis: Long): Int
    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun delete(id: Long)
}
