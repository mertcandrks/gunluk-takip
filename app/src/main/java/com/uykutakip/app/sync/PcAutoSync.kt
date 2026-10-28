package com.uykutakip.app.sync

import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uykutakip.app.TrackerApplication
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.resume

object PcAutoSync {
    const val NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    fun preferences(context: Context) = context.getSharedPreferences("pc_sync", Context.MODE_PRIVATE)
    fun hasPermission(context: Context) = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(context, NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun pair(context: Context, credentials: PcSyncCredentials, enabled: Boolean) {
        preferences(context).edit().putString("last_host", credentials.host.trim())
            .putString("last_code", credentials.code).putString("service_id", credentials.serviceId)
            .putBoolean("auto_enabled", enabled).putLong("last_success", System.currentTimeMillis())
            .remove("last_error").apply()
        schedule(context)
    }

    fun forget(context: Context) {
        preferences(context).edit().clear().apply()
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork("pc-auto-sync")
        manager.cancelUniqueWork("pc-auto-sync-now")
    }

    fun schedule(context: Context, immediate: Boolean = false) {
        val prefs = preferences(context)
        if (!prefs.getBoolean("auto_enabled", false) || prefs.getString("last_code", "").orEmpty().length != 8) return
        val manager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true).build()
        manager.enqueueUniquePeriodicWork("pc-auto-sync", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PcSyncWorker>(30, TimeUnit.MINUTES).setConstraints(constraints).build())
        if (immediate) manager.enqueueUniqueWork("pc-auto-sync-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PcSyncWorker>().setConstraints(constraints).build())
    }

    @Suppress("DEPRECATION")
    suspend fun discover(context: Context, id: String): String? {
        if (id.isEmpty()) return null
        val nsd = context.getSystemService(NsdManager::class.java)
        val resolving = AtomicBoolean(false)
        var listener: NsdManager.DiscoveryListener? = null
        try {
            return withTimeoutOrNull(6_000) {
                suspendCancellableCoroutine<String?> { continuation ->
                    val discovery = object : NsdManager.DiscoveryListener {
                        override fun onDiscoveryStarted(type: String) = Unit
                        override fun onDiscoveryStopped(type: String) = Unit
                        override fun onServiceLost(info: NsdServiceInfo) = Unit
                        override fun onStartDiscoveryFailed(type: String, error: Int) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                        override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
                        override fun onServiceFound(info: NsdServiceInfo) {
                            if (!info.serviceName.startsWith("GunlukTakip-$id") || !resolving.compareAndSet(false, true)) return
                            try { nsd.resolveService(info, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(service: NsdServiceInfo, error: Int) { resolving.set(false) }
                                override fun onServiceResolved(service: NsdServiceInfo) {
                                    val identity = service.attributes["id"]?.toString(Charsets.UTF_8)
                                    if (identity == id && service.port == 47832 && continuation.isActive) {
                                        continuation.resume(service.host?.hostAddress)
                                    } else resolving.set(false)
                                }
                            }) } catch (_: Exception) { resolving.set(false) }
                        }
                    }
                    listener = discovery
                    nsd.discoverServices("_gunluktakip._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery)
                }
            }
        } finally {
            listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        }
    }
}

class PcSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = PcAutoSync.preferences(applicationContext)
        if (!prefs.getBoolean("auto_enabled", false)) return Result.success()
        if (!PcAutoSync.hasPermission(applicationContext)) {
            prefs.edit().putString("last_error", "Yerel ağ izni gerekiyor.").apply()
            return Result.success()
        }
        val code = prefs.getString("last_code", "").orEmpty()
        if (code.length != 8) return Result.success()
        return try {
            val host = PcAutoSync.discover(applicationContext, prefs.getString("service_id", "").orEmpty())
                ?: prefs.getString("last_host", "").orEmpty()
            currentCoroutineContext().ensureActive()
            if (!prefs.getBoolean("auto_enabled", false) || prefs.getString("last_code", "") != code) return Result.success()
            val app = applicationContext as TrackerApplication
            PcSyncClient(app.container.syncSnapshotRepository).sync(host, code)
            if (prefs.getBoolean("auto_enabled", false) && prefs.getString("last_code", "") == code) {
                prefs.edit().putString("last_host", host).putLong("last_success", System.currentTimeMillis())
                    .remove("last_error").apply()
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            prefs.edit().putString("last_error", "PC ile bağlantı kurulamadı; sonraki eşitlemede yeniden denenecek.").apply()
            // No retry storm while the PC is closed; the periodic job tries again.
            Result.success()
        }
    }
}
