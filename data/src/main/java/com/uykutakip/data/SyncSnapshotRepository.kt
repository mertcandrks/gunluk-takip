package com.uykutakip.data

import com.uykutakip.data.local.*
import com.uykutakip.sync.SyncSnapshot

class SyncSnapshotRepository internal constructor(
    private val sleepDao: SleepDao,
    private val mealDao: MealDao,
    private val hydrationDao: HydrationDao,
    private val agendaDao: AgendaDao,
    private val privateActivityDao: PrivateActivityDao,
    private val deletionDao: SyncDeletionDao,
) {
    suspend fun createSnapshot(): SyncSnapshot {
        val now = System.currentTimeMillis()
        val activeSleep = sleepDao.snapshotActive()
        val activeMeal = mealDao.snapshotActive()
        val settings = hydrationDao.snapshotSettings()
        return SyncSnapshot(
            now, activeSleep?.startMillis, activeSleep?.updatedAtMillis ?: 0L,
            sleepDao.snapshotRecords().map { SyncSnapshot.Sleep(it.id, it.startMillis, it.endMillis, it.updatedAtMillis) },
            activeMeal?.let { SyncSnapshot.Meal(it.id, it.type, it.startMillis, it.startMillis, it.itemsJson, false, it.updatedAtMillis) },
            mealDao.snapshotRecords().map { SyncSnapshot.Meal(it.id, it.type, it.startMillis, it.endMillis, it.itemsJson, it.isQuickRecord, it.updatedAtMillis) },
            hydrationDao.snapshotRecords().map { SyncSnapshot.Water(it.id, it.amountMl, it.timestampMillis, it.updatedAtMillis) },
            settings?.dailyGoalMl ?: 2_500, settings?.updatedAtMillis ?: 0L,
            agendaDao.snapshotRecords().map {
                SyncSnapshot.Agenda(it.id, it.day, it.text, it.isTask, it.completed, it.notDone, it.repeatDaily,
                    it.isGeneralGoal, it.streak, it.notifyEnabled, it.notifyHour, it.notifyMinute, it.category, it.subcategory,
                    it.lastCompletedDay, it.updatedAtMillis, it.projectPartsJson, it.dailyCountsJson)
            },
            agendaDao.snapshotTemplates().map {
                SyncSnapshot.AgendaTemplate(it.id, it.text, it.category, it.subcategory, it.favorite, it.builtIn, it.hidden, it.updatedAtMillis)
            },
            agendaDao.snapshotCategories().map {
                SyncSnapshot.AgendaCategory(it.id, it.category, it.subcategory, it.hidden, it.updatedAtMillis)
            },
            agendaDao.snapshotHiddenSuggestions().map { SyncSnapshot.HiddenAgendaSuggestion(it.suggestionKey, it.hiddenAtMillis) },
            privateActivityDao.snapshotRecords().map { SyncSnapshot.PrivateActivity(it.id, it.startMillis, it.endMillis, it.updatedAtMillis) },
            deletionDao.all().map { SyncSnapshot.Deletion(it.kind, it.recordId, it.updatedAtMillis) },
        )
    }

    suspend fun applySnapshot(remote: SyncSnapshot): ApplyResult {
        require(remote.generatedAtMillis > 0L)
        var applied = 0
        var deleted = 0
        val tombstones = (deletionDao.all().map { Triple(it.kind, it.recordId, it.updatedAtMillis) } +
            remote.deletions.map { Triple(it.kind, it.id, it.updatedAtMillis) })
            .groupBy { it.first to it.second }.mapValues { entry -> entry.value.maxOf { it.third } }

        val localSleeps = sleepDao.snapshotRecords().associateBy { it.id }
        remote.sleeps.filter { it.startMillis > 0 && it.endMillis > it.startMillis }.forEach { item ->
            if (wins(item.updatedAtMillis, localSleeps[item.id]?.updatedAtMillis, tombstones["sleep" to item.id])) {
                sleepDao.save(SleepRecordEntity(item.id, item.startMillis, item.endMillis, item.startMillis, item.updatedAtMillis))
                deletionDao.remove("sleep", item.id); applied++
            }
        }
        val localMeals = mealDao.snapshotRecords().associateBy { it.id }
        remote.meals.filter { it.type in MEAL_TYPES && it.endMillis >= it.startMillis && it.itemsJson.length <= MAX_TEXT }.forEach { item ->
            if (wins(item.updatedAtMillis, localMeals[item.id]?.updatedAtMillis, tombstones["meal" to item.id])) {
                mealDao.save(MealRecordEntity(item.id, item.type, item.startMillis, item.endMillis, item.itemsJson,
                    item.quick, item.startMillis, item.updatedAtMillis))
                deletionDao.remove("meal", item.id); applied++
            }
        }
        val localWater = hydrationDao.snapshotRecords().associateBy { it.id }
        remote.water.filter { it.amountMl in 10..5_000 && it.timestampMillis > 0 }.forEach { item ->
            if (wins(item.updatedAtMillis, localWater[item.id]?.updatedAtMillis, tombstones["water" to item.id])) {
                hydrationDao.add(WaterIntakeEntity(item.id, item.amountMl, item.timestampMillis, item.updatedAtMillis))
                deletionDao.remove("water", item.id); applied++
            }
        }
        val localAgenda = agendaDao.snapshotRecords().associateBy { it.id }
        remote.agenda.filter { it.day.matches(DAY_PATTERN) && it.text.isNotBlank() && it.text.length <= MAX_TEXT }.forEach { item ->
            if (wins(item.updatedAtMillis, localAgenda[item.id]?.updatedAtMillis, tombstones["agenda" to item.id])) {
                val parts = decodeAgendaParts(item.projectPartsJson)
                decodeDailyCounts(item.dailyCountsJson)
                require(parts.isEmpty() || item.task)
                val completed = if (parts.isEmpty()) item.completed else parts.all { it.progress == 100 }
                val local = localAgenda[item.id]
                // Timer state is device-local; older desktop snapshots must not erase it.
                val keepRunning = !completed && !item.notDone && item.task && !item.repeatDaily
                agendaDao.insert(AgendaEntity(item.id, item.day, item.text.trim(), item.task, completed,
                    item.repeatDaily, item.streak, item.lastCompletedDay, item.notifyEnabled,
                    item.notifyHour.coerceIn(0, 23), item.notifyMinute.coerceIn(0, 59), item.notDone,
                    item.generalGoal, item.category.take(60), item.subcategory.take(60), item.updatedAtMillis, encodeAgendaParts(parts), item.dailyCountsJson,
                    local?.let { if (keepRunning) it.elapsedMillis else agendaElapsed(it, System.currentTimeMillis()) } ?: 0,
                    if (keepRunning) local?.timerStartedAtMillis ?: 0 else 0,
                    // ponytail: priority stays device-local until sync protocol v8 adds it.
                    local?.priority ?: 0))
                deletionDao.remove("agenda", item.id); applied++
            }
        }
        val localTemplates = agendaDao.snapshotTemplates().associateBy { it.id }
        remote.agendaTemplates.filter { it.id.isNotBlank() && it.id.length <= 120 && it.text.isNotBlank() && it.text.length <= 4000 }.forEach { item ->
            if (item.updatedAtMillis > (localTemplates[item.id]?.updatedAtMillis ?: Long.MIN_VALUE)) {
                agendaDao.upsertTemplate(AgendaTaskTemplateEntity(item.id, item.text.trim(), item.category.take(60),
                    item.subcategory.take(60), item.favorite, item.builtIn, item.hidden, item.updatedAtMillis)); applied++
            }
        }
        val localCategories = agendaDao.snapshotCategories().associateBy { it.id }
        remote.agendaCategories.filter { it.category.isNotBlank() && it.category.length <= 60 && it.subcategory.length <= 60 }.forEach { item ->
            if (item.updatedAtMillis > (localCategories[item.id]?.updatedAtMillis ?: Long.MIN_VALUE)) {
                agendaDao.upsertCategory(AgendaCategoryEntity(item.id, item.category.trim(), item.subcategory.trim(), item.hidden, item.updatedAtMillis)); applied++
            }
        }
        val localHiddenSuggestions = agendaDao.snapshotHiddenSuggestions().associateBy { it.suggestionKey }
        remote.hiddenAgendaSuggestions.filter { it.key.isNotBlank() && it.key.length <= 4200 }.forEach { item ->
            if (item.hiddenAtMillis > (localHiddenSuggestions[item.key]?.hiddenAtMillis ?: Long.MIN_VALUE)) {
                agendaDao.upsertHiddenSuggestion(AgendaHiddenTaskSuggestionEntity(item.key, item.hiddenAtMillis)); applied++
            }
        }
        val localPrivate = privateActivityDao.snapshotRecords().associateBy { it.id }
        remote.privateActivities.filter { it.startMillis > 0 && (it.endMillis == null || it.endMillis > it.startMillis) }.forEach { item ->
            if (wins(item.updatedAtMillis, localPrivate[item.id]?.updatedAtMillis, tombstones["private" to item.id])) {
                privateActivityDao.insert(PrivateActivityEntity(item.id, item.startMillis, item.endMillis, item.updatedAtMillis))
                deletionDao.remove("private", item.id); applied++
            }
        }

        remote.deletions.filter { it.kind in KINDS && it.updatedAtMillis > 0 }.forEach { item ->
            val localUpdated = when (item.kind) {
                "sleep" -> localSleeps[item.id]?.updatedAtMillis
                "meal" -> localMeals[item.id]?.updatedAtMillis
                "water" -> localWater[item.id]?.updatedAtMillis
                "agenda" -> localAgenda[item.id]?.updatedAtMillis
                else -> localPrivate[item.id]?.updatedAtMillis
            } ?: Long.MIN_VALUE
            if (item.updatedAtMillis >= localUpdated) {
                when (item.kind) {
                    "sleep" -> sleepDao.delete(item.id)
                    "meal" -> mealDao.delete(item.id)
                    "water" -> hydrationDao.delete(item.id)
                    "agenda" -> agendaDao.delete(item.id)
                    "private" -> privateActivityDao.delete(item.id)
                }
                deletionDao.upsert(SyncDeletionEntity(item.kind, item.id, item.updatedAtMillis)); deleted++
            }
        }

        val localSleepActive = sleepDao.snapshotActive()
        if (remote.activeSleepStartMillis != null && remote.activeSleepUpdatedAtMillis > (localSleepActive?.updatedAtMillis ?: Long.MIN_VALUE)) {
            sleepDao.replaceActive(ActiveSleepEntity(startMillis = remote.activeSleepStartMillis, updatedAtMillis = remote.activeSleepUpdatedAtMillis)); applied++
        }
        val activeAfterMerge = sleepDao.snapshotActive()
        if (activeAfterMerge != null && remote.sleeps.any {
                it.startMillis == activeAfterMerge.startMillis && it.updatedAtMillis >= activeAfterMerge.updatedAtMillis
            }) {
            sleepDao.replaceActive(null); applied++
        }
        val localMealActive = mealDao.snapshotActive()
        remote.activeMeal?.takeIf { it.type in MEAL_TYPES && it.itemsJson.length <= MAX_TEXT }?.let { item ->
            if (item.updatedAtMillis > (localMealActive?.updatedAtMillis ?: Long.MIN_VALUE)) {
                mealDao.replaceActive(ActiveMealEntity(id = item.id, type = item.type, startMillis = item.startMillis,
                    itemsJson = item.itemsJson, updatedAtMillis = item.updatedAtMillis)); applied++
            }
        }
        val activeMealAfterMerge = mealDao.snapshotActive()
        if (activeMealAfterMerge != null && remote.meals.any {
                it.id == activeMealAfterMerge.id && it.updatedAtMillis >= activeMealAfterMerge.updatedAtMillis
            }) {
            mealDao.replaceActive(null); applied++
        }
        if (remote.waterGoalMl in 500..10_000 && remote.waterGoalUpdatedAtMillis > (hydrationDao.snapshotSettings()?.updatedAtMillis ?: Long.MIN_VALUE)) {
            hydrationDao.saveSettings(HydrationSettingsEntity(dailyGoalMl = remote.waterGoalMl,
                updatedAtMillis = remote.waterGoalUpdatedAtMillis)); applied++
        }
        return ApplyResult(applied, deleted)
    }

    data class ApplyResult(val applied: Int, val deleted: Int)

    private fun wins(remote: Long, local: Long?, deleted: Long?): Boolean =
        remote > (local ?: Long.MIN_VALUE) && remote > (deleted ?: Long.MIN_VALUE)

    private companion object {
        const val MAX_TEXT = 1_000_000
        val DAY_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        val MEAL_TYPES = setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")
        val KINDS = setOf("sleep", "meal", "water", "agenda", "private")
    }
}
