package com.uykutakip.app.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PcSyncDialog(client: PcSyncClient, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("pc_sync", Context.MODE_PRIVATE) }
    var host by remember { mutableStateOf(preferences.getString("last_host", "").orEmpty()) }
    var code by remember { mutableStateOf(preferences.getString("last_code", "").orEmpty()) }
    var running by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var autoEnabled by remember { mutableStateOf(preferences.getBoolean("auto_enabled", true)) }
    var serviceId by remember { mutableStateOf(preferences.getString("service_id", "").orEmpty()) }
    val scope = rememberCoroutineScope()
    fun startSync(syncHost: String, syncCode: String) {
        if (running) return
        host = syncHost.trim()
        code = syncCode
        if (!PcAutoSync.hasPermission(context)) {
            error = "Yerel ağ iznini açıp yeniden senkronize et."
            return
        }
        running = true
        error = null
        scope.launch {
            try {
                val result = client.sync(syncHost, syncCode)
                PcAutoSync.pair(context, PcSyncCredentials(syncHost, syncCode, serviceId), autoEnabled)
                Toast.makeText(context, "İki yönlü senkron tamamlandı: ${result.applied} değişiklik, ${result.deleted} silme alındı.", Toast.LENGTH_LONG).show()
                onDismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.localizedMessage ?: "Bağlantı kurulamadı."
            } finally {
                running = false
            }
        }
    }
    val networkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        error = if (granted) null else "Yerel ağ izni verilmedi."
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showScanner = true
        } else {
            error = "QR taramak için kamera izni gerekiyor. Manuel adres ve kodla da bağlanabilirsin."
        }
    }

    if (showScanner) {
        PcSyncQrScanner(
            onScanned = { credentials ->
                showScanner = false
                serviceId = credentials.serviceId
                startSync(credentials.host, credentials.code)
            },
            onDismiss = { showScanner = false },
        )
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        icon = { Icon(Icons.Outlined.Computer, contentDescription = null) },
        title = { Text("PC ile senkronize et") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!PcAutoSync.hasPermission(context)) {
                    TextButton(onClick = { networkPermission.launch(PcAutoSync.NETWORK_PERMISSION) }) { Text("Yerel ağ izni ver") }
                }
                Text("PC uygulamasını aç. En hızlı bağlantı için ekrandaki QR kodunu tara.")
                OutlinedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            showScanner = true
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !running,
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Text("  QR kodunu tara")
                }
                Text("İstersen bağlantı bilgilerini manuel de girebilirsin.")
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; serviceId = ""; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PC adresi") },
                    placeholder = { Text("192.168.1.50") },
                    leadingIcon = { Icon(Icons.Outlined.Computer, contentDescription = null) },
                    singleLine = true,
                    enabled = !running,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(8); serviceId = ""; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Eşleştirme kodu") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    enabled = !running,
                )
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Otomatik eşitleme")
                    Switch(checked = autoEnabled, enabled = !running, onCheckedChange = { enabled ->
                        autoEnabled = enabled
                        if (preferences.getString("last_code", "").orEmpty().length == 8) {
                            preferences.edit().putBoolean("auto_enabled", enabled).apply()
                            if (enabled) PcAutoSync.schedule(context, immediate = true)
                            else {
                                androidx.work.WorkManager.getInstance(context).cancelUniqueWork("pc-auto-sync")
                                androidx.work.WorkManager.getInstance(context).cancelUniqueWork("pc-auto-sync-now")
                            }
                        }
                    })
                }
                preferences.getString("last_error", null)?.let { Text(it) }
                val lastSuccess = preferences.getLong("last_success", 0)
                if (lastSuccess > 0) Text("Son eşitleme: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSuccess))}")
                if (preferences.contains("last_code")) TextButton(enabled = !running, onClick = {
                    PcAutoSync.forget(context); host = ""; code = ""; serviceId = ""; autoEnabled = false
                }) { Text("Eşleşmeyi unut") }
                Text("Aktarım yalnızca aynı ağ içinde ve şifreli yapılır.")
            }
        },
        confirmButton = {
            Button(
                enabled = !running && host.isNotBlank() && code.length == 8,
                onClick = { startSync(host, code) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(if (running) "Gönderiliyor" else "Senkronize et")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !running) { Text("Vazgeç") } },
    )
}
