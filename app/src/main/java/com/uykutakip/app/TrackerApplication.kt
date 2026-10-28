package com.uykutakip.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrackerApplication : Application() {
    val dataReady = kotlinx.coroutines.CompletableDeferred<Unit>()
    lateinit var diagnostics: com.uykutakip.app.diagnostics.DiagnosticsStore
        private set
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        diagnostics = com.uykutakip.app.diagnostics.DiagnosticsStore(this)
        container = AppContainer(this)
        applicationScope.launch {
            try { container.migrateLegacyData(); dataReady.complete(Unit) }
            catch (error: Exception) { dataReady.completeExceptionally(error) }
        }
        com.uykutakip.app.sync.PcAutoSync.schedule(this)
    }
}
