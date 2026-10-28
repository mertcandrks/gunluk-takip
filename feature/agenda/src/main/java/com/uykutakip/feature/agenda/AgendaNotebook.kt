package com.uykutakip.feature.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uykutakip.core.model.AgendaEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal data class NotebookDay(val date: LocalDate, val entries: List<AgendaEntry>)

internal fun notebookDay(date: LocalDate, entries: List<AgendaEntry>) = NotebookDay(date,
    entries.filter { !it.isGeneralGoal && (!it.repeatDaily || it.day <= date.toString()) }
        .map { if (it.repeatDaily) it.copy(day = date.toString()) else it })

internal fun notebookDates(day: LocalDate): List<LocalDate> {
    val start = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return List(7) { start.plusDays(it.toLong()) }
}

@Composable
internal fun AgendaNotebook(
    day: LocalDate,
    days: List<NotebookDay>,
    select: (LocalDate) -> Unit,
    close: () -> Unit,
    add: (LocalDate, Boolean) -> Unit,
    edit: (AgendaEntry) -> Unit,
    complete: (AgendaEntry) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val paper = if (dark) Color(0xFF202123) else Color(0xFFFCFCFA)
    val ink = if (dark) Color(0xFFF0F1F2) else Color(0xFF27292D)
    val divider = ink.copy(alpha = 0.12f)
    val texture = ImageBitmap.imageResource(R.drawable.notebook_paper)
    val dates = remember(day) { notebookDates(day) }
    val label = remember { DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr-TR")) }
    Surface(Modifier.fillMaxSize(), color = paper, contentColor = ink) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Defter", fontFamily = FontFamily.SansSerif, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                TextButton(onClick = close) { Icon(Icons.Outlined.ViewAgenda, null); Spacer(Modifier.width(6.dp)); Text("Ajanda") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { select(day.minusWeeks(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Önceki hafta") }
                Text("${dates.first().format(label)} – ${dates.last().format(label)} ${dates.last().year}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { select(LocalDate.now()) }) { Text("Bugün") }
                IconButton(onClick = { select(day.plusWeeks(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Sonraki hafta") }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = if (maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.25f) 2 else 1
                val pages = days.ifEmpty { dates.map { NotebookDay(it, emptyList()) } }.chunked(columns)
                LazyColumn(
                    Modifier.fillMaxSize().drawWithCache {
                        val brush = ShaderBrush(ImageShader(texture, TileMode.Repeated, TileMode.Repeated))
                        onDrawBehind {
                            drawRect(brush, alpha = if (dark) 0.06f else 0.35f)
                            if (columns == 2) drawLine(divider, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
                        }
                    },
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    items(pages, key = { it.first().date.toString() }) { pair ->
                        Row(Modifier.fillMaxWidth()) {
                            pair.forEach { page ->
                                Column(Modifier.weight(1f).heightIn(min = 200.dp).padding(horizontal = 12.dp, vertical = 16.dp)) {
                                    NotebookDate(page.date, add)
                                    HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = divider)
                                    page.entries.forEach { entry -> NotebookLine(entry, edit, complete, ink) }
                                    if (page.entries.isEmpty()) Text("Henüz kayıt yok", modifier = Modifier.padding(8.dp),
                                        color = ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (pair.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (days.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun NotebookDate(date: LocalDate, add: (LocalDate, Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("d.MM.yyyy")), fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(date.format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("tr-TR"))), style = MaterialTheme.typography.labelSmall)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Add, "${date} tarihine ekle") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Görev") }, leadingIcon = { Icon(Icons.Outlined.Check, null) },
                    onClick = { menu = false; add(date, true) })
                DropdownMenuItem(text = { Text("Not") }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                    onClick = { menu = false; add(date, false) })
            }
        }
    }
}

@Composable
private fun NotebookLine(entry: AgendaEntry, edit: (AgendaEntry) -> Unit, complete: (AgendaEntry) -> Unit, ink: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).clickable(onClickLabel = "Kaydı düzenle") { edit(entry) }.padding(start = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Text(entry.text, fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp)
            if (entry.repeatDaily) Text("${entry.dailyCount} kez", style = MaterialTheme.typography.labelSmall)
            if (entry.notDone) Text("Yapılmadı", style = MaterialTheme.typography.labelSmall)
            if (entry.parts.isNotEmpty()) Text("${entry.parts.count { it.progress == 100 }}/${entry.parts.size} alt görev", style = MaterialTheme.typography.labelSmall)
        }
        if (entry.isTask) IconButton(onClick = { complete(entry) }) {
            Icon(if (entry.completed) Icons.Outlined.Check else if (entry.parts.isNotEmpty()) Icons.Outlined.AccountTree else Icons.Outlined.RadioButtonUnchecked,
                if (entry.parts.isNotEmpty()) "Alt görevleri düzenle" else if (entry.repeatDaily) "Bir kez yaptım" else if (entry.completed) "Görevi yeniden aç" else "Görevi tamamla",
                tint = if (entry.completed) ink else ink.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
        }
    }
}
