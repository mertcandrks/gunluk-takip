package com.uykutakip.app.mcp

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun McpAccessDialog(dismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { McpAccessStore(context) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var writes by remember { mutableStateOf(store.writes) }
    var privateAccess by remember { mutableStateOf(store.privateAccess) }
    var deletes by remember { mutableStateOf(store.deletes) }
    var busy by remember { mutableStateOf(false) }
    val pairing by McpPairingWindow.active.collectAsState()
    DisposableEffect(Unit) { onDispose { McpPairingWindow.close() } }
    LaunchedEffect(pairing) { if (pairing) { kotlinx.coroutines.delay(60000); McpPairingWindow.close() } }
    val scope = rememberCoroutineScope()
    fun change(block: () -> Unit, update: () -> Unit) {
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { block() }; update() }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { Toast.makeText(context, "Ayar kaydedilemedi.", Toast.LENGTH_SHORT).show() }
            finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("AI / MCP erişimi") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MCP erişimi", Modifier.weight(1f))
                Switch(enabled, { next -> change({ if (next && store.token.isEmpty()) store.rotate(); store.enabled = next }, { enabled = next }) }, enabled = !busy)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Kayıtları değiştirme", Modifier.weight(1f))
                Switch(writes, { next -> change({ store.writes = next }, { writes = next }) }, enabled = enabled && !busy)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Özel alan erişimi", Modifier.weight(1f))
                Switch(privateAccess, { next -> change({ store.privateAccess = next }, { privateAccess = next }) }, enabled = enabled && !busy)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Kalıcı kayıt silme", Modifier.weight(1f))
                Switch(deletes, { next -> change({ store.deletes = next }, { deletes = next }) }, enabled = enabled && writes && !busy)
            }
            HorizontalDivider()
            OutlinedButton(onClick = { McpPairingWindow.open() }, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (pairing) "Eşleştirme açık" else "PC eşleştirmeyi aç")
            }
            OutlinedButton(onClick = {
                val clip = ClipData.newPlainText("MCP access key", store.token)
                if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            }, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Anahtarı kopyala")
            }
            OutlinedButton(onClick = { change({ store.rotate() }, {}) }, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Anahtarı yenile")
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Kapat") } })
}
