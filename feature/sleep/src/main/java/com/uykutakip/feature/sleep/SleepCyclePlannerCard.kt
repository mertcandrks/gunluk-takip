package com.uykutakip.feature.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uykutakip.core.time.TimeFormatter
import com.uykutakip.core.ui.components.TrackerCard
import com.uykutakip.core.ui.theme.AppSurfaceHigh
import com.uykutakip.core.ui.theme.SleepAccent
import com.uykutakip.core.ui.theme.TextSecondary

@Composable
internal fun SleepCyclePlannerCard(
    bedtimeMillis: Long,
    isSleeping: Boolean,
    formatter: TimeFormatter,
) {
    val options = remember(bedtimeMillis) { SleepCycleCalculator.calculate(bedtimeMillis) }

    TrackerCard(containerColor = AppSurfaceHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = SleepAccent)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Uyku döngüsü", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isSleeping) "Bu uyku için tahmini kalkış saatleri" else "Şimdi yatarsan tahmini kalkış saatleri",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        options.forEachIndexed { index, option ->
            SleepCycleOptionRow(option, formatter, recommended = option.cycleCount == 5)
            if (index < options.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = Color.White.copy(alpha = 0.06f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "15 dk dalma süresi ve 90 dk döngü varsayımıdır. Uyku evreleri kişiden kişiye değişir.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SleepCycleOptionRow(
    option: SleepCycleOption,
    formatter: TimeFormatter,
    recommended: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "${option.cycleCount} döngü",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (recommended) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                "${option.sleepMinutes / 60} sa ${option.sleepMinutes % 60} dk uyku",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatter.time(option.wakeTimeMillis),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SleepAccent,
            )
            if (recommended) {
                Text("Dengeli", style = MaterialTheme.typography.labelSmall, color = SleepAccent)
            }
        }
    }
}
