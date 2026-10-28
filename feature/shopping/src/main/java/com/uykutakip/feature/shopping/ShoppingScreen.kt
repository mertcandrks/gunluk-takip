package com.uykutakip.feature.shopping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uykutakip.core.model.ShoppingItem
import com.uykutakip.core.ui.components.EmptyState
import com.uykutakip.core.ui.theme.TextSecondary
import com.uykutakip.core.ui.theme.WarmAccent
import com.uykutakip.data.ShoppingRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ShoppingRoute(repository: ShoppingRepository) {
    val vm: ShoppingViewModel = viewModel(factory = remember(repository) { ShoppingViewModelFactory(repository) })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        ShoppingScreen(state, vm::save, vm::toggle, vm::delete, Modifier.padding(padding))
    }
}

@Composable
private fun ShoppingScreen(state: ShoppingState, save: (Long, String, Boolean, () -> Unit) -> Unit, toggle: (Long) -> Unit, delete: (Long, () -> Unit) -> Unit, modifier: Modifier = Modifier) {
    var longTerm by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var draftLongTerm by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ShoppingItem?>(null) }
    val visible = state.items.filter { it.longTerm == longTerm }
    val pending = visible.filterNot { it.purchased }
    val purchased = visible.filter { it.purchased }
    val today = LocalDate.now()
    val dayFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr-TR"))
    fun dayLabel(date: LocalDate) = when (date) {
        today -> "Bugün"
        today.minusDays(1) -> "Dün"
        else -> date.format(dayFormatter)
    }
    fun groupByDay(items: List<ShoppingItem>) = items
        .groupBy { Instant.ofEpochMilli(it.createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDate() }
        .toSortedMap(compareByDescending<LocalDate> { it })
    val edit: (ShoppingItem?) -> Unit = { item ->
        draft = item?.text.orEmpty(); draftLongTerm = item?.longTerm ?: longTerm; editingId = item?.id ?: 0L
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Alınacaklar", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { edit(null) }) { Icon(Icons.Outlined.Add, "Listeye ekle", tint = WarmAccent) }
            }
        }
        item { HorizonPicker(longTerm) { longTerm = it } }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (visible.isEmpty()) item { EmptyState("Bu listede ürün yok", Icons.Outlined.ShoppingCart) }
        if (pending.isNotEmpty()) item { Text("${pending.size} alınacak", style = MaterialTheme.typography.labelMedium, color = TextSecondary) }
        groupByDay(pending).forEach { (date, dayItems) ->
            item(key = "pending-$date") { Text(dayLabel(date), style = MaterialTheme.typography.titleSmall, color = WarmAccent) }
            items(dayItems, key = { it.id }) { item -> ShoppingRow(item, state.saving, { toggle(item.id) }, { edit(item) }, { deleting = item }) }
        }
        if (purchased.isNotEmpty()) item { HorizontalDivider(); Text("Alınanlar · ${purchased.size}", style = MaterialTheme.typography.titleMedium) }
        groupByDay(purchased).forEach { (date, dayItems) ->
            item(key = "purchased-$date") { Text(dayLabel(date), style = MaterialTheme.typography.titleSmall, color = WarmAccent) }
            items(dayItems, key = { it.id }) { item -> ShoppingRow(item, state.saving, { toggle(item.id) }, { edit(item) }, { deleting = item }) }
        }
    }
    editingId?.let { id ->
        AlertDialog(onDismissRequest = { if (!state.saving) editingId = null }, title = { Text(if (id == 0L) "Alınacak ekle" else "Alınacağı düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(draft, { if (it.length <= 2000) draft = it }, label = { Text("Ürün") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, enabled = !state.saving)
                    HorizonPicker(draftLongTerm, enabled = !state.saving) { draftLongTerm = it }
                }
            },
            confirmButton = { TextButton(enabled = draft.isNotBlank() && !state.saving, onClick = { save(id, draft, draftLongTerm) { editingId = null } }) { Text("Kaydet") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { editingId = null }) { Text("Vazgeç") } })
    }
    deleting?.let { item ->
        AlertDialog(onDismissRequest = { if (!state.saving) deleting = null }, title = { Text("Listeden silinsin mi?") }, text = { Text(item.text.take(200)) },
            confirmButton = { TextButton(enabled = !state.saving, onClick = { delete(item.id) { deleting = null } }) { Text("Sil") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { deleting = null }) { Text("Vazgeç") } })
    }
}

@Composable
private fun HorizonPicker(longTerm: Boolean, enabled: Boolean = true, select: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf("Kısa vadeli", "Uzun vadeli").forEachIndexed { index, label ->
            SegmentedButton(selected = longTerm == (index == 1), onClick = { select(index == 1) }, enabled = enabled, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
        }
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItem, busy: Boolean, toggle: () -> Unit, edit: () -> Unit, delete: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.purchased, { toggle() }, enabled = !busy, modifier = Modifier.semantics { contentDescription = if (item.purchased) "${item.text}: alınmadı yap" else "${item.text}: aldım" })
            Text(item.text, modifier = Modifier.weight(1f), textDecoration = if (item.purchased) TextDecoration.LineThrough else TextDecoration.None, color = if (item.purchased) TextSecondary else MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = edit, enabled = !busy) { Icon(Icons.Outlined.Edit, "Ürünü düzenle") }
            IconButton(onClick = delete, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "Ürünü sil") }
        }
        HorizontalDivider()
    }
}
