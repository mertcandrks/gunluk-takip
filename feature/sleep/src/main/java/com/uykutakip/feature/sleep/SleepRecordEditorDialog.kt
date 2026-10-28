package com.uykutakip.feature.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.uykutakip.core.time.TimeFormatter
import com.uykutakip.core.ui.components.DateTimeButton
import com.uykutakip.core.ui.components.rememberDateTimePicker
import com.uykutakip.core.ui.theme.CoralAccent

@Composable
fun SleepRecordEditorDialog(
    title: String,
    initialStartMillis: Long,
    initialEndMillis: Long,
    formatter: TimeFormatter,
    onDismiss: () -> Unit,
    onSave: (startMillis: Long, endMillis: Long) -> Unit,
) {
    val showDateTimePicker = rememberDateTimePicker()
    var startMillis by remember(initialStartMillis) { mutableLongStateOf(initialStartMillis) }
    var endMillis by remember(initialEndMillis) { mutableLongStateOf(initialEndMillis) }
    val isValid = startMillis > 0L && endMillis > startMillis && endMillis <= System.currentTimeMillis() + 60_000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateTimeButton(
                    label = "Uyku başlangıcı",
                    millis = startMillis,
                    formatter = formatter,
                    onClick = { showDateTimePicker(startMillis) { startMillis = it } },
                )
                DateTimeButton(
                    label = "Uyanma zamanı",
                    millis = endMillis,
                    formatter = formatter,
                    onClick = { showDateTimePicker(endMillis) { endMillis = it } },
                )
                if (!isValid) {
                    Text(
                        "Uyanma zamanı başlangıçtan sonra ve gelecekte olmayacak şekilde seçilmeli.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoralAccent,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(startMillis, endMillis) }, enabled = isValid) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

