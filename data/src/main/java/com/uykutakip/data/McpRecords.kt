package com.uykutakip.data

import com.uykutakip.data.local.*
import org.json.JSONArray
import org.json.JSONObject

internal fun AgendaEntity.mcpJson() = json("id" to id, "day" to day, "text" to text, "isTask" to isTask,
    "completed" to completed, "notDone" to notDone, "repeatDaily" to repeatDaily, "isGeneralGoal" to isGeneralGoal,
    "category" to category, "subcategory" to subcategory, "streak" to streak, "lastCompletedDay" to lastCompletedDay,
    "notifyEnabled" to notifyEnabled, "notifyHour" to notifyHour, "notifyMinute" to notifyMinute,
    "updatedAtMillis" to updatedAtMillis, "parts" to JSONArray(projectPartsJson), "dailyCounts" to JSONObject(dailyCountsJson),
    "elapsedMillis" to elapsedMillis, "timerStartedAtMillis" to timerStartedAtMillis)

internal fun SleepRecordEntity.mcpJson() = json("id" to id, "startMillis" to startMillis, "endMillis" to endMillis,
    "durationMillis" to (endMillis - startMillis), "updatedAtMillis" to updatedAtMillis)
internal fun ActiveSleepEntity.mcpJson() = json("startMillis" to startMillis, "updatedAtMillis" to updatedAtMillis)
internal fun IdeaEntity.mcpJson() = json("id" to id, "text" to text, "createdAtMillis" to createdAtMillis,
    "updatedAtMillis" to updatedAtMillis, "completedAtMillis" to completedAtMillis)
internal fun ShoppingEntity.mcpJson() = json("id" to id, "text" to text, "longTerm" to longTerm, "purchased" to purchased,
    "createdAtMillis" to createdAtMillis, "updatedAtMillis" to updatedAtMillis)
internal fun PrivateActivityEntity.mcpJson() = json("id" to id, "startMillis" to startMillis,
    "endMillis" to endMillis, "updatedAtMillis" to updatedAtMillis)
internal fun AgendaTaskTemplateEntity.mcpJson() = json("id" to id, "text" to text, "category" to category,
    "subcategory" to subcategory, "favorite" to favorite, "builtIn" to builtIn, "hidden" to hidden, "updatedAtMillis" to updatedAtMillis)
internal fun AgendaCategoryEntity.mcpJson() = json("id" to id, "category" to category, "subcategory" to subcategory,
    "hidden" to hidden, "updatedAtMillis" to updatedAtMillis)
