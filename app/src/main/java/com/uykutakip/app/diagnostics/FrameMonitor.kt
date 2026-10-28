package com.uykutakip.app.diagnostics

import android.os.Build
import android.view.FrameMetrics
import android.view.Window

class FrameMonitor(private val store: DiagnosticsStore) {
    private var frames = 0
    private var slow = 0
    private var dropped = 0
    private var maxNanos = 0L
    private var route = "startup"
    private var lastReport = 0L
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, lost ->
        if (store.enabled.value) {
            val current = store.screen
            if (current != route) { flush(); route = current }
            val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val deadline = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L
            if (duration >= 0L) {
                frames++
                if (duration >= (if (deadline > 0L) deadline else 16_666_667L)) slow++
                maxNanos = maxOf(maxNanos, duration)
            }
            dropped += lost
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastReport >= 5_000L) { flush(); lastReport = now }
        } else { reset() }
    }

    fun start(window: Window) = window.addOnFrameMetricsAvailableListener(listener, store.handler)
    fun stop(window: Window) {
        window.removeOnFrameMetricsAvailableListener(listener)
        store.handler.post { flush() }
    }

    private fun flush() {
        if (frames > 0) store.event("FRAMES screen=$route count=$frames slow=$slow maxMs=${maxNanos / 1_000_000} dropped=$dropped threshold=${if (Build.VERSION.SDK_INT >= 31) "deadline" else "16.67ms-estimate"}")
        reset()
    }

    private fun reset() { frames = 0; slow = 0; dropped = 0; maxNanos = 0L }
}
