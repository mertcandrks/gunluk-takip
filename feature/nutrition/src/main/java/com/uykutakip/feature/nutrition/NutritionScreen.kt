package com.uykutakip.feature.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import com.uykutakip.core.model.FoodCategory
import com.uykutakip.core.model.FoodEntry
import com.uykutakip.core.model.FoodUnit
import com.uykutakip.core.model.MealRecord
import com.uykutakip.core.model.MealType
import com.uykutakip.core.time.TimeFormatter
import com.uykutakip.core.ui.components.EmptyState
import com.uykutakip.core.ui.components.DateTimeButton
import com.uykutakip.core.ui.components.MetricCard
import com.uykutakip.core.ui.components.SectionTitle
import com.uykutakip.core.ui.components.TrackerCard
import com.uykutakip.core.ui.components.rememberDateTimePicker
import com.uykutakip.core.ui.theme.AppSurfaceHigh
import com.uykutakip.core.ui.theme.CoralAccent
import com.uykutakip.core.ui.theme.MealAccent
import com.uykutakip.core.ui.theme.SleepAccent
import com.uykutakip.core.ui.theme.TextSecondary
import com.uykutakip.core.ui.theme.WarmAccent
import java.math.BigDecimal

private enum class AddMode { ACTIVE_MEAL, QUICK_RECORD, FORGOTTEN_MEAL }

private data class ForgottenMealDraft(
    val type: MealType = MealType.BREAKFAST,
    val startMillis: Long,
    val endMillis: Long,
    val items: List<FoodEntry> = emptyList(),
)

