package com.uykutakip.feature.agenda

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uykutakip.data.AgendaCategoryOption
import com.uykutakip.data.AgendaTaskCatalog
import com.uykutakip.data.AgendaTaskSuggestion
import com.uykutakip.data.AgendaTaskTemplate

private enum class CatalogTab { TASKS, CATEGORIES }

@Composable
internal fun TaskCatalogDialog(
    catalog: AgendaTaskCatalog,
    savedSuggestions: List<AgendaTaskSuggestion>,
    initialText: String,
    dismiss: () -> Unit,
    select: (AgendaTaskSuggestion) -> Unit,
    addTemplate: (String, String, String, Boolean) -> Unit,
    setFavorite: (String, Boolean) -> Unit,
    removeTemplate: (String) -> Unit,
    addCategory: (String, String) -> Unit,
    removeCategory: (Long) -> Unit,
    hideSuggestion: (String, String, String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(CatalogTab.TASKS) }
    var showTemplateForm by rememberSaveable { mutableStateOf(false) }
    var showCategoryForm by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf(initialText) }
    var templateText by rememberSaveable { mutableStateOf(initialText) }
    var templateCategory by rememberSaveable { mutableStateOf("") }
    var templateSubcategory by rememberSaveable { mutableStateOf("") }
    var templateFavorite by rememberSaveable { mutableStateOf(false) }
    var categoryText by rememberSaveable { mutableStateOf("") }
    var subcategoryText by rememberSaveable { mutableStateOf("") }
    val filteredTemplates = remember(catalog.templates, query) {
        val normalized = query.trim().lowercase()
        catalog.templates.filter {
            normalized.isBlank() || listOf(it.text, it.category, it.subcategory).any { value -> value.lowercase().contains(normalized) }
        }
    }
    val historicalMatches = remember(savedSuggestions, catalog.templates, query) {
        val templateKeys = catalog.templates.map { suggestionKey(it.text, it.category, it.subcategory) }.toSet()
        val normalized = query.trim().lowercase()
        savedSuggestions.filter {
            suggestionKey(it.text, it.category, it.subcategory) !in templateKeys &&
                (normalized.isBlank() || listOf(it.text, it.category, it.subcategory).any { value -> value.lowercase().contains(normalized) })
        }.take(12)
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Görev kitaplığı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == CatalogTab.TASKS, onClick = { tab = CatalogTab.TASKS }, label = { Text("Görevler") })
                    FilterChip(selected = tab == CatalogTab.CATEGORIES, onClick = { tab = CatalogTab.CATEGORIES }, label = { Text("Kategoriler") })
                }
                if (tab == CatalogTab.TASKS) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { if (it.length <= 4000) query = it },
                        label = { Text("Ara veya görev yaz") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    TextButton(onClick = { showTemplateForm = !showTemplateForm }) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (showTemplateForm) "Yeni görevi kapat" else "Hazır görev ekle")
                    }
                    if (showTemplateForm) {
                        TemplateForm(
                            text = templateText,
                            category = templateCategory,
                            subcategory = templateSubcategory,
                            favorite = templateFavorite,
                            updateText = { templateText = it },
                            updateCategory = { templateCategory = it },
                            updateSubcategory = { templateSubcategory = it },
                            updateFavorite = { templateFavorite = it },
                            save = {
                                addTemplate(templateText, templateCategory, templateSubcategory, templateFavorite)
                                query = templateText
                                templateText = ""
                                templateCategory = ""
                                templateSubcategory = ""
                                templateFavorite = false
                                showTemplateForm = false
                            },
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 410.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
                        val favorites = filteredTemplates.filter { it.favorite }
                        val remainingTemplates = filteredTemplates.filterNot { it.favorite }
                        if (favorites.isNotEmpty()) {
                            item { Text("Favoriler", style = MaterialTheme.typography.titleSmall) }
                            items(favorites, key = { "favorite-${it.id}" }) { template ->
                                TemplateRow(template, select, setFavorite, removeTemplate)
                            }
                        }
                        if (remainingTemplates.isNotEmpty()) {
                            item { Text("Hazır görevler", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = if (favorites.isEmpty()) 0.dp else 8.dp)) }
                            items(remainingTemplates, key = { it.id }) { template ->
                                TemplateRow(template, select, setFavorite, removeTemplate)
                            }
                        }
                        if (historicalMatches.isNotEmpty()) {
                            item { Text("Önceki görevler", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                            items(historicalMatches, key = { "history-${it.text}|${it.category}|${it.subcategory}" }) { suggestion ->
                                HistoricalSuggestionRow(suggestion, select, addTemplate, hideSuggestion)
                            }
                        }
                        if (filteredTemplates.isEmpty() && historicalMatches.isEmpty()) {
                            item { Text("Eşleşen görev yok. Yazdığını hazır görev olarak ekleyebilirsin.") }
                        }
                    }
                } else {
                    TextButton(onClick = { showCategoryForm = !showCategoryForm }) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (showCategoryForm) "Kategori eklemeyi kapat" else "Kategori ekle")
                    }
                    if (showCategoryForm) {
                        CategoryForm(
                            category = categoryText,
                            subcategory = subcategoryText,
                            updateCategory = { categoryText = it },
                            updateSubcategory = { subcategoryText = it },
                            save = {
                                addCategory(categoryText, subcategoryText)
                                categoryText = ""
                                subcategoryText = ""
                                showCategoryForm = false
                            },
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 410.dp)) {
                        if (catalog.categories.isEmpty()) item { Text("Henüz kategori yok.") }
                        items(catalog.categories, key = { it.id }) { option -> CategoryRow(option, removeCategory) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Kapat") } },
    )
}

