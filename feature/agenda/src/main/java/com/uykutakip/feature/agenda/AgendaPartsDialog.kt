package com.uykutakip.feature.agenda

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uykutakip.core.model.AgendaEntry
import com.uykutakip.core.model.AgendaPart
import com.uykutakip.core.model.progressPercent
import java.util.UUID
import kotlin.math.roundToInt

internal fun appendPendingPart(parts: List<AgendaPart>, title: String): List<AgendaPart> {
    if (title.isBlank()) return parts
    require(parts.size < 100 && title.length <= 200)
    return parts + AgendaPart(UUID.randomUUID().toString(), title.trim())
}

@Composable
internal fun AgendaPartsDialog(entry: AgendaEntry, dismiss: () -> Unit, save: (List<AgendaPart>) -> Unit) {
    var parts by remember(entry.id) { mutableStateOf(entry.parts) }
    var title by remember(entry.id) { mutableStateOf("") }
    var titles by remember(entry.id) { mutableStateOf(entry.parts.associate { it.id to it.title }) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 680.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Alt görevler", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Kapat") }
                }
                Text(entry.text, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                Text("%${parts.progressPercent} · ${parts.count { it.progress == 100 }}/${parts.size} tamamlandı", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(progress = { parts.progressPercent / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(parts, key = { it.id }) { part ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(part.progress == 100, { checked -> parts = parts.map { if (it.id == part.id) it.copy(progress = if (checked) 100 else 0) else it } }, Modifier.semantics { contentDescription = "${part.title} tamamlandı" })
                                val draftTitle = titles[part.id] ?: part.title
                                OutlinedTextField(draftTitle, { value ->
                                    if (value.length <= 200) {
                                        titles = titles + (part.id to value)
                                        if (value.isNotBlank()) parts = parts.map { if (it.id == part.id) it.copy(title = value) else it }
                                    }
                                }, Modifier.weight(1f), label = { Text("Alt görev") }, isError = draftTitle.isBlank(), maxLines = 3)
                                IconButton(onClick = { parts = parts.filterNot { it.id == part.id } }) { Icon(Icons.Outlined.DeleteOutline, "Alt görevi sil") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Slider(part.progress.toFloat(), { value -> parts = parts.map { if (it.id == part.id) it.copy(progress = value.roundToInt()) else it } }, Modifier.weight(1f).semantics { contentDescription = "${part.title} ilerlemesi" }, valueRange = 0f..100f)
                                Text("%${part.progress}", Modifier.width(48.dp))
                            }
                            Text(when (part.progress) { 0 -> "Başlanmadı"; 100 -> "Tamamlandı"; else -> "Devam ediyor" }, style = MaterialTheme.typography.labelSmall)
                            HorizontalDivider()
                        }
                    }
                }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(title, { if (it.length <= 200) title = it }, Modifier.weight(1f), label = { Text("Yeni alt görev") }, maxLines = 2, enabled = parts.size < 100)
                    IconButton(enabled = title.isNotBlank() && parts.size < 100, onClick = {
                        parts = appendPendingPart(parts, title)
                        title = ""
                    }) { Icon(Icons.Outlined.Add, "Alt görev ekle") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = dismiss) { Text("Vazgeç") }
                    Button(enabled = parts.all { (titles[it.id] ?: it.title).isNotBlank() }, onClick = {
                        parts = appendPendingPart(parts, title)
                        title = ""
                        save(parts)
                    }) { Text("Kaydet") }
                }
            }
        }
    }
}
