package com.uykutakip.feature.settings

import com.uykutakip.core.model.PrivateActivityRecord
import com.uykutakip.core.model.SleepRecord
import com.uykutakip.core.model.WaterIntake
import com.uykutakip.core.model.Idea
import com.uykutakip.core.model.ShoppingItem
import org.json.JSONObject

internal fun exportCsv(sleep: List<SleepRecord>, water: List<WaterIntake>, privateRecords: List<PrivateActivityRecord>): String = buildString {
    appendLine("type,date,start,end,duration_minutes")
    sleep.forEach { appendLine("sleep,${it.startMillis},${it.startMillis},${it.endMillis},${it.durationMillis / 60000}") }
    water.forEach { appendLine("water,${it.timestampMillis},,,${it.amountMl}") }
    privateRecords.filter { !it.isActive }.forEach { appendLine("private,${it.startMillis},${it.startMillis},${it.endMillis},${it.durationMillis / 60000}") }
}

internal fun exportJson(sleep: List<SleepRecord>, water: List<WaterIntake>, privateRecords: List<PrivateActivityRecord>, ideas: List<Idea> = emptyList(), shopping: List<ShoppingItem> = emptyList()): String = buildString {
    append("{\"sleep\":[")
    append(sleep.joinToString(",") { "{\"startMillis\":${it.startMillis},\"endMillis\":${it.endMillis}}" })
    append("],\"water\":[")
    append(water.joinToString(",") { "{\"timestampMillis\":${it.timestampMillis},\"amountMl\":${it.amountMl}}" })
    append("],\"private\":[")
    append(privateRecords.joinToString(",") { "{\"startMillis\":${it.startMillis},\"endMillis\":${it.endMillis ?: "null"}}" })
    append("]")
    if (ideas.isNotEmpty()) {
        append(",\"ideas\":[")
        append(ideas.joinToString(",") {
            JSONObject().put("id", it.id).put("text", it.text)
                .put("createdAtMillis", it.createdAtMillis).put("updatedAtMillis", it.updatedAtMillis).toString()
        })
        append("]")
    }
    if (shopping.isNotEmpty()) {
        append(",\"shopping\":[")
        append(shopping.joinToString(",") {
            JSONObject().put("id", it.id).put("text", it.text).put("longTerm", it.longTerm).put("purchased", it.purchased)
                .put("createdAtMillis", it.createdAtMillis).put("updatedAtMillis", it.updatedAtMillis).toString()
        })
        append("]")
    }
    append("}")
}
