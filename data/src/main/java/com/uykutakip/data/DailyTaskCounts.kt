package com.uykutakip.data

import java.time.LocalDate
import org.json.JSONObject

internal fun decodeDailyCounts(raw: String): Map<String, Int> {
    if (raw.isBlank() || raw == "{}") return emptyMap()
    require(raw.length <= 200_000)
    val json = JSONObject(raw)
    require(json.length() <= 10_000)
    return json.keys().asSequence().associateWith { day ->
        require(LocalDate.parse(day).toString() == day)
        val count = json.getDouble(day)
        require(count.isFinite() && count == count.toInt().toDouble() && count in 0.0..9999.0)
        count.toInt()
    }
}
