package com.uykutakip.feature.agenda

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.uykutakip.core.model.AgendaEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun AgendaTimerControls(entry: AgendaEntry, toggle: () -> Unit, finish: () -> Unit) {
    val running = entry.timerStartedAtMillis > 0
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, lifecycle) {
        if (running) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { now = System.currentTimeMillis(); delay(1000) }
        }
    }
    val total = entry.elapsedMillis + if (running) (now - entry.timerStartedAtMillis).coerceAtLeast(0) else 0
    Column(Modifier.fillMaxWidth()) {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!entry.completed && !entry.notDone) TextButton(onClick = toggle) {
            Icon(if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null)
            Text(if (running) "Duraklat" else if (total > 0) "Devam et" else "Başlat")
        }
        if (!entry.completed && !entry.notDone && (total > 0 || running)) TextButton(onClick = finish) { Text("Bitir") }
      }
        if (total > 0 || running) Text(
            "%02d:%02d:%02d".format(total / 3600000, total / 60000 % 60, total / 1000 % 60),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}
