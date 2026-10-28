package com.uykutakip.feature.agenda

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uykutakip.core.model.AgendaEntry
import com.uykutakip.core.model.AgendaPart
import com.uykutakip.core.model.progressPercent
import com.uykutakip.data.AgendaTaskSuggestion
import com.uykutakip.core.ui.theme.WarmAccent
import com.uykutakip.data.AgendaRepository
import com.uykutakip.data.ReminderScheduler
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AgendaRoute(repository: AgendaRepository, scheduler: ReminderScheduler) {
    val factory = remember(repository) { AgendaViewModelFactory(repository) }
    val vm: AgendaViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    var notebookMode by rememberSaveable { mutableStateOf(false) }
    val notebookDays = if (notebookMode) vm.notebook.collectAsStateWithLifecycle().value else emptyList()
    val context = LocalContext.current
    var importedText by rememberSaveable { mutableStateOf("") }
    var showSmartAdd by rememberSaveable { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
        }.getOrDefault("")
        importedText = imported.take(8000)
        if (importedText.isBlank()) Toast.makeText(context, "Dosyada okunabilir metin bulunamadı.", Toast.LENGTH_SHORT).show()
        else showSmartAdd = true
    }
    LaunchedEffect(vm) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    val add: (LocalDate, String, Boolean, Boolean, Boolean, Int, Int, String, String, () -> Unit) -> Unit = { date, text, task, repeat, notify, hour, minute, category, subcategory, done ->
        vm.add(date, text, task, repeat, notify, hour, minute, category, subcategory) {
            if (notify) {
                val requestCode = "$date|$text|$hour:$minute".hashCode()
                if (repeat) scheduler.schedule(hour, minute, requestCode, "Ajanda görevi", text)
                else scheduler.scheduleOnce(date, hour, minute, requestCode, "Ajanda görevi", text)
            }
            done()
        }
    }
    val smartAdd: (List<AgendaDraft>, () -> Unit) -> Unit = { entries, done ->
        vm.addAll(entries) {
            entries.filter { it.selected && it.notifyEnabled }.forEach { entry ->
                scheduler.scheduleOnce(
                    entry.day,
                    entry.notifyHour,
                    entry.notifyMinute,
                    "${entry.day}|${entry.text}|${entry.notifyHour}:${entry.notifyMinute}".hashCode(),
                    "Ajanda görevi",
                    entry.text,
                )
            }
            done()
        }
    }
    AgendaScreen(
        state, vm::select, add, vm::addGeneralGoal, smartAdd, vm::edit,
        { entry -> vm.complete(entry, if (notebookMode) LocalDate.parse(entry.day) else state.day) }, { entry -> if (entry.repeatDaily) vm.decrement(entry, state.day) else vm.markNotDone(entry) }, vm::promoteToGeneralGoal,
        { entry -> vm.moveGeneralGoalToDay(entry, state.day) },
        { entry -> vm.postponeToTomorrow(entry, state.day) }, vm::delete,
        vm::saveTaskTemplate, vm::setTaskTemplateFavorite, vm::removeTaskTemplate, vm::saveCategory, vm::removeCategory, vm::hideTaskSuggestion,
        saveParts = vm::saveParts,
        addTasks = vm::addTasks,
        setTimer = vm::setTimer,
        finishTask = vm::finishTask,
        mergeTasks = { sourceId, targetId, done ->
            val source = state.entries.firstOrNull { it.id == sourceId }
            vm.mergeTasks(sourceId, targetId) {
                if (source?.notifyEnabled == true) scheduler.cancel("${source.day}|${source.text}|${source.notifyHour}:${source.notifyMinute}".hashCode())
                done()
            }
        },
        importNotes = { importLauncher.launch(arrayOf("text/plain", "text/csv", "application/json", "text/*")) },
        showSmartAdd = showSmartAdd,
        importedText = importedText,
        openSmartAdd = { showSmartAdd = true },
        closeSmartAdd = { showSmartAdd = false; importedText = "" },
        notebookMode = notebookMode,
        notebookDays = notebookDays,
        toggleNotebook = { notebookMode = !notebookMode },
        adjustPriority = vm::adjustPriority,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaScreen(
    state: AgendaState,
    select: (LocalDate) -> Unit,
    add: (LocalDate, String, Boolean, Boolean, Boolean, Int, Int, String, String, () -> Unit) -> Unit,
    addGeneralGoal: (LocalDate, String, () -> Unit) -> Unit,
    smartAdd: (List<AgendaDraft>, () -> Unit) -> Unit,
    edit: (Long, String, String, String, () -> Unit) -> Unit,
    complete: (AgendaEntry) -> Unit,
    markNotDone: (AgendaEntry) -> Unit,
    promoteToGeneralGoal: (AgendaEntry) -> Unit,
    moveGeneralGoalToDay: (AgendaEntry) -> Unit,
    postponeToTomorrow: (AgendaEntry) -> Unit,
    delete: (Long) -> Unit,
    saveTaskTemplate: (String, String, String, Boolean) -> Unit,
    setTaskTemplateFavorite: (String, Boolean) -> Unit,
    removeTaskTemplate: (String) -> Unit,
    saveCategory: (String, String) -> Unit,
    removeCategory: (Long) -> Unit,
    hideTaskSuggestion: (String, String, String) -> Unit,
    saveParts: (Long, List<AgendaPart>, () -> Unit) -> Unit,
    addTasks: (List<AgendaTaskSuggestion>, (Boolean) -> Unit) -> Unit,
    setTimer: (AgendaEntry) -> Unit,
    finishTask: (AgendaEntry) -> Unit,
    mergeTasks: (Long, Long, () -> Unit) -> Unit,
    importNotes: () -> Unit,
    showSmartAdd: Boolean,
    importedText: String,
    openSmartAdd: () -> Unit,
    closeSmartAdd: () -> Unit,
    notebookMode: Boolean,
    notebookDays: List<NotebookDay>,
    toggleNotebook: () -> Unit,
    adjustPriority: (AgendaEntry, Int) -> Unit,
) {
    var editor by remember { mutableStateOf<AgendaEntry?>(null) }
    var projectEditor by remember { mutableStateOf<AgendaEntry?>(null) }
    var deleting by remember { mutableStateOf<AgendaEntry?>(null) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    var showBatch by rememberSaveable { mutableStateOf(false) }
    var merging by remember { mutableStateOf<Pair<AgendaEntry, AgendaEntry>?>(null) }
    val taskBounds = remember { mutableMapOf<Long, Rect>() }
    var draggedEntry by remember { mutableStateOf<AgendaEntry?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dropTargetBounds by remember { mutableStateOf(Rect.Zero) }
    val dateLabel = state.day.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr-TR")))
    val generalGoals = state.entries.filter { it.isGeneralGoal && !it.completed }
    val finishedProjects = state.entries.filter { it.isGeneralGoal && it.completed && it.parts.isNotEmpty() }
    val dailyTasks = state.entries.filter { it.isTask && it.repeatDaily && it.day <= state.day.toString() }
    val tasks = state.entries.filter { it.isTask && !it.isGeneralGoal && !it.repeatDaily }
    val plannedTasks = tasks.filterNot { it.completed || it.notDone }.sortedWith(compareByDescending<AgendaEntry> { it.priority }.thenBy { it.id })
    val notDoneTasks = tasks.filter { it.notDone }
    val completedTasks = tasks.filter { it.completed && !it.notDone }
    val notes = state.entries.filter { !it.isTask }
    val mergeCandidates = plannedTasks + generalGoals
    fun mergeTarget(pointer: Offset, source: AgendaEntry) = mergeCandidates.firstOrNull {
        it.id != source.id && taskBounds[it.id]?.contains(pointer) == true
    }
    fun finishDrag(pointer: Offset, source: AgendaEntry) {
        if (dropTargetBounds.contains(pointer)) {
            if (source.isGeneralGoal) moveGeneralGoalToDay(source) else promoteToGeneralGoal(source)
        } else if (!source.notDone) mergeTarget(pointer, source)?.let { merging = source to it }
        draggedEntry = null
        dropTargetBounds = Rect.Zero
    }
    if (notebookMode) AgendaNotebook(
        day = state.day, days = notebookDays, select = select, close = toggleNotebook,
        add = { date, task -> editor = AgendaEntry(day = date.toString(), text = "", isTask = task) },
        edit = { editor = it },
        complete = { entry -> if (entry.parts.isNotEmpty()) projectEditor = entry else complete(entry) },
    ) else Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            Text("GÜNLÜK TAKİP", color = WarmAccent, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ajanda", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = toggleNotebook) {
                    Icon(Icons.Outlined.MenuBook, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Defter")
                }
            }
            Text(dateLabel, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { select(state.day.minusDays(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Önceki gün") }
                OutlinedButton(onClick = { showCalendar = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(state.day.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                }
                IconButton(onClick = { select(state.day.plusDays(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Sonraki gün") }
                TextButton(onClick = { select(LocalDate.now()) }) { Text("Bugün") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editor = AgendaEntry(day = state.day.toString(), text = "", isTask = true) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null)
                    Text("Görev")
                }
                IconButton(onClick = { showBatch = true }) { Icon(Icons.Outlined.PlaylistAdd, "Toplu görev ekle") }
                OutlinedButton(onClick = { editor = AgendaEntry(day = state.day.toString(), text = "", isTask = false) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.EditNote, null)
                    Text("Not")
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { editor = AgendaEntry(day = state.day.toString(), text = "", isTask = true, isGeneralGoal = true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Flag, null)
                Spacer(Modifier.width(8.dp))
                Text("Genel hedef ekle")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = openSmartAdd, modifier = Modifier.weight(1.3f)) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Metni ayır")
                }
                OutlinedButton(onClick = importNotes, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.FileOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text("İçe aktar")
                }
            }
        }
        item {
            Text("Yaptıklarım", style = MaterialTheme.typography.titleLarge)
            Text("${completedTasks.size} tamamlanan görev", style = MaterialTheme.typography.labelMedium, color = WarmAccent)
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (completedTasks.isEmpty()) item { Text("Bu gün tamamlanan görev yok", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(completedTasks, key = { it.id }) { entry ->
            AgendaRow(entry, { complete(entry) }, { markNotDone(entry) }, { editor = entry }, { deleting = entry }, parts = { projectEditor = entry }, timer = { setTimer(entry) }, finishTimer = { finishTask(entry) })
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Yapılmadı", style = MaterialTheme.typography.titleLarge)
            Text("${notDoneTasks.size} yapılmayan görev", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        if (!state.loading && notDoneTasks.isEmpty()) item { Text("İşaretlenmiş yapılmadı kaydı yok", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(notDoneTasks, key = { it.id }) { entry ->
            AgendaGoalDragSource(
                entry = entry,
                active = draggedEntry?.id == entry.id,
                onDragStart = { pointer ->
                    draggedEntry = entry
                    dragPointer = pointer
                    dropTargetBounds = Rect.Zero
                },
                onDrag = { amount -> dragPointer += amount },
                onDragEnd = { pointer ->
                    finishDrag(pointer, entry)
                },
                onDragCancel = { draggedEntry = null; dropTargetBounds = Rect.Zero },
            ) {
                AgendaRow(entry, { complete(entry) }, { markNotDone(entry) }, { editor = entry }, { deleting = entry }, postpone = if (entry.repeatDaily) null else { { postponeToTomorrow(entry) } }, parts = { projectEditor = entry })
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Yapacaklarım", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showTemplates = true }) { Text("Hazır görevler") }
            }
            Text("${plannedTasks.size} bekleyen görev", style = MaterialTheme.typography.labelMedium, color = WarmAccent)
        }
        if (!state.loading && plannedTasks.isEmpty()) item { Text("Bu gün için bekleyen görev yok", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(plannedTasks, key = { it.id }) { entry ->
            AgendaGoalDragSource(
                entry = entry,
                active = draggedEntry?.id == entry.id,
                onDragStart = { pointer ->
                    draggedEntry = entry
                    dragPointer = pointer
                    dropTargetBounds = Rect.Zero
                },
                onDrag = { amount -> dragPointer += amount },
                onDragEnd = { pointer ->
                    finishDrag(pointer, entry)
                },
                onDragCancel = { draggedEntry = null; dropTargetBounds = Rect.Zero },
                onBounds = { if (it == Rect.Zero) taskBounds.remove(entry.id) else taskBounds[entry.id] = it },
                mergeTarget = draggedEntry?.let { mergeTarget(dragPointer, it)?.id == entry.id } == true,
            ) {
                AgendaRow(entry, { complete(entry) }, { markNotDone(entry) }, { editor = entry }, { deleting = entry }, postpone = if (entry.repeatDaily) null else { { postponeToTomorrow(entry) } }, parts = { projectEditor = entry }, timer = { setTimer(entry) }, finishTimer = { finishTask(entry) }, prioritySwipe = { adjustPriority(entry, it) })
            }
        }
        if (dailyTasks.isNotEmpty()) item { HorizontalDivider(); Text("Günlük tekrarlar", style = MaterialTheme.typography.titleLarge) }
        items(dailyTasks, key = { it.id }) { entry ->
            AgendaRow(entry, { complete(entry) }, { markNotDone(entry) }, { editor = entry }, { deleting = entry })
        }
        item { HorizontalDivider(); Spacer(Modifier.height(12.dp)); Text("Notlar", style = MaterialTheme.typography.titleLarge) }
        if (!state.loading && notes.isEmpty()) item { Text("Bu gün için not yok", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(notes, key = { it.id }) { entry ->
            AgendaRow(entry, {}, {}, { editor = entry }, { deleting = entry })
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Flag, null, tint = WarmAccent)
                Spacer(Modifier.width(8.dp))
                Text("Genel hedefler", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { editor = AgendaEntry(day = state.day.toString(), text = "", isTask = true, isGeneralGoal = true) }) {
                    Icon(Icons.Filled.Add, "Genel hedef ekle")
                }
            }
            Text("Tamamlanana kadar her gün görünür", style = MaterialTheme.typography.labelMedium, color = WarmAccent)
        }
        if (!state.loading && generalGoals.isEmpty()) item { Text("Bekleyen genel hedef yok", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(generalGoals, key = { it.id }) { entry ->
            AgendaGoalDragSource(
                entry = entry,
                active = draggedEntry?.id == entry.id,
                onDragStart = { pointer ->
                    draggedEntry = entry
                    dragPointer = pointer
                    dropTargetBounds = Rect.Zero
                },
                onDrag = { amount -> dragPointer += amount },
                onDragEnd = { pointer ->
                    finishDrag(pointer, entry)
                },
                onDragCancel = { draggedEntry = null; dropTargetBounds = Rect.Zero },
                onBounds = { if (it == Rect.Zero) taskBounds.remove(entry.id) else taskBounds[entry.id] = it },
                mergeTarget = draggedEntry?.let { mergeTarget(dragPointer, it)?.id == entry.id } == true,
            ) {
                AgendaRow(entry, { complete(entry) }, { markNotDone(entry) }, { editor = entry }, { deleting = entry }, allowNotDone = false, parts = { projectEditor = entry }, timer = { setTimer(entry) }, finishTimer = { finishTask(entry) })
            }
        }
        if (finishedProjects.isNotEmpty()) item { Text("Tamamlanan projeler", style = MaterialTheme.typography.titleMedium) }
        items(finishedProjects, key = { it.id }) { entry ->
            AgendaRow(entry, {}, {}, { editor = entry }, { deleting = entry }, allowNotDone = false, parts = { projectEditor = entry }, timer = { setTimer(entry) }, finishTimer = { finishTask(entry) })
        }
        }
        draggedEntry?.let { entry ->
            val moveToGeneral = !entry.isGeneralGoal
            AgendaTransferDropTarget(
                moveToGeneral = moveToGeneral,
                active = dropTargetBounds.contains(dragPointer),
                modifier = Modifier
                    .align(if (moveToGeneral) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(16.dp)
                    .zIndex(10f)
                    .onGloballyPositioned { dropTargetBounds = it.boundsInRoot() },
            )
        }
    }
    if (showCalendar) {
        val calendar = rememberDatePickerState(initialSelectedDateMillis = state.day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = { TextButton(onClick = {
                calendar.selectedDateMillis?.let { select(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                showCalendar = false
            }, enabled = calendar.selectedDateMillis != null) { Text("Seç") } },
            dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Vazgeç") } },
        ) { DatePicker(calendar) }
    }
    if (showBatch) AgendaBatchDialog(
        suggestions = state.suggestions + state.catalog.templates.map { AgendaTaskSuggestion(it.text, it.category, it.subcategory) },
        dismiss = { showBatch = false }, save = addTasks,
    )
    merging?.let { (source, target) ->
        AlertDialog(onDismissRequest = { merging = null }, title = { Text("Görevler birleştirilsin mi?") },
            text = { Text("${source.text.take(100)}\n+\n${target.text.take(100)}\n\nMetinler, alt görevler ve süreler birleştirilecek. Çalışan sayaçlar duraklatılacak. Hedef görevin günü ve kategorisi kullanılacak; kaynak kategorisi metinde korunacak.") },
            confirmButton = { TextButton(onClick = { mergeTasks(source.id, target.id) { merging = null } }) { Text("Birleştir") } },
            dismissButton = { TextButton(onClick = { merging = null }) { Text("Vazgeç") } })
    }
    if (showTemplates) TaskCatalogDialog(
        catalog = state.catalog,
        savedSuggestions = state.suggestions,
        initialText = "",
        dismiss = { showTemplates = false },
        select = { suggestion -> add(state.day, suggestion.text, true, false, false, 20, 0, suggestion.category, suggestion.subcategory) { showTemplates = false } },
        addTemplate = saveTaskTemplate,
        setFavorite = setTaskTemplateFavorite,
        removeTemplate = removeTaskTemplate,
        addCategory = saveCategory,
        removeCategory = removeCategory,
        hideSuggestion = hideTaskSuggestion,
    )
    if (showSmartAdd) {
        SmartAgendaDialog(
            baseDate = state.day,
            initialInput = importedText,
            dismiss = closeSmartAdd,
            save = { entries -> smartAdd(entries, closeSmartAdd) },
        )
    }
    projectEditor?.let { entry ->
        AgendaPartsDialog(entry, dismiss = { projectEditor = null }, save = { parts ->
            saveParts(entry.id, parts) { projectEditor = null }
        })
    }
    editor?.let { entry ->
        AgendaEditor(entry, state.suggestions, state.catalog, { editor = null }, saveTaskTemplate, setTaskTemplateFavorite, removeTaskTemplate, saveCategory, removeCategory, hideTaskSuggestion) { text, repeatDaily, notifyEnabled, notifyHour, notifyMinute, category, subcategory, done ->
            if (entry.id == 0L && entry.isGeneralGoal) addGeneralGoal(LocalDate.parse(entry.day), text, done)
            else if (entry.id == 0L) add(LocalDate.parse(entry.day), text, entry.isTask, repeatDaily, notifyEnabled, notifyHour, notifyMinute, category, subcategory, done)
            else edit(entry.id, text, category, subcategory, done)
        }
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Kayıt silinsin mi?") },
            text = { Text(entry.text.take(160)) },
            confirmButton = { TextButton(onClick = { delete(entry.id); deleting = null }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun AgendaRow(entry: AgendaEntry, complete: () -> Unit, markNotDone: () -> Unit, edit: () -> Unit, delete: () -> Unit, allowNotDone: Boolean = true, postpone: (() -> Unit)? = null, parts: (() -> Unit)? = null, timer: (() -> Unit)? = null, finishTimer: (() -> Unit)? = null, prioritySwipe: ((Int) -> Unit)? = null) {
    var swipeOffset by remember(entry.id) { mutableStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val currentPrioritySwipe by rememberUpdatedState(prioritySwipe)
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { translationX = swipeOffset }
            .pointerInput(entry.id) {
                if (currentPrioritySwipe == null) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffset >= swipeThreshold) currentPrioritySwipe?.invoke(1)
                        else if (swipeOffset <= -swipeThreshold) currentPrioritySwipe?.invoke(-1)
                        swipeOffset = 0f
                    },
                    onDragCancel = { swipeOffset = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        swipeOffset = (swipeOffset + amount).coerceIn(-240f, 240f)
                    },
                )
            },
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (entry.repeatDaily) {
            Surface(modifier = Modifier.size(52.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WarmAccent)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(entry.dailyCount.toString(), color = WarmAccent, modifier = Modifier.semantics { contentDescription = "Seçili gün ${entry.dailyCount} kez" })
                }
            }
            Spacer(Modifier.width(12.dp))
        } else if (entry.parts.isNotEmpty()) {
            IconButton(onClick = { parts?.invoke() }) { Icon(Icons.Outlined.AccountTree, "Alt görevleri düzenle", tint = WarmAccent) }
        } else if (entry.isTask && entry.completed) {
            IconButton(onClick = complete) { Icon(Icons.Outlined.Replay, "Görevi yeniden aç", tint = WarmAccent) }
        } else if (entry.isTask) Checkbox(false, { complete() })
        else Icon(Icons.AutoMirrored.Outlined.Notes, null, Modifier.padding(end = 12.dp), tint = WarmAccent)
        Column(Modifier.weight(1f)) {
            Text(entry.text, style = if (entry.completed) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
            if (entry.completed && !entry.repeatDaily) Text("Tamamlandı", style = MaterialTheme.typography.labelMedium, color = WarmAccent)
            if (timer != null && !entry.repeatDaily) AgendaTimerControls(entry, timer, finishTimer ?: complete)
            if (entry.repeatDaily) Text("Toplam yapılan gün: ${entry.totalCompletedDays}", style = MaterialTheme.typography.labelMedium, color = WarmAccent)
            if (entry.category.isNotBlank()) Text(listOf(entry.category, entry.subcategory).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = WarmAccent)
            if (entry.isTask && entry.streak > 0) Text("${entry.streak} günlük seri", style = MaterialTheme.typography.labelSmall, color = WarmAccent)
            if (entry.isTask && !entry.repeatDaily && entry.priority != 0) Text(
                if (entry.priority > 0) "Yüksek öncelik" else "Düşük öncelik",
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.priority > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.parts.isNotEmpty()) {
                Text("%${entry.parts.progressPercent} · ${entry.parts.count { it.progress == 100 }}/${entry.parts.size} alt görev", style = MaterialTheme.typography.labelMedium, color = WarmAccent)
                LinearProgressIndicator(progress = { entry.parts.progressPercent / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                entry.parts.take(3).forEach { part -> Text("${part.title} · %${part.progress}", style = MaterialTheme.typography.bodySmall) }
            }
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (entry.repeatDaily) {
            IconButton(onClick = markNotDone, enabled = entry.dailyCount > 0) { Icon(Icons.Outlined.Remove, "Son yapmayı geri al") }
            FilledTonalButton(onClick = complete) {
                Icon(Icons.Outlined.Check, null)
                Spacer(Modifier.width(6.dp))
                Text("Yaptım")
            }
        }
        if (entry.isTask && !entry.repeatDaily && allowNotDone && entry.parts.isEmpty()) IconButton(onClick = markNotDone) {
            Icon(
                if (entry.notDone) Icons.Outlined.Replay else Icons.Outlined.Close,
                if (entry.notDone) "Yapılmadı durumunu kaldır" else "Yapılmadı olarak işaretle",
                tint = if (entry.notDone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (postpone != null) IconButton(onClick = postpone) { Icon(Icons.Outlined.Schedule, "Yarına ertele") }
        if (parts != null && entry.isTask && !entry.repeatDaily) TextButton(onClick = parts) {
            Icon(Icons.Outlined.AccountTree, null)
            Spacer(Modifier.width(4.dp))
            Text("Alt görevler")
        }
        IconButton(onClick = edit) { Icon(Icons.Outlined.Edit, "Kaydı düzenle") }
        IconButton(onClick = delete) { Icon(Icons.Outlined.DeleteOutline, "Kaydı sil") }
      }
    }
}

@Composable
private fun AgendaGoalDragSource(
    entry: AgendaEntry,
    active: Boolean,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
    onDragCancel: () -> Unit,
    onBounds: (Rect) -> Unit = {},
    mergeTarget: Boolean = false,
    content: @Composable () -> Unit,
) {
    var dragTranslation by remember(entry.id) { mutableStateOf(Offset.Zero) }
    var dragHandleOrigin by remember(entry.id) { mutableStateOf(Offset.Zero) }
    var pointerPosition by remember(entry.id) { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val currentOnBounds by rememberUpdatedState(onBounds)
    DisposableEffect(entry.id) { onDispose { currentOnBounds(Rect.Zero) } }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { if (!active) onBounds(it.boundsInRoot()) }
            .zIndex(if (active) 5f else 0f)
            .graphicsLayer {
                translationX = dragTranslation.x
                translationY = dragTranslation.y
                alpha = if (active) 0.92f else 1f
                shadowElevation = if (active) 18.dp.toPx() else 0f
            },
        color = if (mergeTarget) MaterialTheme.colorScheme.primaryContainer else if (active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .onGloballyPositioned { dragHandleOrigin = it.positionInRoot() }
                    .pointerInput(entry.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localPosition ->
                                dragTranslation = Offset.Zero
                                pointerPosition = dragHandleOrigin + localPosition
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnDragStart(pointerPosition)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragTranslation += amount
                                pointerPosition += amount
                                currentOnDrag(amount)
                            },
                            onDragEnd = {
                                currentOnDragEnd(pointerPosition)
                                dragTranslation = Offset.Zero
                            },
                            onDragCancel = {
                                currentOnDragCancel()
                                dragTranslation = Offset.Zero
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.DragIndicator, "Taşımak için basılı tutup sürükle", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
private fun AgendaTransferDropTarget(
    moveToGeneral: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = if (moveToGeneral) "Genel hedeflere bırak" else "Günlük görevlere bırak"
    Surface(
        modifier = modifier.fillMaxWidth().height(88.dp),
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(2.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (moveToGeneral) Icons.Outlined.Flag else Icons.Outlined.Today, label, tint = if (active) MaterialTheme.colorScheme.primary else WarmAccent)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AgendaEditor(
    entry: AgendaEntry,
    savedSuggestions: List<AgendaTaskSuggestion>,
    catalog: com.uykutakip.data.AgendaTaskCatalog,
    dismiss: () -> Unit,
    saveTaskTemplate: (String, String, String, Boolean) -> Unit,
    setTaskTemplateFavorite: (String, Boolean) -> Unit,
    removeTaskTemplate: (String) -> Unit,
    saveCategory: (String, String) -> Unit,
    removeCategory: (Long) -> Unit,
    hideTaskSuggestion: (String, String, String) -> Unit,
    save: (String, Boolean, Boolean, Int, Int, String, String, () -> Unit) -> Unit,
) {
    var text by rememberSaveable(entry.id, entry.day, entry.isTask) { mutableStateOf(entry.text) }
    var repeatDaily by rememberSaveable(entry.id) { mutableStateOf(entry.repeatDaily) }
    var notifyEnabled by rememberSaveable(entry.id) { mutableStateOf(entry.notifyEnabled) }
    var notifyHour by rememberSaveable(entry.id) { mutableIntStateOf(entry.notifyHour) }
    var notifyMinute by rememberSaveable(entry.id) { mutableIntStateOf(entry.notifyMinute) }
    var category by rememberSaveable(entry.id) { mutableStateOf(entry.category) }
    var subcategory by rememberSaveable(entry.id) { mutableStateOf(entry.subcategory) }
    var categoryEdited by rememberSaveable(entry.id) { mutableStateOf(entry.id != 0L) }
    var subcategoryEdited by rememberSaveable(entry.id) { mutableStateOf(entry.id != 0L) }
    var showTime by remember { mutableStateOf(false) }
    var showTaskPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showSubcategoryPicker by remember { mutableStateOf(false) }
    val taskSuggestions = remember(savedSuggestions, catalog.templates) {
        (savedSuggestions + catalog.templates.map { AgendaTaskSuggestion(it.text, it.category, it.subcategory) })
            .distinctBy { Triple(it.text.lowercase(), it.category.lowercase(), it.subcategory.lowercase()) }
    }
    val autoCompleteSuggestions = remember(text, taskSuggestions) {
        val query = text.trim().lowercase()
        if (query.isBlank()) taskSuggestions.take(5)
        else taskSuggestions.filter { it.text.lowercase().contains(query) }.take(5)
    }
    val categories = remember(catalog.categories) { catalog.categories.map { it.category }.filter { it.isNotBlank() }.distinct() }
    val subcategories = remember(category, catalog.categories) { catalog.categories.filter { it.category == category }.map { it.subcategory }.filter { it.isNotBlank() }.distinct() }
    if (showTaskPicker) {
        TaskCatalogDialog(
            catalog = catalog,
            savedSuggestions = savedSuggestions,
            initialText = text,
            dismiss = { showTaskPicker = false },
            select = { suggestion ->
                text = suggestion.text
                category = suggestion.category
                subcategory = suggestion.subcategory
                showTaskPicker = false
            },
            addTemplate = saveTaskTemplate,
            setFavorite = setTaskTemplateFavorite,
            removeTemplate = removeTaskTemplate,
            addCategory = saveCategory,
            removeCategory = removeCategory,
            hideSuggestion = hideTaskSuggestion,
        )
    } else AlertDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(if (entry.isGeneralGoal) "Genel hedef" else if (entry.isTask) "Görev" else "Günlük not") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = text, onValueChange = { value ->
                        if (value.length <= 4000) {
                            text = value
                            val suggestion = matchingTask(value, taskSuggestions)
                            if (entry.isTask && suggestion != null) {
                                if (!categoryEdited) category = suggestion.category
                                if (!subcategoryEdited && category == suggestion.category) subcategory = suggestion.subcategory
                            } else if (entry.isTask) {
                                if (!categoryEdited) category = ""
                                if (!subcategoryEdited) subcategory = ""
                            }
                        }
                    },
                    label = { Text(if (entry.isTask) "Yapılacak iş" else "Notun") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (entry.isTask) 96.dp else 220.dp, max = 420.dp),
                    minLines = if (entry.isTask) 2 else 8,
                    maxLines = if (entry.isTask) 4 else 18,
                    singleLine = false,
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                if (entry.isTask) {
                    if (autoCompleteSuggestions.isNotEmpty()) {
                        Text("Önceki görevlerden öneriler", style = MaterialTheme.typography.labelMedium, color = WarmAccent)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(autoCompleteSuggestions, key = { "${it.text}|${it.category}|${it.subcategory}" }) { suggestion ->
                                AssistChip(
                                    onClick = { text = suggestion.text; category = suggestion.category; subcategory = suggestion.subcategory },
                                    label = { Text(suggestion.text, maxLines = 1) },
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { showTaskPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.PlaylistAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Hazır görevler, favoriler ve kategoriler")
                    }
                    Box {
                        OutlinedTextField(value = category, onValueChange = { if (it.length <= 60) { category = it; categoryEdited = true } }, label = { Text("Kategori") }, trailingIcon = { IconButton(onClick = { showCategoryPicker = true }) { Icon(Icons.Outlined.ArrowDropDown, "Kategorileri seç") } }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        DropdownMenu(expanded = showCategoryPicker, onDismissRequest = { showCategoryPicker = false }) { categories.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { category = option; categoryEdited = true; if (catalog.categories.none { it.category == option && it.subcategory == subcategory }) subcategory = ""; showCategoryPicker = false }) } }
                    }
                    Box {
                        OutlinedTextField(value = subcategory, onValueChange = { if (it.length <= 60) { subcategory = it; subcategoryEdited = true } }, label = { Text("Alt kategori") }, trailingIcon = { IconButton(onClick = { showSubcategoryPicker = true }) { Icon(Icons.Outlined.ArrowDropDown, "Alt kategorileri seç") } }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        DropdownMenu(expanded = showSubcategoryPicker, onDismissRequest = { showSubcategoryPicker = false }) { subcategories.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { subcategory = option; subcategoryEdited = true; showSubcategoryPicker = false }) } }
                    }
                }
                if (entry.isTask && entry.id == 0L && !entry.isGeneralGoal) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(repeatDaily, { repeatDaily = it })
                        Text("Her gün tekrarla")
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(notifyEnabled, { notifyEnabled = it }); Text("Bildirim gönder")
                        TextButton(onClick = { showTime = true }, enabled = notifyEnabled) { Text("%02d:%02d".format(notifyHour, notifyMinute)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { save(text, repeatDaily, notifyEnabled, notifyHour, notifyMinute, category, subcategory, dismiss) }, enabled = text.isNotBlank()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Vazgeç") } },
    )
    if (showTime) {
        val time = rememberTimePickerState(notifyHour, notifyMinute, is24Hour = true)
        AlertDialog(onDismissRequest = { showTime = false }, title = { Text("Görev bildirim saati") }, text = { TimeInput(time) }, confirmButton = { TextButton(onClick = { notifyHour = time.hour; notifyMinute = time.minute; showTime = false }) { Text("Uygula") } }, dismissButton = { TextButton(onClick = { showTime = false }) { Text("Vazgeç") } })
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SmartAgendaDialog(
    baseDate: LocalDate,
    initialInput: String = "",
    dismiss: () -> Unit,
    save: (List<AgendaDraft>) -> Unit,
) {
    var inputMode by remember { mutableStateOf(AgendaIntent.AUTO) }
    var mixedInput by rememberSaveable(baseDate, initialInput) { mutableStateOf(initialInput) }
    var completedInput by rememberSaveable(baseDate) { mutableStateOf("") }
    var plannedInput by rememberSaveable(baseDate) { mutableStateOf("") }
    var drafts by remember(baseDate) { mutableStateOf<List<AgendaDraft>?>(null) }
    var previewMode by remember { mutableStateOf(AgendaIntent.AUTO) }
    var parseAttempted by remember { mutableStateOf(false) }
    val selectedCount = drafts?.count { it.selected && it.text.isNotBlank() } ?: 0
    val currentInput = when (inputMode) {
        AgendaIntent.AUTO -> mixedInput
        AgendaIntent.COMPLETED -> completedInput
        AgendaIntent.PLANNED -> plannedInput
    }
    val hasInput = mixedInput.isNotBlank() || completedInput.isNotBlank() || plannedInput.isNotBlank()

    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 720.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = WarmAccent)
                    Spacer(Modifier.width(10.dp))
                    Text("Akıllı ajanda", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Kapat") }
                }

                if (drafts == null) {
                    Text(
                        baseDate.format(DateTimeFormatter.ofPattern("d MMMM EEEE", Locale.forLanguageTag("tr-TR"))),
                        style = MaterialTheme.typography.labelMedium,
                        color = WarmAccent,
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(
                            AgendaIntent.AUTO to "Karışık",
                            AgendaIntent.COMPLETED to "Yaptıklarım",
                            AgendaIntent.PLANNED to "Yapacaklarım",
                        ).forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = inputMode == mode,
                                onClick = { inputMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = currentInput,
                        onValueChange = { value ->
                            if (value.length <= 8000) when (inputMode) {
                                AgendaIntent.AUTO -> mixedInput = value
                                AgendaIntent.COMPLETED -> completedInput = value
                                AgendaIntent.PLANNED -> plannedInput = value
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 180.dp, max = 420.dp),
                        label = { Text(when (inputMode) {
                            AgendaIntent.AUTO -> "Karışık günlük metni"
                            AgendaIntent.COMPLETED -> "Yaptıklarım"
                            AgendaIntent.PLANNED -> "Yapacaklarım"
                        }) },
                        placeholder = { Text(when (inputMode) {
                            AgendaIntent.AUTO -> "Yürüyüş yaptım. Yarın 14:30'da dişçiye gideceğim."
                            AgendaIntent.COMPLETED -> "yurus yaptm, faturayi odedm"
                            AgendaIntent.PLANNED -> "yarin 14.30 doktor, aksam market"
                        }) },
                        minLines = 7,
                    )
                    if (parseAttempted) {
                        Spacer(Modifier.height(8.dp))
                        Text("Ajandaya eklenecek bir kayıt bulunamadı.", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val result = buildList {
                                addAll(SmartAgendaParser.parse(mixedInput, baseDate, AgendaIntent.AUTO))
                                addAll(SmartAgendaParser.parse(completedInput, baseDate, AgendaIntent.COMPLETED))
                                addAll(SmartAgendaParser.parse(plannedInput, baseDate, AgendaIntent.PLANNED))
                            }.distinctBy { "${it.day}|${it.completed}|${it.text.lowercase()}" }
                            parseAttempted = true
                            if (result.isNotEmpty()) drafts = result
                        },
                        enabled = hasInput,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Taslağı hazırla")
                    }
                } else {
                    Text(
                        "$selectedCount kayıt seçildi · ${drafts.orEmpty().count { it.completed }} yapılan · ${drafts.orEmpty().count { it.isTask && !it.completed }} yapılacak",
                        style = MaterialTheme.typography.labelMedium,
                        color = WarmAccent,
                    )
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(
                            AgendaIntent.AUTO to "Hepsi",
                            AgendaIntent.COMPLETED to "Yaptıklarım",
                            AgendaIntent.PLANNED to "Yapacaklarım",
                        ).forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = previewMode == mode,
                                onClick = { previewMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        val visibleDrafts = drafts.orEmpty().mapIndexedNotNull { index, draft ->
                            val visible = when (previewMode) {
                                AgendaIntent.AUTO -> true
                                AgendaIntent.COMPLETED -> draft.completed
                                AgendaIntent.PLANNED -> !draft.completed
                            }
                            if (visible) index to draft else null
                        }
                        items(visibleDrafts, key = { (index, item) -> "$index|${item.day}|${item.text}" }) { (index, draft) ->
                            SmartDraftEditor(
                                draft = draft,
                                update = { updated -> drafts = drafts.orEmpty().toMutableList().also { it[index] = updated } },
                            )
                            HorizontalDivider()
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { drafts = null }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null)
                            Text("Metne dön")
                        }
                        Button(onClick = { save(drafts.orEmpty()) }, enabled = selectedCount > 0, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.DoneAll, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Ajandaya ekle")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SmartDraftEditor(draft: AgendaDraft, update: (AgendaDraft) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.selected, onCheckedChange = { update(draft.copy(selected = it)) })
            Text(
                draft.day.format(DateTimeFormatter.ofPattern("d MMM, EEE", Locale.forLanguageTag("tr-TR"))),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { update(draft.copy(day = draft.day.minusDays(1))) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Bir gün geri")
            }
            IconButton(onClick = { update(draft.copy(day = draft.day.plusDays(1))) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Bir gün ileri")
            }
        }
        OutlinedTextField(
            value = draft.text,
            onValueChange = { if (it.length <= 4000) update(draft.copy(text = it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = draft.selected,
            minLines = 2,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = draft.isTask,
                onClick = { update(draft.copy(isTask = true)) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                enabled = draft.selected,
            ) { Text("Görev") }
            SegmentedButton(
                selected = !draft.isTask,
                onClick = { update(draft.copy(isTask = false, completed = false, notifyEnabled = false)) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                enabled = draft.selected,
            ) { Text("Not") }
        }
        if (draft.isTask) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = draft.completed,
                    onCheckedChange = { update(draft.copy(completed = it, notifyEnabled = if (it) false else draft.notifyEnabled)) },
                    enabled = draft.selected,
                )
                Text("Tamamlandı", modifier = Modifier.weight(1f))
                Checkbox(
                    checked = draft.notifyEnabled,
                    onCheckedChange = { update(draft.copy(notifyEnabled = it)) },
                    enabled = draft.selected && !draft.completed,
                )
                Text(if (draft.notifyEnabled) "%02d:%02d".format(draft.notifyHour, draft.notifyMinute) else "Bildirim")
            }
        }
    }
}
