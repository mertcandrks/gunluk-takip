package com.uykutakip.app.diagnostics

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uykutakip.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsDialog(store: DiagnosticsStore, onDismiss: () -> Unit) {
    val enabled by store.enabled.collectAsState()
    val logs by store.logs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            exporting = true
            try {
                withContext(Dispatchers.IO) {
                    val report = store.report()
                    checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(report) }
                }
                status = "Rapor kaydedildi"
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                status = "Rapor kaydedilemedi"
            } finally { exporting = false }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Geliştirici", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onDismiss) { Text("Kapat") }
                }
                Text("${BuildConfig.VERSION_NAME} • ${BuildConfig.BUILD_TYPE}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tanılama kaydı", modifier = Modifier.weight(1f).padding(top = 12.dp))
                    Switch(checked = enabled, onCheckedChange = store::setEnabled)
                }
                Row {
                    TextButton(enabled = !exporting, onClick = { export.launch("uyku-tanilama.json") }) { Text("JSON dışa aktar") }
                    TextButton(onClick = { confirmClear = true }, enabled = logs.isNotEmpty()) { Text("Logları sil") }
                }
                if (status.isNotEmpty()) Text(status)
                HorizontalDivider()
                if (logs.isEmpty()) Text("Henüz tanılama kaydı yok", Modifier.padding(vertical = 16.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(logs) { entry -> Text(entry, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Tanılama logları silinsin mi?") },
        text = { Text("Uyku ve diğer takip kayıtları silinmez.") },
        confirmButton = { TextButton(onClick = { store.clear(); confirmClear = false }) { Text("Sil") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } },
    )
}
