package com.uykutakip.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uykutakip.core.time.TimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun DateTimeButton(label: String, millis: Long, formatter: TimeFormatter, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Schedule, null)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${formatter.shortDate(millis)}  ${formatter.time(millis)}")
        }
    }
}

@Composable
fun rememberDateTimePicker(): (Long, (Long) -> Unit) -> Unit {
    var request by remember { mutableStateOf<Pair<Long, (Long) -> Unit>?>(null) }
    request?.let { (initial, callback) ->
        TrackerDateTimeDialog(initial, { request = null }) {
            request = null
            callback(it)
        }
    }
    return { initial, callback -> request = initial to callback }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerDateTimeDialog(initialMillis: Long, onDismiss: () -> Unit, onSelected: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = remember(initialMillis) { Instant.ofEpochMilli(initialMillis).atZone(zone) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val timeState = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    var choosingTime by rememberSaveable { mutableStateOf(false) }
    if (!choosingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { choosingTime = true }, enabled = dateState.selectedDateMillis != null) { Text("Saat seç") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
        ) {
            DatePicker(state = dateState, title = { Text("Tarih seç", Modifier.padding(24.dp), style = MaterialTheme.typography.titleMedium) })
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Saat seç") },
            text = { TimeInput(state = timeState) },
            confirmButton = { TextButton(onClick = {
                // Calendar picker values use UTC midnight; preserve the local calendar day.
                val date = Instant.ofEpochMilli(dateState.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                onSelected(date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant().toEpochMilli())
            }) { Text("Uygula") } },
            dismissButton = { TextButton(onClick = { choosingTime = false }) { Text("Tarihe dön") } },
        )
    }
}
