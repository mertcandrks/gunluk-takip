package com.uykutakip.data

import android.database.sqlite.SQLiteConstraintException
import com.uykutakip.core.model.FoodCategory
import com.uykutakip.core.model.FoodEntry
import com.uykutakip.core.model.FoodUnit
import com.uykutakip.core.model.MealRecord
import com.uykutakip.core.model.MealType
import com.uykutakip.data.local.ActiveMealEntity
import com.uykutakip.data.local.MealDao
import com.uykutakip.data.local.MealRecordEntity
import com.uykutakip.data.local.SyncDeletionDao
import com.uykutakip.data.local.SyncDeletionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject

internal class OfflineMealRepository(
    private val dao: MealDao,
    private val deletions: SyncDeletionDao = NoopSyncDeletionDao,
) : MealRepository {
    override val records: Flow<List<MealRecord>> = dao.observeRecords()
        .map { rows -> rows.map { it.toModel() } }.flowOn(Dispatchers.Default)
    override val activeMeal: Flow<MealRecord?> = dao.observeActive()
        .map { it?.toModel() }.flowOn(Dispatchers.Default)

    override suspend fun start(type: MealType, now: Long): Boolean {
        return try {
            dao.start(
                ActiveMealEntity(
                    id = 0L,
                    type = type.name,
                    startMillis = now,
                    itemsJson = "[]",
                    updatedAtMillis = now,
                ),
            )
            true
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    override suspend fun addItem(item: FoodEntry): Boolean {
        if (!item.isValid()) return false
        val active = dao.currentActive()?.toModel() ?: return false
        return dao.updateItems(itemsToJson(active.items + item.normalized()), System.currentTimeMillis()) > 0
    }

    override suspend fun removeItem(index: Int): Boolean {
        val active = dao.currentActive()?.toModel() ?: return false
        if (index !in active.items.indices) return false
        return dao.updateItems(
            itemsToJson(active.items.filterIndexed { itemIndex, _ -> itemIndex != index }),
            System.currentTimeMillis(),
        ) > 0
    }

    override suspend fun finish(now: Long): MealRecord? = dao.finish(now, System.currentTimeMillis())?.toModel()

    override suspend fun addQuick(type: MealType, item: FoodEntry, now: Long): MealRecord? {
        if (!item.isValid()) return null
        val record = MealRecord(0L, type, now, now, listOf(item.normalized()), true)
        val id = dao.save(record.toEntity())
        return record.copy(id = id)
    }

    override suspend fun addRecord(record: MealRecord): MealRecord? {
        val endMillis = record.endMillis ?: return null
        if (!record.type.isEating || record.items.isEmpty() || record.startMillis >= endMillis) return null
        if (endMillis > System.currentTimeMillis() + 60_000L || record.items.any { !it.isValid() }) return null
        val normalized = record.copy(
            id = 0L,
            items = record.items.map { it.normalized() },
            isQuickRecord = false,
        )
        val id = dao.save(normalized.toEntity())
        return normalized.copy(id = id)
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
        deletions.upsert(SyncDeletionEntity("meal", id, System.currentTimeMillis()))
    }
    override suspend fun clearRecords() {
        val now = System.currentTimeMillis()
        dao.snapshotRecords().forEach { deletions.upsert(SyncDeletionEntity("meal", it.id, now)) }
        dao.clearRecords()
    }

    private fun MealRecordEntity.toModel() = MealRecord(
        id = id,
        type = type.toMealType(),
        startMillis = startMillis,
        endMillis = endMillis,
        items = jsonToItems(itemsJson),
        isQuickRecord = isQuickRecord,
    )

    private fun ActiveMealEntity.toModel() = MealRecord(
        id = id,
        type = type.toMealType(),
        startMillis = startMillis,
        items = jsonToItems(itemsJson),
    )

    private fun MealRecord.toEntity() = MealRecordEntity(
        id = id,
        type = type.name,
        startMillis = startMillis,
        endMillis = endMillis ?: startMillis,
        itemsJson = itemsToJson(items),
        isQuickRecord = isQuickRecord,
        createdAtMillis = startMillis,
        updatedAtMillis = System.currentTimeMillis(),
    )

    private fun String.toMealType() = runCatching { MealType.valueOf(this) }.getOrDefault(MealType.SNACK)

    private fun jsonToItems(raw: String): List<FoodEntry> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                when (val value = array.opt(index)) {
                    is JSONObject -> value.toFoodEntry()?.let(::add)
                    is String -> value.toLegacyFoodEntry()?.let(::add)
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun itemsToJson(items: List<FoodEntry>) = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("name", item.name)
                put("quantity", item.quantity)
                put("unit", item.unit.name)
                put("category", item.category.name)
            })
        }
    }.toString()

    private fun JSONObject.toFoodEntry(): FoodEntry? {
        val name = optString("name").trim()
        val quantity = optDouble("quantity", 1.0)
        if (name.isBlank() || !quantity.isFinite() || quantity <= 0.0) return null
        return FoodEntry(
            name = name,
            quantity = quantity,
            unit = enumValueOrDefault(optString("unit"), FoodUnit.PORTION),
            category = enumValueOrDefault(optString("category"), FoodCategory.OTHER),
        )
    }

    private fun String.toLegacyFoodEntry(): FoodEntry? {
        val value = trim()
        if (value.isBlank()) return null
        val match = LEGACY_QUANTITY.matchEntire(value)
        val quantity = match?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 1.0
        val remainder = match?.groupValues?.get(2)?.trim().orEmpty()
        val unitMatch = FoodUnit.entries.firstOrNull { remainder.startsWith("${it.label} ", ignoreCase = true) }
        val name = when {
            match == null -> value
            unitMatch != null -> remainder.substring(unitMatch.label.length).trim()
            else -> remainder
        }
        return FoodEntry(
            name = name.replaceFirstChar { it.titlecase() },
            quantity = quantity,
            unit = unitMatch ?: FoodUnit.PIECE,
            category = FoodCategory.OTHER,
        )
    }

    private fun FoodEntry.isValid() = name.isNotBlank() && quantity.isFinite() && quantity > 0.0
    private fun FoodEntry.normalized() = copy(name = name.trim().replaceFirstChar { it.titlecase() })

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private companion object {
        val LEGACY_QUANTITY = Regex("^(\\d+(?:[.,]\\d+)?)\\s+(.+)$")
    }
}
