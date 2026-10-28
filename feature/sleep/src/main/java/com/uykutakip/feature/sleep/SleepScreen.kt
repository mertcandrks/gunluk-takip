package com.uykutakip.feature.sleep

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.BedtimeOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.uykutakip.core.model.SleepRecord
import com.uykutakip.core.ui.components.EmptyState
import com.uykutakip.core.ui.components.MetricCard
import com.uykutakip.core.ui.components.SectionTitle
import com.uykutakip.core.ui.components.TrackerCard
import com.uykutakip.core.ui.components.rememberDateTimePicker
import com.uykutakip.core.ui.theme.AppSurfaceHigh
import com.uykutakip.core.ui.theme.SleepAccent
import com.uykutakip.core.ui.theme.TextSecondary
import com.uykutakip.core.ui.theme.WarmAccent
import com.uykutakip.core.time.TimeFormatter

@Composable
fun SleepScreen(
    now: Long,
    activeStartMillis: Long,
    records: List<SleepRecord>,
    formatter: TimeFormatter,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onFinishAt: (Long) -> Unit,
    onUpdateActiveStart: (Long) -> Unit,
    onAddRecord: (Long, Long) -> Unit,
    onUpdateRecord: (SleepRecord, Long, Long) -> Unit,
    onDeleteRecord: (SleepRecord) -> Unit,
    onClear: () -> Unit,
) {
    val showDateTimePicker = rememberDateTimePicker()
    var showClearDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<SleepRecord?>(null) }
    var deletingRecord by remember { mutableStateOf<SleepRecord?>(null) }
    val isSleeping = activeStartMillis > 0L
    val statusColor by animateColorAsState(
        targetValue = if (isSleeping) SleepAccent else TextSecondary,
        animationSpec = tween(250),
        label = "sleepStatus",
    )
    val totalDuration = remember(records) { records.sumOf { it.durationMillis } }
    val weekStart = now - 7L * 86_400_000L
    val weekDuration = remember(records, now / 60_000L) {
        records.sumOf { if (it.endMillis >= weekStart) it.durationMillis else 0L }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("GÜNLÜK TAKİP", style = MaterialTheme.typography.labelMedium, color = SleepAccent)
                Spacer(Modifier.height(5.dp))
                Text("Uyku", style = MaterialTheme.typography.headlineLarge)
                Text(formatter.fullDate(now), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }

            item {
                SleepLiveCard(
                    activeStartMillis = activeStartMillis,
                    calendarNow = now,
                    formatter = formatter,
                    onStart = onStart,
                    onFinish = onFinish,
                    onFinishAt = onFinishAt,
                    onUpdateActiveStart = onUpdateActiveStart,
                    showDateTimePicker = showDateTimePicker,
                )
            }

            item {
                SleepCyclePlannerCard(
                    bedtimeMillis = if (isSleeping) activeStartMillis else now,
                    isSleeping = isSleeping,
                    formatter = formatter,
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Son uyku", records.firstOrNull()?.let { formatter.duration(it.durationMillis) } ?: "-", SleepAccent, Modifier.weight(1f))
                    MetricCard("Ortalama", if (records.isEmpty()) "-" else formatter.duration(totalDuration / records.size), WarmAccent, Modifier.weight(1f))
                    MetricCard("Son 7 gün", formatter.duration(weekDuration), Color(0xFF63C7FF), Modifier.weight(1f))
                }
            }

            item {
                SectionTitle("Uyku geçmişi") {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Eksik uyku kaydı ekle", tint = SleepAccent)
                    }
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Uyku kayıtlarını temizle", tint = TextSecondary)
                        }
                    }
                }
            }

            if (records.isEmpty()) {
                item { EmptyState("Henüz uyku kaydı yok", Icons.Outlined.BedtimeOff) }
            } else {
                item { Text("${records.size} kayıt", style = MaterialTheme.typography.labelMedium, color = TextSecondary) }
                items(records, key = { it.id }) { record ->
                    SleepRecordCard(
                        record = record,
                        formatter = formatter,
                        onEdit = { editingRecord = record },
                        onDelete = { deletingRecord = record },
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Uyku kayıtları silinsin mi?") },
            text = { Text("Cihazdaki ${records.size} uyku kaydının tamamı silinecek. Bu işlem geri alınamaz.") },
            confirmButton = { TextButton(onClick = { showClearDialog = false; onClear() }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Vazgeç") } },
        )
    }
    if (showAddDialog) {
        SleepRecordEditorDialog(
            title = "Eksik uyku kaydı",
            initialStartMillis = now - 8L * 60L * 60L * 1000L,
            initialEndMillis = now,
            formatter = formatter,
            onDismiss = { showAddDialog = false },
            onSave = { start, end -> showAddDialog = false; onAddRecord(start, end) },
        )
    }
    editingRecord?.let { record ->
        SleepRecordEditorDialog(
            title = "Uyku kaydını düzenle",
            initialStartMillis = record.startMillis,
            initialEndMillis = record.endMillis,
            formatter = formatter,
            onDismiss = { editingRecord = null },
            onSave = { start, end -> editingRecord = null; onUpdateRecord(record, start, end) },
        )
    }
    deletingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { deletingRecord = null },
            title = { Text("Uyku kaydı silinsin mi?") },
            text = { Text("${formatter.shortDate(record.startMillis)} tarihli kayıt kaldırılacak.") },
            confirmButton = {
                TextButton(onClick = { deletingRecord = null; onDeleteRecord(record) }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { deletingRecord = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun SleepLiveCard(
    activeStartMillis: Long,
    calendarNow: Long,
    formatter: TimeFormatter,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onFinishAt: (Long) -> Unit,
    onUpdateActiveStart: (Long) -> Unit,
    showDateTimePicker: (Long, (Long) -> Unit) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isSleeping = activeStartMillis > 0L
    var liveNow by remember { mutableLongStateOf(calendarNow) }
    LaunchedEffect(lifecycle, activeStartMillis) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                liveNow = System.currentTimeMillis()
                delay(if (activeStartMillis > 0L) 1_000L else 60_000L - liveNow % 60_000L)
            }
        }
    }
    val displayNow = if (isSleeping) liveNow else calendarNow
    val statusColor by animateColorAsState(
        targetValue = if (isSleeping) SleepAccent else TextSecondary,
        animationSpec = tween(250),
        label = "sleepStatus",
    )

    TrackerCard(containerColor = AppSurfaceHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isSleeping) Icons.Filled.Bedtime else Icons.Outlined.BedtimeOff,
                contentDescription = null,
                tint = statusColor,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isSleeping) "Uyku takibi devam ediyor" else "Takip hazır",
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            if (isSleeping) formatter.timer(displayNow - activeStartMillis) else formatter.time(displayNow),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
            color = Color.White,
        )
        Text(
            if (isSleeping) "Başlangıç  ${formatter.time(activeStartMillis)}" else "Yatarken başlat, uyanınca bitir",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = TextSecondary,
        )
        if (isSleeping) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { showDateTimePicker(activeStartMillis, onUpdateActiveStart) }) { Text("Başlangıcı düzelt") }
                TextButton(onClick = { showDateTimePicker(displayNow, onFinishAt) }) { Text("Uyanma saati seç") }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStart,
                enabled = !isSleeping,
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SleepAccent),
            ) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Başlat", maxLines = 1)
            }
            FilledTonalButton(
                onClick = onFinish,
                enabled = isSleeping,
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = WarmAccent.copy(alpha = 0.18f), contentColor = WarmAccent),
            ) {
                Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Uyandım")
            }
        }
    }
}


@Composable
private fun SleepRecordCard(
    record: SleepRecord,
    formatter: TimeFormatter,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    TrackerCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatter.shortDate(record.startMillis), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${formatter.time(record.startMillis)}  →  ${formatter.time(record.endMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatter.duration(record.durationMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SleepAccent,
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Uyku kaydını düzenle", tint = TextSecondary, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Uyku kaydını sil", tint = WarmAccent, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
    }
}

