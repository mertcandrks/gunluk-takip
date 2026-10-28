package com.uykutakip.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcSyncQrPayloadTest {
    @Test
    fun parsesPersistentIdentityAndRejectsInvalidIdentity() {
        val base = "gunluktakip://sync?v=1&host=192.168.1.40&port=47832&code=12345678"
        val identity = "0123456789abcdef0123456789abcdef"
        assertEquals(PcSyncCredentials("192.168.1.40", "12345678", identity), PcSyncQrPayload.parse("$base&id=$identity"))
        assertNull(PcSyncQrPayload.parse("$base&id=foreign-device"))
    }
    @Test
    fun parsesSupportedPayload() {
        assertEquals(
            PcSyncCredentials("192.168.1.40", "12345678"),
            PcSyncQrPayload.parse("gunluktakip://sync?v=1&host=192.168.1.40&port=47832&code=12345678"),
        )
    }

    @Test
    fun decodesEscapedHost() {
        assertEquals(
            PcSyncCredentials("desktop.local", "87654321"),
            PcSyncQrPayload.parse("gunluktakip://sync?v=1&host=desktop.local&port=47832&code=87654321"),
        )
    }

    @Test
    fun rejectsForeignOrMalformedPayloads() {
        assertNull(PcSyncQrPayload.parse("https://example.com/?code=12345678"))
        assertNull(PcSyncQrPayload.parse("gunluktakip://sync?v=2&host=192.168.1.40&port=47832&code=12345678"))
        assertNull(PcSyncQrPayload.parse("gunluktakip://sync?v=1&host=192.168.1.40&port=80&code=12345678"))
        assertNull(PcSyncQrPayload.parse("gunluktakip://sync?v=1&host=192.168.1.40&port=47832&code=1234"))
    }
}
