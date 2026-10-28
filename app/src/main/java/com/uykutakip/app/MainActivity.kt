package com.uykutakip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.uykutakip.app.ui.DailyTrackerApp
import com.uykutakip.core.ui.theme.DailyTrackerTheme
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var frameMonitor: com.uykutakip.app.diagnostics.FrameMonitor
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val permissionPrefs by lazy { getSharedPreferences("runtime_permissions", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val diagnostics = (application as TrackerApplication).diagnostics
        frameMonitor = com.uykutakip.app.diagnostics.FrameMonitor(diagnostics)
        if (Build.VERSION.SDK_INT >= 33 && !permissionPrefs.getBoolean("notification_asked", false)) {
            permissionPrefs.edit().putBoolean("notification_asked", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val container = (application as TrackerApplication).container
        setContent {
            DailyTrackerTheme {
                DailyTrackerApp(container, diagnostics)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        frameMonitor.start(window)
        com.uykutakip.app.sync.PcAutoSync.schedule(this, immediate = true)
    }

    override fun onStop() {
        frameMonitor.stop(window)
        super.onStop()
    }
}