@Composable
fun MealScreen(
    now: Long,
    activeMeal: MealRecord?,
    records: List<MealRecord>,
    formatter: TimeFormatter,
    onStart: (MealType) -> Unit,
    onAddItem: (FoodEntry) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onFinish: () -> Unit,
    onQuickAdd: (MealType, FoodEntry) -> Unit,
    onAddPastMeal: (MealType, Long, Long, List<FoodEntry>) -> Unit,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit,
) {
    val analytics = remember(formatter) { MealAnalytics(formatter) }
    val day = formatter.localDate(now)
    val summary = remember(records, day, analytics) { analytics.todaySummary(records) }
    var showStartDialog by remember { mutableStateOf(false) }
    var pickerMode by remember { mutableStateOf<AddMode?>(null) }
    var portionItem by remember { mutableStateOf<FoodCatalogItem?>(null) }
    var pendingDelete by remember { mutableStateOf<MealRecord?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var forgottenMeal by remember { mutableStateOf<ForgottenMealDraft?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("GÜNLÜK TAKİP", style = MaterialTheme.typography.labelMedium, color = MealAccent)
                Spacer(Modifier.height(5.dp))
                Text("Beslenme", style = MaterialTheme.typography.headlineLarge)
                Text(formatter.fullDate(now), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Ana öğün", summary.mainMealCount.toString(), MealAccent, Modifier.weight(1f))
                    MetricCard("Atıştırma", summary.snackCount.toString(), WarmAccent, Modifier.weight(1f))
                    MetricCard("Son ara", summary.latestGapMillis?.let(formatter::duration) ?: "-", Color(0xFF63C7FF), Modifier.weight(1f))
                }
            }
            item {
                ActiveMealCard(
                    now = now,
                    activeMeal = activeMeal,
                    formatter = formatter,
                    onStart = { showStartDialog = true },
                    onAddFood = { pickerMode = AddMode.ACTIVE_MEAL },
                    onSuggestion = { portionItem = it; pickerMode = AddMode.ACTIVE_MEAL },
                    onRemoveItem = onRemoveItem,
                    onFinish = onFinish,
                )
            }
            item {
                FilledTonalButton(
                    onClick = {
                        forgottenMeal = ForgottenMealDraft(
                            startMillis = now - 30 * 60_000L,
                            endMillis = now,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = WarmAccent.copy(alpha = 0.14f),
                        contentColor = WarmAccent,
                    ),
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Unutulan öğün ekle")
                }
            }
            item {
                FilledTonalButton(
                    onClick = { pickerMode = AddMode.QUICK_RECORD },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SleepAccent.copy(alpha = 0.14f),
                        contentColor = SleepAccent,
                    ),
                ) {
                    Icon(Icons.Outlined.LocalCafe, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Atıştırma veya içecek kaydet")
                }
            }
            item {
                SectionTitle("Beslenme geçmişi") {
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Beslenme kayıtlarını temizle", tint = TextSecondary)
                        }
                    }
                }
            }
            if (records.isEmpty()) {
                item { EmptyState("Henüz beslenme kaydı yok", Icons.Outlined.RestaurantMenu) }
            } else {
                itemsIndexed(records, key = { _, record -> record.id }) { index, record ->
                    MealRecordCard(
                        record = record,
                        sequence = analytics.dailySequenceNumber(record, records, index),
                        previous = if (record.type.isEating) analytics.previousEatingRecord(records, index) else null,
                        formatter = formatter,
                        onDelete = { pendingDelete = record },
                    )
                }
            }
        }
    }

    if (showStartDialog) {
        MealTypeDialog(
            title = "Hangi öğün başlıyor?",
            onDismiss = { showStartDialog = false },
            onSelected = { showStartDialog = false; onStart(it) },
        )
    }
    if (pickerMode != null && portionItem == null) {
        FoodPickerDialog(
            title = when (pickerMode) {
                AddMode.ACTIVE_MEAL -> "Öğüne yiyecek ekle"
                AddMode.QUICK_RECORD -> "Hızlı kayıt"
                AddMode.FORGOTTEN_MEAL -> "Unutulan öğüne yiyecek ekle"
                null -> "Yiyecek seç"
            },
            onDismiss = { pickerMode = null },
            onSelected = { portionItem = it },
            onCustom = { portionItem = FoodCatalogItem("", FoodCategory.OTHER, 1.0, FoodUnit.PORTION) },
        )
    }
    portionItem?.let { catalogItem ->
        FoodPortionDialog(
            item = catalogItem,
            onDismiss = { portionItem = null; pickerMode = null },
            onSubmit = { entry ->
                when (pickerMode) {
                    AddMode.ACTIVE_MEAL -> onAddItem(entry)
                    AddMode.QUICK_RECORD -> onQuickAdd(
                        if (entry.category == FoodCategory.DRINK) MealType.DRINK else MealType.SNACK,
                        entry,
                    )
                    AddMode.FORGOTTEN_MEAL -> forgottenMeal = forgottenMeal?.let { it.copy(items = it.items + entry) }
                    null -> Unit
                }
                portionItem = null
                pickerMode = null
            },
        )
    }
    forgottenMeal?.let { draft ->
        if (pickerMode == null && portionItem == null) {
            ForgottenMealDialog(
                draft = draft,
                formatter = formatter,
                onDraftChange = { forgottenMeal = it },
                onAddFood = { pickerMode = AddMode.FORGOTTEN_MEAL },
                onDismiss = { forgottenMeal = null },
                onSave = {
                    onAddPastMeal(it.type, it.startMillis, it.endMillis, it.items)
                    forgottenMeal = null
                },
            )
        }
    }
    pendingDelete?.let { record ->
        ConfirmDialog(
            title = "Kayıt silinsin mi?",
            message = "${record.type.label} kaydı cihazdan kaldırılacak.",
            onDismiss = { pendingDelete = null },
            onConfirm = { pendingDelete = null; onDelete(record.id) },
        )
    }
    if (showClearDialog) {
        ConfirmDialog(
            title = "Beslenme geçmişi silinsin mi?",
            message = "Tüm tamamlanmış öğün ve hızlı kayıtlar kaldırılacak.",
            onDismiss = { showClearDialog = false },
            onConfirm = { showClearDialog = false; onClear() },
        )
    }
}

