package com.uykutakip.feature.agenda

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uykutakip.data.AgendaTaskSuggestion

@Composable
internal fun AgendaBatchDialog(
    suggestions: List<AgendaTaskSuggestion>,
    dismiss: () -> Unit,
    save: (List<AgendaTaskSuggestion>, (Boolean) -> Unit) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var drafts by remember { mutableStateOf<List<AgendaTaskSuggestion>?>(null) }
    var saving by remember { mutableStateOf(false) }
    val rows = drafts.orEmpty()
    val valid = rows.isNotEmpty() && rows.size <= 100 && rows.all {
        it.text.isNotBlank() && it.text.length <= 4000 && it.category.length <= 60 && it.subcategory.length <= 60
    }
    AlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text(if (drafts == null) "Toplu görev ekle" else "${rows.size} görev") },
        text = {
            if (drafts == null) OutlinedTextField(input, { if (it.length <= 20000) input = it },
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), minLines = 6, maxLines = 12,
                label = { Text("Görevler") }, placeholder = { Text("Market alışverişi\nKitap oku\nYürüyüş yap") })
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(rows) { index, task ->
                    fun update(value: AgendaTaskSuggestion) { drafts = rows.toMutableList().also { it[index] = value } }
                    Column {
                        Row {
                            OutlinedTextField(task.text, { update(task.copy(text = it)) }, enabled = !saving,
                                modifier = Modifier.weight(1f), label = { Text("Görev ${index + 1}") })
                            IconButton(onClick = { drafts = rows.filterIndexed { i, _ -> i != index } }, enabled = !saving) {
                                Icon(Icons.Outlined.DeleteOutline, "Taslağı kaldır")
                            }
                        }
                        OutlinedTextField(task.category, { if (it.length <= 60) update(task.copy(category = it)) },
                            label = { Text("Kategori") }, enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(task.subcategory, { if (it.length <= 60) update(task.copy(subcategory = it)) },
                            label = { Text("Alt kategori") }, enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && (if (drafts == null) input.isNotBlank() && taskLines(input, suggestions).size <= 100 else valid),
                onClick = {
                    if (drafts == null) drafts = taskLines(input, suggestions)
                    else { saving = true; save(rows) { success -> saving = false; if (success) dismiss() } }
                }) { Text(if (drafts == null) "Önizle" else "Tümünü kaydet") }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !saving) { Text("Vazgeç") } },
    )
}
