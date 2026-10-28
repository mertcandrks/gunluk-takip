package com.uykutakip.feature.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uykutakip.core.model.SleepRecord
import com.uykutakip.core.time.TimeFormatter
import com.uykutakip.core.ui.components.MetricCard
import com.uykutakip.core.ui.components.SectionTitle
import com.uykutakip.core.ui.components.TrackerCard
import com.uykutakip.core.ui.theme.AppSurfaceHigh
import com.uykutakip.core.ui.theme.SleepAccent
import com.uykutakip.core.ui.theme.TextSecondary
import com.uykutakip.data.*

@Composable
fun StatsRoute(sleep: SleepRepository, settings: TrackerSettingsRepository) {
    val vm: StatsViewModel = viewModel(factory = remember(sleep, settings) { StatsViewModelFactory(sleep, settings) })
    val state by vm.state.collectAsStateWithLifecycle()
    StatsScreen(state)
}

@Composable
private fun StatsScreen(state: StatsState) {
    val formatter = remember { TimeFormatter() }
    var period by rememberSaveable { mutableIntStateOf(0) }
    val periodDays = if (period == 0) 7 else 30
    val cutoff = remember(period) { System.currentTimeMillis() - periodDays * 86_400_000L }
    val periodSleep = remember(state.sleep, cutoff) { state.sleep.filter { it.startMillis >= cutoff } }
    val totalSleep = remember(periodSleep) { periodSleep.sumOf { it.durationMillis } }
    val average = if (periodSleep.isEmpty()) 0L else totalSleep / periodSleep.size
    val progress = (average / 60_000f / state.sleepGoalMinutes).coerceIn(0f, 1f)
    val bestSleep = remember(periodSleep) { periodSleep.maxByOrNull { it.durationMillis } }
    val weakestSleep = remember(periodSleep) { periodSleep.minByOrNull { it.durationMillis } }
    LazyColumn(Modifier.fillMaxSize().widthIn(max = 720.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("GÜNLÜK TAKİP", color = SleepAccent, style = MaterialTheme.typography.labelMedium); Text("İstatistikler", style = MaterialTheme.typography.headlineLarge); Text("Günlük düzeninin kısa özeti", color = TextSecondary) }
        item { SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf("Haftalık", "Aylık").forEachIndexed { index, label -> SegmentedButton(selected = period == index, onClick = { period = index }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) } } } }
        item { SectionTitle("Dönem özeti") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Ortalama uyku", formatter.duration(average), SleepAccent, Modifier.weight(1f)); MetricCard("Toplam uyku", formatter.duration(totalSleep), SleepAccent, Modifier.weight(1f)) } }
        item { TrackerCard(containerColor = AppSurfaceHigh) { Text("Uyku hedefi", color = TextSecondary); Text("${formatter.duration(average)} / ${formatter.duration(state.sleepGoalMinutes * 60_000L)}", style = MaterialTheme.typography.titleLarge, color = SleepAccent); Spacer(Modifier.height(10.dp)); LinearProgressIndicator({ progress }, Modifier.fillMaxWidth(), color = SleepAccent); Spacer(Modifier.height(6.dp)); Text("${(progress * 100).toInt()}% hedefe ulaşıldı", color = TextSecondary) } }
        item {
            TrackerCard {
                Text("Uyku dengesi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                SleepBalanceRow("En iyi gün", bestSleep, formatter)
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SleepBalanceRow("En zayıf gün", weakestSleep, formatter)
            }
        }
    }
}

@Composable
private fun SleepBalanceRow(label: String, record: SleepRecord?, formatter: TimeFormatter) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text(
            record?.let {
                val date = formatter.localDate(it.startMillis)
                "%02d.%02d · %s".format(date.dayOfMonth, date.monthValue, formatter.duration(it.durationMillis))
            } ?: "Kayıt yok",
            color = if (record == null) TextSecondary else SleepAccent,
            maxLines = 1,
        )
    }
}