@Composable
private fun ActiveMealCard(
    now: Long,
    activeMeal: MealRecord?,
    formatter: TimeFormatter,
    onStart: () -> Unit,
    onAddFood: () -> Unit,
    onSuggestion: (FoodCatalogItem) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    TrackerCard(containerColor = AppSurfaceHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.RestaurantMenu, contentDescription = null, tint = MealAccent)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(activeMeal?.let { "${it.type.label} devam ediyor" } ?: "Aktif öğün yok", style = MaterialTheme.typography.titleMedium)
                Text(
                    activeMeal?.let { "Başlangıç  ${formatter.time(it.startMillis)}" } ?: "Başlangıç ve bitiş saatini birlikte takip et",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            if (activeMeal != null) {
                Text(formatter.timer(now - activeMeal.startMillis), style = MaterialTheme.typography.titleLarge, color = MealAccent)
            }
        }

        if (activeMeal == null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MealAccent),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Öğün Başlat")
            }
            return@TrackerCard
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
        Spacer(Modifier.height(12.dp))
        if (activeMeal.items.isEmpty()) {
            Text("Bu öğüne henüz yiyecek eklenmedi", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        } else {
            activeMeal.items.forEachIndexed { index, item ->
                FoodEntryRow(item = item, onRemove = { onRemoveItem(index) })
            }
        }

        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FoodCatalog.suggestions(activeMeal.type), key = { it.name }) { item ->
                AssistChip(
                    onClick = { onSuggestion(item) },
                    label = { Text(item.name) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MealAccent.copy(alpha = 0.12f),
                        labelColor = Color.White,
                        leadingIconContentColor = MealAccent,
                    ),
                    border = null,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onAddFood, modifier = Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Yiyecek")
            }
            Button(
                onClick = onFinish,
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarmAccent),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Bitir")
            }
        }
    }
}

@Composable
private fun FoodEntryRow(item: FoodEntry, onRemove: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (item.category == FoodCategory.DRINK) Icons.Outlined.LocalCafe else Icons.Outlined.Fastfood,
            contentDescription = null,
            tint = if (item.category == FoodCategory.DRINK) WarmAccent else MealAccent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(item.portionLabel(), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Yiyeceği kaldır", tint = CoralAccent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MealRecordCard(
    record: MealRecord,
    sequence: Int,
    previous: MealRecord?,
    formatter: TimeFormatter,
    onDelete: () -> Unit,
) {
    TrackerCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${record.type.label} $sequence", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatter.shortDate(record.startMillis)} · ${formatter.time(record.startMillis)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Kaydı sil", tint = CoralAccent)
            }
        }
        if (!record.isQuickRecord) {
            Text("Süre  ${formatter.duration(record.durationMillis)}", style = MaterialTheme.typography.labelMedium, color = MealAccent)
        }
        Spacer(Modifier.height(7.dp))
        record.items.forEach { FoodEntryRow(item = it) }
        previous?.let {
            val gap = (record.startMillis - (it.endMillis ?: it.startMillis)).coerceAtLeast(0L)
            Spacer(Modifier.height(7.dp))
            Text("Önceki öğünden ${formatter.duration(gap)} sonra", style = MaterialTheme.typography.labelMedium, color = WarmAccent)
        }
    }
}

