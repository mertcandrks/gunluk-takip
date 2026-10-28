package com.uykutakip.feature.agenda

import com.uykutakip.data.AgendaTaskSuggestion
import java.util.Locale

internal fun matchingTask(text: String, suggestions: List<AgendaTaskSuggestion>): AgendaTaskSuggestion? {
    fun normalized(value: String) = value.trim().lowercase(Locale.forLanguageTag("tr-TR"))
        .replace(Regex("\\s+"), " ")
    val key = normalized(text)
    if (key.isEmpty()) return null
    return suggestions.firstOrNull { normalized(it.text) == key && it.category.isNotBlank() }
}

internal fun taskLines(input: String, suggestions: List<AgendaTaskSuggestion>): List<AgendaTaskSuggestion> =
    input.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.map { text ->
        matchingTask(text, suggestions)?.copy(text = text) ?: AgendaTaskSuggestion(text, "", "")
    }.toList()
