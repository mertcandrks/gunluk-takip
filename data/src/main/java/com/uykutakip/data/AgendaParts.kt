package com.uykutakip.data

import com.uykutakip.core.model.AgendaPart
import org.json.JSONArray
import org.json.JSONObject

internal fun decodeAgendaParts(raw: String): List<AgendaPart> {
    if (raw.isBlank() || raw == "[]") return emptyList()
    require(raw.length <= 100_000)
    val array = JSONArray(raw.ifBlank { "[]" })
    require(array.length() <= 100)
    val parts = List(array.length()) { index ->
        val part = array.getJSONObject(index)
        val progress = part.getDouble("progress")
        require(progress.isFinite() && progress == progress.toInt().toDouble())
        AgendaPart(part.getString("id"), part.getString("title"), progress.toInt())
    }
    require(parts.map { it.id }.distinct().size == parts.size)
    return parts
}

internal fun encodeAgendaParts(parts: List<AgendaPart>): String {
    if (parts.isEmpty()) return "[]"
    require(parts.size <= 100 && parts.map { it.id }.distinct().size == parts.size)
    return JSONArray().apply {
        parts.forEach { part -> put(JSONObject().apply {
            put("id", part.id); put("title", part.title); put("progress", part.progress)
        }) }
    }.toString()
}
