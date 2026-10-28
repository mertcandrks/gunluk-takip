package com.uykutakip.feature.hydration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uykutakip.core.model.WaterIntake
import com.uykutakip.core.time.TimeFormatter
import com.uykutakip.core.ui.components.EmptyState
import com.uykutakip.core.ui.components.MetricCard
import com.uykutakip.core.ui.components.SectionTitle
import com.uykutakip.core.ui.components.TrackerCard
import com.uykutakip.core.ui.theme.AppSurfaceHigh
import com.uykutakip.core.ui.theme.TextSecondary
import com.uykutakip.core.ui.theme.WaterAccent

private val QuickAmounts = listOf(200, 250, 330, 500)

@Composable
fun HydrationScreen(
    records: List<WaterIntake>,
    dailyGoalMl: Int,
    formatter: TimeFormatter,
    onAdd: (Int) -> Unit,
    onUpdateGoal: (Int) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val analytics = remember { HydrationAnalytics() }
    val todayRecords = analytics.recordsForDay(records)
    val summary = analytics.todaySummary(records, dailyGoalMl)
    var showCustomAmount by remember { mutableStateOf(false) }
    var showGoalEditor by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("GÜNLÜK TAKİP", style = MaterialTheme.typography.labelMedium, color = WaterAccent)
                Spacer(Modifier.height(5.dp))
                Text("Su", style = MaterialTheme.typography.headlineLarge)
                Text(formatter.fullDate(System.currentTimeMillis()), color = TextSecondary)
            }

            item {
                TrackerCard(containerColor = AppSurfaceHigh) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Bugünkü su", color = TextSecondary)
                            Text(
                                "${summary.totalMl} ml",
                                style = MaterialTheme.typography.displayMedium,
                                color = WaterAccent,
                            )
                        }
                        Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = WaterAccent)
                    }
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { summary.progress },
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = WaterAccent,
                        trackColor = Color.White.copy(alpha = 0.08f),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Hedef: $dailyGoalMl ml", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(
                            if (summary.remainingMl > 0) "${summary.remainingMl} ml kaldı" else "Hedef tamamlandı",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WaterAccent,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("İçim", summary.servingCount.toString(), WaterAccent, Modifier.weight(1f))
                    MetricCard("Tamamlanan", "%${(summary.progress * 100).toInt()}", WaterAccent, Modifier.weight(1f))
                }
            }

            item { SectionTitle("Hızlı ekle") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = { onAdd(125) },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppSurfaceHigh,
                            contentColor = WaterAccent,
                        ),
                    ) {
                        Icon(Icons.Outlined.WaterDrop, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("Yarım bardak · 125 ml")
                    }
                    QuickAmounts.chunked(2).forEach { rowAmounts ->
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            rowAmounts.forEach { amount ->
                                Button(
                                    onClick = { onAdd(amount) },
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WaterAccent,
                                        contentColor = Color(0xFF002633),
                                    ),
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("$amount ml")
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(
                            onClick = { showCustomAmount = true },
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) { Text("Özel miktar") }
                        OutlinedButton(
                            onClick = { showGoalEditor = true },
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Hedef")
                        }
                    }
                }
            }

            item {
                SectionTitle("Bugünün kayıtları") {
                    if (records.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirmation = true }) { Text("Tümünü temizle") }
                    }
                }
            }
            if (todayRecords.isEmpty()) {
                item { EmptyState("Bugün henüz su kaydı yok", Icons.Outlined.WaterDrop) }
            } else {
                items(todayRecords, key = { it.id }) { record ->
                    WaterRecordRow(record, formatter, onDelete)
                }
            }
        }
    }

    if (showCustomAmount) {
        NumberEditorDialog(
            title = "Özel miktar",
            label = "Miktar (ml)",
            initialValue = "",
            validRange = 10..5_000,
            onDismiss = { showCustomAmount = false },
            onConfirm = {
                onAdd(it)
                showCustomAmount = false
            },
        )
    }
    if (showGoalEditor) {
        NumberEditorDialog(
            title = "Günlük su hedefi",
            label = "Hedef (ml)",
            initialValue = dailyGoalMl.toString(),
            validRange = 500..10_000,
            onDismiss = { showGoalEditor = false },
            onConfirm = {
                onUpdateGoal(it)
                showGoalEditor = false
            },
        )
    }
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Su geçmişini temizle") },
            text = { Text("Tüm su kayıtları kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirmation = false
                }) { Text("Temizle") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun WaterRecordRow(
    record: WaterIntake,
    formatter: TimeFormatter,
    onDelete: (Long) -> Unit,
) {
    TrackerCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = WaterAccent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${record.amountMl} ml", fontWeight = FontWeight.SemiBold)
                Text(formatter.time(record.timestampMillis), color = TextSecondary)
            }
            IconButton(onClick = { onDelete(record.id) }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Su kaydını sil", tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun NumberEditorDialog(
    title: String,
    label: String,
    initialValue: String,
    validRange: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val parsed = value.toIntOrNull()
    val isValid = parsed != null && parsed in validRange

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { input -> value = input.filter(Char::isDigit).take(5) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                suffix = { Text("ml") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsed!!) }, enabled = isValid) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
