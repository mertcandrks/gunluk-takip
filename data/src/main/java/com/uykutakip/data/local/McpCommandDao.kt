package com.uykutakip.data.local

import androidx.room3.*

@Entity(tableName = "mcp_receipts")
internal data class McpReceiptEntity(
    @PrimaryKey val requestId: String,
    val payloadHash: String,
    val resultJson: String,
    val tool: String,
    val createdAtMillis: Long,
)

@Dao
internal interface McpCommandDao {
    @Query("SELECT * FROM mcp_receipts WHERE requestId = :requestId")
    suspend fun find(requestId: String): McpReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(receipt: McpReceiptEntity)

    @Transaction
    suspend fun execute(requestId: String, payloadHash: String, tool: String, block: suspend () -> String): String {
        val existing = find(requestId)
        if (existing != null) {
            check(existing.payloadHash == payloadHash) { "REQUEST_ID_CONFLICT" }
            return existing.resultJson
        }
        val result = block()
        require(result.toByteArray(Charsets.UTF_8).size <= 100000) { "RESPONSE_TOO_LARGE" }
        insert(McpReceiptEntity(requestId, payloadHash, result, tool, System.currentTimeMillis()))
        return result
    }
}
