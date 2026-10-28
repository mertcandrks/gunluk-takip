package com.uykutakip.feature.private_tracking

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uykutakip.core.model.PrivateActivityRecord
import com.uykutakip.core.time.TimeFormatter
import com.uykutakip.core.ui.components.DateTimeButton
import com.uykutakip.core.ui.components.MetricCard
import com.uykutakip.core.ui.components.TrackerCard
import com.uykutakip.core.ui.theme.AppSurfaceHigh
import com.uykutakip.core.ui.theme.TextSecondary
import com.uykutakip.core.ui.theme.WarmAccent
import com.uykutakip.data.PrivateActivityRepository

@Composable
fun PrivateTrackingRoute(repository: PrivateActivityRepository) {
    val vm: PrivateTrackingViewModel = viewModel(factory = remember(repository) { PrivateTrackingViewModelFactory(repository) })
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    PrivateTrackingScreen(state, vm::start, vm::finish, vm::add, vm::update, vm::delete)
}

@Composable
private fun PrivateTrackingScreen(
    state: PrivateTrackingState,
    start: () -> Unit,
    finish: () -> Unit,
    add: (Long, Long, () -> Unit) -> Unit,
    update: (Long, Long, Long, () -> Unit) -> Unit,
    delete: (Long) -> Unit,
) {
    val formatter = remember { TimeFormatter() }
    val day = formatter.localDate(state.now)
    val today = remember(state.records, day) {
        state.records.filter { formatter.localDate(it.startMillis) == day }
    }
    val completedDuration = remember(today) {
        today.sumOf { if (it.isActive) 0L else it.durationMillis }
    }
    val todayDuration = completedDuration + (state.active?.takeIf {
        formatter.localDate(it.startMillis) == day
    }?.let { (state.now - it.startMillis).coerceAtLeast(0L) } ?: 0L)
    var editor by remember { mutableStateOf<PrivateActivityRecord?>(null) }
    var deleting by remember { mutableStateOf<PrivateActivityRecord?>(null) }
    LazyColumn(Modifier.fillMaxSize().widthIn(max = 720.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("GÜNLÜK TAKİP", style = MaterialTheme.typography.labelMedium, color = WarmAccent)
            Spacer(Modifier.height(5.dp))
            Text("Özel takip", style = MaterialTheme.typography.headlineLarge)
            Text("Bu alan yalnızca cihazında saklanır", color = TextSecondary)
        }
        item {
            TrackerCard(containerColor = AppSurfaceHigh) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (state.active != null) "Takip devam ediyor" else "Bugünkü toplam", color = TextSecondary)
                        Text(if (state.active != null) formatter.timer(state.now - state.active.startMillis) else formatter.duration(todayDuration), style = MaterialTheme.typography.displaySmall, color = WarmAccent)
                    }
                    Icon(Icons.Outlined.Lock, "Özel alan", tint = WarmAccent)
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = if (state.active == null) start else finish, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (state.active == null) Icons.Filled.Add else Icons.Outlined.Timer, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.active == null) "Otomatik başlat" else "Otomatik bitir")
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Bugün", today.size.toString(), WarmAccent, Modifier.weight(1f)); MetricCard("Toplam süre", formatter.duration(todayDuration), WarmAccent, Modifier.weight(1f)) } }
        item {
            OutlinedButton(onClick = { editor = PrivateActivityRecord(startMillis = state.now - 30 * 60_000L, endMillis = state.now) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("Manuel kayıt ekle")
            }
        }
        item { Text("Geçmiş", style = MaterialTheme.typography.titleLarge) }
        if (state.records.isEmpty()) item { Text("Henüz kayıt yok", color = TextSecondary) }
        items(state.records, key = { it.id }) { record ->
            TrackerCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(formatter.fullDate(record.startMillis), color = TextSecondary)
                        Text("${formatter.time(record.startMillis)} – ${record.endMillis?.let(formatter::time) ?: "devam ediyor"}")
                        Text(if (record.isActive) formatter.timer(state.now - record.startMillis) else formatter.duration(record.durationMillis), color = WarmAccent)
                    }
                    IconButton(onClick = { editor = record }) { Icon(Icons.Outlined.Edit, "Kaydı düzenle") }
                    IconButton(onClick = { deleting = record }) { Icon(Icons.Outlined.DeleteOutline, "Kaydı sil") }
                }
            }
        }
    }
    editor?.let { record ->
        PrivateRecordEditor(record, formatter, { editor = null }) { startMillis, endMillis, done ->
            if (record.id == 0L) add(startMillis, endMillis, done) else update(record.id, startMillis, endMillis, done)
        }
    }
    deleting?.let { record ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Kayıt silinsin mi?") }, text = { Text(formatter.fullDate(record.startMillis)) }, confirmButton = { TextButton(onClick = { delete(record.id); deleting = null }) { Text("Sil") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } })
    }
}

@Composable
private fun PrivateRecordEditor(record: PrivateActivityRecord, formatter: TimeFormatter, dismiss: () -> Unit, save: (Long, Long, () -> Unit) -> Unit) {
    var start by rememberSaveable(record.id) { mutableStateOf(record.startMillis) }
    var end by rememberSaveable(record.id) { mutableStateOf(record.endMillis ?: System.currentTimeMillis()) }
    val picker = com.uykutakip.core.ui.components.rememberDateTimePicker()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (record.id == 0L) "Manuel kayıt" else "Kaydı düzenle") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { DateTimeButton("Başlangıç", start, formatter) { picker(start) { start = it } }; DateTimeButton("Bitiş", end, formatter) { picker(end) { end = it } } } },
        confirmButton = { TextButton(onClick = { save(start, end, dismiss) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } },
    )
}
