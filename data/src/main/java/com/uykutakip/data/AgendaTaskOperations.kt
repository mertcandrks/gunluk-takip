package com.uykutakip.data

import com.uykutakip.data.local.AgendaEntity
import com.uykutakip.core.model.AgendaPart
import java.util.UUID

internal fun agendaElapsed(entry: AgendaEntity, now: Long): Long =
    Math.addExact(entry.elapsedMillis, if (entry.timerStartedAtMillis > 0) (now - entry.timerStartedAtMillis).coerceAtLeast(0) else 0)

internal fun mergeAgendaTasks(source: AgendaEntity, target: AgendaEntity, now: Long): AgendaEntity {
    require(source.id != target.id)
    require(listOf(source, target).all { it.isTask && !it.repeatDaily && !it.completed && !it.notDone })
    val sourceCategory = listOf(source.category, source.subcategory).filter { it.isNotBlank() }.joinToString(" / ")
    val text = listOf(target.text, source.text, sourceCategory.takeIf { it.isNotBlank() }?.let { "[$it]" }).filterNotNull().joinToString("\n\n")
    require(text.length <= 4000) { "Merged text is too long" }
    val parts = mergeAgendaParts(decodeAgendaParts(source.projectPartsJson), decodeAgendaParts(target.projectPartsJson))
    return target.copy(text = text, projectPartsJson = encodeAgendaParts(parts),
        elapsedMillis = Math.addExact(agendaElapsed(source, now), agendaElapsed(target, now)),
        timerStartedAtMillis = 0, updatedAtMillis = now)
}

internal fun mergeAgendaParts(sourceParts: List<AgendaPart>, targetParts: List<AgendaPart>): List<AgendaPart> {
    val ids = targetParts.map { it.id }.toMutableSet()
    val parts = targetParts + sourceParts.map { part ->
        val id = if (ids.add(part.id)) part.id else UUID.randomUUID().toString().also { ids.add(it) }
        part.copy(id = id)
    }
    require(parts.size <= 100)
    return parts
}