@Composable
private fun HistoricalSuggestionRow(
    suggestion: AgendaTaskSuggestion,
    select: (AgendaTaskSuggestion) -> Unit,
    addTemplate: (String, String, String, Boolean) -> Unit,
    hideSuggestion: (String, String, String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(suggestion.text) },
        supportingContent = suggestionLabel(suggestion.category, suggestion.subcategory),
        trailingContent = {
            Row {
                IconButton(onClick = { addTemplate(suggestion.text, suggestion.category, suggestion.subcategory, true) }) {
                    Icon(Icons.Outlined.StarBorder, "Favoriye ve hazır görevlere ekle")
                }
                IconButton(onClick = { hideSuggestion(suggestion.text, suggestion.category, suggestion.subcategory) }) {
                    Icon(Icons.Outlined.DeleteOutline, "Önerilerden kaldır")
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { select(suggestion) },
    )
}

@Composable
private fun TemplateRow(
    template: AgendaTaskTemplate,
    select: (AgendaTaskSuggestion) -> Unit,
    setFavorite: (String, Boolean) -> Unit,
    removeTemplate: (String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(template.text) },
        supportingContent = suggestionLabel(template.category, template.subcategory),
        trailingContent = {
            Row {
                IconButton(onClick = { setFavorite(template.id, !template.favorite) }) {
                    Icon(if (template.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, if (template.favorite) "Favoriden çıkar" else "Favoriye ekle")
                }
                IconButton(onClick = { removeTemplate(template.id) }) {
                    Icon(Icons.Outlined.DeleteOutline, "Hazır görevden çıkar")
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { select(AgendaTaskSuggestion(template.text, template.category, template.subcategory)) },
    )
}

@Composable
private fun TemplateForm(
    text: String,
    category: String,
    subcategory: String,
    favorite: Boolean,
    updateText: (String) -> Unit,
    updateCategory: (String) -> Unit,
    updateSubcategory: (String) -> Unit,
    updateFavorite: (Boolean) -> Unit,
    save: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(text, { if (it.length <= 4000) updateText(it) }, label = { Text("Görev") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(category, { if (it.length <= 60) updateCategory(it) }, label = { Text("Kategori") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(subcategory, { if (it.length <= 60) updateSubcategory(it) }, label = { Text("Alt kategori") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        FilterChip(selected = favorite, onClick = { updateFavorite(!favorite) }, label = { Text("Favori") }, leadingIcon = { Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null) })
        TextButton(onClick = save, enabled = text.isNotBlank()) { Text("Hazır göreve kaydet") }
    }
}

@Composable
private fun CategoryForm(
    category: String,
    subcategory: String,
    updateCategory: (String) -> Unit,
    updateSubcategory: (String) -> Unit,
    save: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(category, { if (it.length <= 60) updateCategory(it) }, label = { Text("Kategori") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(subcategory, { if (it.length <= 60) updateSubcategory(it) }, label = { Text("Alt kategori, isteğe bağlı") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        TextButton(onClick = save, enabled = category.isNotBlank()) { Text("Kategoriye ekle") }
    }
}

@Composable
private fun CategoryRow(option: AgendaCategoryOption, remove: (Long) -> Unit) {
    ListItem(
        headlineContent = { Text(option.category) },
        supportingContent = if (option.subcategory.isNotBlank()) ({ Text(option.subcategory) }) else null,
        trailingContent = { IconButton(onClick = { remove(option.id) }) { Icon(Icons.Outlined.DeleteOutline, "Kategoriden çıkar") } },
    )
}

private fun suggestionLabel(category: String, subcategory: String): (@Composable () -> Unit)? =
    listOf(category, subcategory).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotBlank() }?.let { label -> { Text(label) } }

private fun suggestionKey(text: String, category: String, subcategory: String): String =
    listOf(text, category, subcategory).joinToString("\u001f") { it.trim().lowercase() }