@Composable
private fun FoodPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onSelected: (FoodCatalogItem) -> Unit,
    onCustom: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<FoodCategory?>(null) }
    val filteredItems = remember(query, category) {
        FoodCatalog.items.filter { item ->
            (category == null || item.category == category) && item.name.contains(query.trim(), ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text("Yiyecek ara") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item {
                        FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Tümü") })
                    }
                    items(FoodCategory.entries.filter { it != FoodCategory.OTHER }) { item ->
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item.label) })
                    }
                }
                TextButton(onClick = onCustom, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Listede olmayan yiyecek")
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp)) {
                    items(filteredItems, key = { it.name }) { item ->
                        ListItem(
                            headlineContent = { Text(item.name, fontWeight = FontWeight.Medium) },
                            supportingContent = { Text("${item.category.label} · ${item.defaultQuantity.cleanNumber()} ${item.defaultUnit.label}") },
                            leadingContent = {
                                Icon(
                                    if (item.category == FoodCategory.DRINK) Icons.Outlined.LocalCafe else Icons.Outlined.Fastfood,
                                    contentDescription = null,
                                    tint = if (item.category == FoodCategory.DRINK) WarmAccent else MealAccent,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onSelected(item) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun FoodPortionDialog(item: FoodCatalogItem, onDismiss: () -> Unit, onSubmit: (FoodEntry) -> Unit) {
    var name by remember(item) { mutableStateOf(item.name) }
    var quantityText by remember(item) { mutableStateOf(item.defaultQuantity.cleanNumber()) }
    var unit by remember(item) { mutableStateOf(item.defaultUnit) }
    var category by remember(item) { mutableStateOf(item.category) }
    val quantity = quantityText.replace(',', '.').toDoubleOrNull()
    val isValid = name.isNotBlank() && quantity != null && quantity.isFinite() && quantity > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.name.isBlank()) "Yeni yiyecek" else item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.name.isBlank()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Yiyecek adı") },
                        singleLine = true,
                    )
                    Text("Kategori", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(FoodCategory.entries) { option ->
                            FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option.label) })
                        }
                    }
                }
                Text("Miktar", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        val current = quantity ?: item.defaultQuantity
                        quantityText = (current - unit.step()).coerceAtLeast(unit.step()).cleanNumber()
                    }) { Icon(Icons.Outlined.Remove, contentDescription = "Miktarı azalt") }
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { value -> if (value.length <= 7) quantityText = value.filter { it.isDigit() || it == ',' || it == '.' } },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        suffix = { Text(unit.label) },
                    )
                    IconButton(onClick = {
                        val current = quantity ?: item.defaultQuantity
                        quantityText = (current + unit.step()).cleanNumber()
                    }) { Icon(Icons.Filled.Add, contentDescription = "Miktarı artır") }
                }
                Text("Birim", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(FoodUnit.entries) { option ->
                        FilterChip(selected = unit == option, onClick = { unit = option }, label = { Text(option.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(FoodEntry(name.trim(), quantity ?: 1.0, unit, category)) },
                enabled = isValid,
            ) { Text("Ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun ForgottenMealDialog(
    draft: ForgottenMealDraft,
    formatter: TimeFormatter,
    onDraftChange: (ForgottenMealDraft) -> Unit,
    onAddFood: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (ForgottenMealDraft) -> Unit,
) {
    val showDateTimePicker = rememberDateTimePicker()
    val isValid = draft.startMillis > 0L &&
        draft.endMillis > draft.startMillis &&
        draft.endMillis <= System.currentTimeMillis() + 60_000L &&
        draft.items.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unutulan öğün") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Öğün türü", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(MealType.entries.filter { it.isEating }) { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = { onDraftChange(draft.copy(type = type)) },
                            label = { Text(type.label) },
                        )
                    }
                }
                DateTimeButton(
                    label = "Başlangıç zamanı",
                    millis = draft.startMillis,
                    formatter = formatter,
                    onClick = {
                        showDateTimePicker(draft.startMillis) {
                            onDraftChange(draft.copy(startMillis = it))
                        }
                    },
                )
                DateTimeButton(
                    label = "Bitiş zamanı",
                    millis = draft.endMillis,
                    formatter = formatter,
                    onClick = {
                        showDateTimePicker(draft.endMillis) {
                            onDraftChange(draft.copy(endMillis = it))
                        }
                    },
                )
                Text("Yediklerim", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                if (draft.items.isEmpty()) {
                    Text("Henüz yiyecek eklenmedi", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp)) {
                        itemsIndexed(draft.items) { index, item ->
                            FoodEntryRow(
                                item = item,
                                onRemove = {
                                    onDraftChange(draft.copy(items = draft.items.filterIndexed { itemIndex, _ -> itemIndex != index }))
                                },
                            )
                        }
                    }
                }
                FilledTonalButton(onClick = onAddFood, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Yiyecek ekle")
                }
                if (!isValid) {
                    Text(
                        if (draft.items.isEmpty()) "Kaydetmek için en az bir yiyecek ekle."
                        else "Bitiş zamanı başlangıçtan sonra ve gelecekte olmayacak şekilde seçilmeli.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoralAccent,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }, enabled = isValid) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun MealTypeDialog(title: String, onDismiss: () -> Unit, onSelected: (MealType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MealType.entries.filter { it.isEating }.forEach { type ->
                    TextButton(onClick = { onSelected(type) }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Sil", color = CoralAccent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

private fun FoodEntry.portionLabel() = "${quantity.cleanNumber()} ${unit.label}"

private fun Double.cleanNumber(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString().replace('.', ',')

private fun FoodUnit.step(): Double = when (this) {
    FoodUnit.GRAM, FoodUnit.MILLILITER -> 10.0
    FoodUnit.TEASPOON, FoodUnit.TABLESPOON -> 0.5
    FoodUnit.PIECE,
    FoodUnit.SLICE,
    FoodUnit.CUP,
    FoodUnit.GLASS,
    FoodUnit.BOWL,
    FoodUnit.PLATE,
    FoodUnit.PORTION,
    FoodUnit.SPOON,
    FoodUnit.PACKAGE,
    FoodUnit.CAN,
    -> 1.0
}
