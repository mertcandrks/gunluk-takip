package com.uykutakip.app.sync

import com.uykutakip.data.SyncSnapshotRepository
import com.uykutakip.sync.SecureSyncPacket
import com.uykutakip.sync.SnapshotCodec
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PcSyncClient(private val snapshots: SyncSnapshotRepository) {
    suspend fun sync(host: String, code: String): SyncResult = transferMutex.withLock { syncLocked(host, code) }

    private suspend fun syncLocked(host: String, code: String): SyncResult = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "PC adresini gir." }
        require(code.length == 8 && code.all(Char::isDigit)) { "8 haneli eşleştirme kodunu gir." }
        val snapshot = snapshots.createSnapshot()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host.trim(), SecureSyncPacket.PORT), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            SecureSyncPacket.write(output, SnapshotCodec.encode(snapshot), code.toCharArray())
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            if (input.readInt() != ACK) error("PC aktarımı onaylamadı.")
            val merged = SnapshotCodec.decode(SecureSyncPacket.read(input, code.toCharArray()))
            val applied = snapshots.applySnapshot(merged)
            return@withContext SyncResult(
                merged.sleeps.size,
                merged.meals.size,
                merged.water.size,
                merged.agenda.size,
                applied.applied,
                applied.deleted,
            )
        }
    }

    data class SyncResult(
        val sleep: Int,
        val meals: Int,
        val water: Int,
        val agenda: Int,
        val applied: Int,
        val deleted: Int,
    )

    companion object {
        private val transferMutex = Mutex()
        const val ACK = 0x47544F4B
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
