package com.uykutakip.app.mcp

internal object McpPairingWindow {
    private var deadline = 0L
    val active = kotlinx.coroutines.flow.MutableStateFlow(false)
    @Synchronized fun open() { deadline = android.os.SystemClock.elapsedRealtime() + 60000; active.value = true }
    @Synchronized fun close() { deadline = 0; active.value = false }
    @Synchronized fun consume(): Boolean {
        val permitted = deadline > android.os.SystemClock.elapsedRealtime()
        deadline = 0
        active.value = false
        return permitted
    }
}
