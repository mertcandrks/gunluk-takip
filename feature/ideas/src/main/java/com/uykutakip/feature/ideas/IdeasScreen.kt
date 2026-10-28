package com.uykutakip.feature.ideas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uykutakip.core.model.Idea
import com.uykutakip.core.time.TimeFormatter
import com.uykutakip.core.ui.components.EmptyState
import com.uykutakip.core.ui.components.TrackerCard
import com.uykutakip.core.ui.theme.TextSecondary
import com.uykutakip.core.ui.theme.WarmAccent
import com.uykutakip.data.IdeasRepository
import java.time.temporal.ChronoUnit

@Composable
fun IdeasRoute(repository: IdeasRepository) {
    val vm: IdeasViewModel = viewModel(factory = remember(repository) { IdeasViewModelFactory(repository) })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    IdeasScreen(state, vm::save, vm::delete, vm::finish, snackbar)
}

@Composable
private fun IdeasScreen(
    state: IdeasState,
    save: (Long, String, () -> Unit) -> Unit,
    delete: (Long, () -> Unit) -> Unit,
    finish: (Long, () -> Unit) -> Unit,
    snackbar: SnackbarHostState,
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Idea?>(null) }
    var finishing by remember { mutableStateOf<Idea?>(null) }
    val formatter = remember { TimeFormatter() }
    val today = formatter.today()
    val edit: (Idea?) -> Unit = { idea -> draft = idea?.text.orEmpty(); editingId = idea?.id ?: 0L }
    val pending = state.ideas.filter { it.completedAtMillis == null }
    val completed = state.ideas.filter { it.completedAtMillis != null }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { edit(null) }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Yeni fikir") })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("${pending.size} bekleyen fikir", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (state.ideas.isEmpty()) item { EmptyState("Henüz fikir yok", Icons.Outlined.Lightbulb) }
            items(pending, key = { it.id }) { idea ->
                IdeaRow(idea, formatter, today, state.saving, { edit(idea) }, { deleting = idea }, { finishing = idea })
            }
            if (completed.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("Yapılan fikirler · ${completed.size}", style = MaterialTheme.typography.titleMedium)
                }
                items(completed, key = { it.id }) { idea ->
                    IdeaRow(idea, formatter, today, state.saving, { edit(idea) }, { deleting = idea }, null)
                }
            }
        }
    }
    editingId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!state.saving) editingId = null },
            title = { Text(if (id == 0L) "Yeni fikir" else "Fikri düzenle") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(draft, { if (it.length <= 20_000) draft = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Fikir") }, minLines = 5, maxLines = 12, enabled = !state.saving)
                }
            },
            confirmButton = { TextButton(enabled = draft.isNotBlank() && !state.saving, onClick = { save(id, draft) { editingId = null } }) { Text("Kaydet") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { editingId = null }) { Text("Vazgeç") } },
        )
    }
    deleting?.let { idea ->
        AlertDialog(onDismissRequest = { if (!state.saving) deleting = null }, title = { Text("Fikir silinsin mi?") }, text = { Text(idea.text.take(200)) },
            confirmButton = { TextButton(enabled = !state.saving, onClick = { delete(idea.id) { deleting = null } }) { Text("Sil") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { deleting = null }) { Text("Vazgeç") } })
    }
    finishing?.let { idea ->
        AlertDialog(
            onDismissRequest = { if (!state.saving) finishing = null },
            title = { Text("Fikir tamamlandı mı?") },
            text = { Text("Bu fikir bugünün Ajanda > Yaptıklarım bölümüne eklenecek.") },
            confirmButton = { TextButton(enabled = !state.saving, onClick = { finish(idea.id) { finishing = null } }) { Text("Yaptım") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { finishing = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun IdeaRow(
    idea: Idea,
    formatter: TimeFormatter,
    today: java.time.LocalDate,
    busy: Boolean,
    edit: () -> Unit,
    delete: () -> Unit,
    finish: (() -> Unit)?,
) {
    val age = ChronoUnit.DAYS.between(formatter.localDate(idea.createdAtMillis), today).coerceAtLeast(0)
    val ageLabel = when (age) { 0L -> "Bugün"; 1L -> "Dün"; else -> "$age gün önce" }
    TrackerCard {
        Column(Modifier.fillMaxWidth().clickable(onClick = edit)) {
            Text(idea.text.lineSequence().first(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detail = idea.text.substringAfter('\n', "").trim()
            if (detail.isNotEmpty()) Text(detail, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Yazıldı: ${formatter.shortDate(idea.createdAtMillis)}, ${formatter.time(idea.createdAtMillis)} · $ageLabel", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f))
            IconButton(onClick = edit, enabled = !busy) { Icon(Icons.Outlined.Edit, "Fikri düzenle") }
            IconButton(onClick = delete, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "Fikri sil") }
        }
        val completedAt = idea.completedAtMillis
        if (completedAt != null) {
            Text("Yapıldı: ${formatter.shortDate(completedAt)}, ${formatter.time(completedAt)}", style = MaterialTheme.typography.labelMedium, color = WarmAccent)
        } else if (finish != null) {
            FilledTonalButton(onClick = finish, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Yaptım · Ajandaya ekle")
            }
        }
    }
}
