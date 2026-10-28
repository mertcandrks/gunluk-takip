package com.uykutakip.feature.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uykutakip.core.time.TimeFormatter
import com.uykutakip.core.ui.components.SectionTitle
import com.uykutakip.core.ui.theme.SleepAccent
import com.uykutakip.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsRoute(
    sleep: SleepRepository,
    water: HydrationRepository,
    privateActivity: PrivateActivityRepository,
    settings: TrackerSettingsRepository,
    ideas: IdeasRepository,
    shopping: ShoppingRepository,
    onOpenPcSync: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenMcp: () -> Unit,
) {
    val config by settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheduler = remember(context) { ReminderScheduler(context) }
    val formatter = remember { TimeFormatter() }
    val scope = rememberCoroutineScope()
    var draftGoal by remember(config.goalMinutes) { mutableFloatStateOf(config.goalMinutes.toFloat()) }
    var timeEditor by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    val onBedtime: (Boolean, Int, Int) -> Unit = { enabled, hour, minute ->
        settings.updateBedtime(enabled, hour, minute)
        if (enabled) scheduler.schedule(hour, minute, 1001, "Uyku zamanı", "Yatma rutinin için biraz zaman ayır.") else scheduler.cancel(1001)
    }
    val onWake: (Boolean, Int, Int) -> Unit = { enabled, hour, minute ->
        settings.updateWake(enabled, hour, minute)
        if (enabled) scheduler.schedule(hour, minute, 1002, "Günaydın", "Yeni gün başladı. Takibini güncelleyebilirsin.") else scheduler.cancel(1002)
    }
    val export: (Uri?, Boolean) -> Unit = { uri, json ->
        if (uri != null) scope.launch {
            exporting = true
            try {
                withContext(Dispatchers.IO) {
                    val sleeps = sleep.records.first()
                    val waters = water.records.first()
                    val privateRecords = privateActivity.records.first()
                    val content = if (json) exportJson(sleeps, waters, privateRecords, ideas.ideas.first(), shopping.items.first()) else exportCsv(sleeps, waters, privateRecords)
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw java.io.IOException("Cannot open export destination")
                    stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                }
                Toast.makeText(context, "Dosya kaydedildi.", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, "Dosya kaydedilemedi.", Toast.LENGTH_LONG).show()
            } finally { exporting = false }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { export(it, false) }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { export(it, true) }

    LazyColumn(Modifier.fillMaxSize().widthIn(max = 720.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Uyku") }
        item {
            Text("Uyku hedefi", style = MaterialTheme.typography.titleMedium)
            Text(formatter.duration(draftGoal.toLong() * 60_000), color = SleepAccent)
            Slider(value = draftGoal, onValueChange = { draftGoal = it }, onValueChangeFinished = { settings.updateGoal(draftGoal.toInt()) }, valueRange = 60f..960f, steps = 29, modifier = Modifier.semantics { contentDescription = "Uyku hedefi süresi" })
        }
        item { SectionTitle("Hatırlatıcılar") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { timeEditor = 1 }, modifier = Modifier.weight(1f)) { Text("Yatma saati · %02d:%02d".format(config.bedtimeHour, config.bedtimeMinute)) }
                Switch(config.bedtimeEnabled, { onBedtime(it, config.bedtimeHour, config.bedtimeMinute) }, modifier = Modifier.semantics { contentDescription = "Yatma hatırlatıcısı" })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { timeEditor = 2 }, modifier = Modifier.weight(1f)) { Text("Kalkma saati · %02d:%02d".format(config.wakeHour, config.wakeMinute)) }
                Switch(config.wakeEnabled, { onWake(it, config.wakeHour, config.wakeMinute) }, modifier = Modifier.semantics { contentDescription = "Kalkma hatırlatıcısı" })
            }
        }
        item { HorizontalDivider() }
        item { SectionTitle("Veriler ve cihazlar") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !exporting, onClick = { csvLauncher.launch("gunluk-takip.csv") }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("CSV") }
                OutlinedButton(enabled = !exporting, onClick = { jsonLauncher.launch("gunluk-takip.json") }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("JSON") }
            }
        }
        item { OutlinedButton(onClick = onOpenPcSync, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Computer, null); Spacer(Modifier.width(8.dp)); Text("PC ile senkronize et") } }
        item { HorizontalDivider() }
        item { SectionTitle("Geliştirici") }
        item { OutlinedButton(onClick = onOpenMcp, modifier = Modifier.fillMaxWidth()) { Text("AI / MCP erişimi") } }
        item { OutlinedButton(onClick = onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.BugReport, null); Spacer(Modifier.width(8.dp)); Text("Loglar ve tanılama") } }
    }
    if (timeEditor != 0) {
        val bedtime = timeEditor == 1
        ReminderTimeDialog(if (bedtime) config.bedtimeHour else config.wakeHour, if (bedtime) config.bedtimeMinute else config.wakeMinute, { timeEditor = 0 }) { hour, minute ->
            if (bedtime) onBedtime(true, hour, minute) else onWake(true, hour, minute)
            timeEditor = 0
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(hour: Int, minute: Int, dismiss: () -> Unit, save: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(hour, minute, is24Hour = true)
    AlertDialog(onDismissRequest = dismiss, title = { Text("Hatırlatıcı saati") }, text = { TimeInput(state) }, confirmButton = { TextButton(onClick = { save(state.hour, state.minute) }) { Text("Uygula") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } })
}
