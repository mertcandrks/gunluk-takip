package com.uykutakip.data

import android.content.Context
import androidx.room3.withWriteTransaction
import com.uykutakip.core.model.MealType
import com.uykutakip.data.local.ActiveMealEntity
import com.uykutakip.data.local.ActiveSleepEntity
import com.uykutakip.data.local.MealRecordEntity
import com.uykutakip.data.local.SleepRecordEntity
import com.uykutakip.data.local.TrackerDatabase
import org.json.JSONArray
import org.json.JSONObject

internal class LegacyDataMigrator(
    context: Context,
    private val database: TrackerDatabase,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun migrateIfNeeded() {
        if (preferences.getBoolean(MIGRATION_COMPLETE, false)) return

        val sleepRecords = parseSleepRecords(preferences.getString(SLEEP_RECORDS, null))
        val mealRecords = parseMealRecords(preferences.getString(MEAL_RECORDS, null))
        val activeSleep = preferences.getLong(ACTIVE_SLEEP, 0L).takeIf { it > 0L }?.let {
            ActiveSleepEntity(startMillis = it, updatedAtMillis = System.currentTimeMillis())
        }
        val activeMeal = preferences.getString(ACTIVE_MEAL, null)?.let(::parseActiveMeal)

        database.withWriteTransaction {
            if (sleepRecords.isNotEmpty()) database.sleepDao().importRecords(sleepRecords)
            if (mealRecords.isNotEmpty()) database.mealDao().importRecords(mealRecords)
            activeSleep?.let { database.sleepDao().importActive(it) }
            activeMeal?.let { database.mealDao().importActive(it) }
        }
        preferences.edit().putBoolean(MIGRATION_COMPLETE, true).commit()
    }

    private fun parseSleepRecords(raw: String?): List<SleepRecordEntity> = parseArray(raw) { json ->
        val start = json.optLong("start")
        val end = json.optLong("end")
        if (start <= 0L || end <= start) return@parseArray null
        SleepRecordEntity(
            id = start,
            startMillis = start,
            endMillis = end,
            createdAtMillis = start,
            updatedAtMillis = end,
        )
    }

    private fun parseMealRecords(raw: String?): List<MealRecordEntity> = parseArray(raw) { json ->
        val start = json.optLong("start")
        if (start <= 0L) return@parseArray null
        val end = json.optLong("end", start).coerceAtLeast(start)
        MealRecordEntity(
            id = json.optLong("id", start),
            type = MealType.fromLabel(json.optString("type")).name,
            startMillis = start,
            endMillis = end,
            itemsJson = (json.optJSONArray("items") ?: JSONArray()).toString(),
            isQuickRecord = json.optBoolean("quick"),
            createdAtMillis = start,
            updatedAtMillis = end,
        )
    }

    private fun parseActiveMeal(raw: String): ActiveMealEntity? = runCatching {
        val json = JSONObject(raw)
        val start = json.optLong("start")
        if (start <= 0L) return null
        ActiveMealEntity(
            id = json.optLong("id", start),
            type = MealType.fromLabel(json.optString("type")).name,
            startMillis = start,
            itemsJson = (json.optJSONArray("items") ?: JSONArray()).toString(),
            updatedAtMillis = System.currentTimeMillis(),
        )
    }.getOrNull()

    private fun <T> parseArray(raw: String?, mapper: (JSONObject) -> T?): List<T> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(mapper)?.let(::add)
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFERENCES_NAME = "sleep_tracker"
        const val MIGRATION_COMPLETE = "room_migration_v1_complete"
        const val ACTIVE_SLEEP = "active_start"
        const val SLEEP_RECORDS = "records"
        const val ACTIVE_MEAL = "active_meal"
        const val MEAL_RECORDS = "meal_records"
    }
}
