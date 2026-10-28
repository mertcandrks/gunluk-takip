package com.uykutakip.data

import com.uykutakip.data.local.TrackerDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class McpRepository internal constructor(private val db: TrackerDatabase, private val data: TrackerDataProvider) {
    suspend fun execute(tool: String, args: JSONObject, write: Boolean): JSONObject {
        if (!write) return read(tool, args)
        val requestId = args.getString("requestId")
        require(requestId.matches(Regex("[a-zA-Z0-9_-]{8,80}")))
        val hash = mcpHash(tool + ":" + canonicalJson(args))
        val encoded = db.mcpCommandDao().execute(requestId, hash, tool) { mutate(tool, args).toString() }
        val result = JSONObject(encoded)
        // Replaying an alarm cancellation is safe even if the process died after the DB commit.
        result.optJSONArray("cancelAlarms")?.let { alarms ->
            for (i in 0 until alarms.length()) data.reminderScheduler.cancel(alarms.getInt(i))
        }
        result.remove("cancelAlarms")
        return result.put("requestId", requestId)
    }

    private fun day(args: JSONObject) = LocalDate.parse(args.optString("day", LocalDate.now().toString()))
    private fun inRange(millis: Long, args: JSONObject): Boolean {
        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        return (!args.has("from") || date >= LocalDate.parse(args.getString("from"))) &&
            (!args.has("to") || date <= LocalDate.parse(args.getString("to")))
    }

    private suspend fun read(tool: String, a: JSONObject): JSONObject = when (tool) {
        "agenda_list" -> {
            val selected = day(a).toString()
            val rows = db.agendaDao().snapshotRecords().filter {
                (a.optBoolean("allDates") || it.day == selected || (it.repeatDaily && it.day <= selected) || (it.isGeneralGoal && (!it.completed || it.projectPartsJson != "[]"))) &&
                    it.text.contains(a.optString("search", ""), ignoreCase = true)
            }
            page(rows.map { it.mcpJson().withRevision() }, a).put("day", selected)
        }
        "sleep_list" -> page(db.sleepDao().snapshotRecords().filter { inRange(it.endMillis, a) }.map { it.mcpJson().withRevision() }, a)
            .put("active", db.sleepDao().snapshotActive()?.mcpJson()?.withRevision() ?: JSONObject.NULL)
        "ideas_list" -> page(db.ideaDao().snapshotRecords().filter { it.text.contains(a.optString("search", ""), true) }.map { it.mcpJson().withRevision() }, a)
        "shopping_list" -> page(db.shoppingDao().snapshotRecords().filter { it.text.contains(a.optString("search", ""), true) }.map { it.mcpJson().withRevision() }, a)
        "private_list" -> page(db.privateActivityDao().snapshotRecords().filter { inRange(it.startMillis, a) }.map { it.mcpJson().withRevision() }, a)
        "catalog_list" -> when (a.optString("kind", "templates")) {
            "templates" -> page(db.agendaDao().snapshotTemplates().filterNot { it.hidden }.map { it.mcpJson().withRevision() }, a)
            "categories" -> page(db.agendaDao().snapshotCategories().filterNot { it.hidden }.map { it.mcpJson().withRevision() }, a)
            else -> error("INVALID_ARGUMENT")
        }
        "settings_get" -> data.settingsRepository.settings.value.let {
            json("goalMinutes" to it.goalMinutes, "bedtimeEnabled" to it.bedtimeEnabled, "bedtimeHour" to it.bedtimeHour,
                "bedtimeMinute" to it.bedtimeMinute, "wakeEnabled" to it.wakeEnabled, "wakeHour" to it.wakeHour, "wakeMinute" to it.wakeMinute)
        }
        "statistics_get" -> {
            val sleeps = db.sleepDao().snapshotRecords().filter { inRange(it.endMillis, a) }
            val entries = db.agendaDao().snapshotRecords().filter {
                (!a.has("from") || it.day >= LocalDate.parse(a.getString("from")).toString()) &&
                    (!a.has("to") || it.day <= LocalDate.parse(a.getString("to")).toString())
            }
            json("sleepRecords" to sleeps.size, "sleepDurationMillis" to sleeps.sumOf { it.endMillis - it.startMillis },
                "tasks" to entries.count { it.isTask }, "completedTasks" to entries.count { it.isTask && it.completed },
                "recordedTaskMillis" to entries.sumOf { it.elapsedMillis })
        }
        "record_delete_preview" -> {
            val record = record(a.getString("kind"), a.getLong("id"))
            json("record" to JSONObject(record.toString()).withRevision(),
                "confirmation" to mcpHash("delete:" + a.getString("kind") + ":" + canonicalJson(record)), "reversible" to false)
        }
        else -> error("UNKNOWN_TOOL")
    }

    private suspend fun record(kind: String, id: Long): JSONObject = when (kind) {
        "agenda" -> requireNotNull(db.agendaDao().find(id)).mcpJson()
        "sleep" -> requireNotNull(db.sleepDao().snapshotRecords().find { it.id == id }).mcpJson()
        "idea" -> requireNotNull(db.ideaDao().find(id)).mcpJson()
        "shopping" -> requireNotNull(db.shoppingDao().find(id)).mcpJson()
        "private" -> requireNotNull(db.privateActivityDao().snapshotRecords().find { it.id == id }).mcpJson()
        else -> error("INVALID_ARGUMENT")
    }

    private suspend fun mutate(tool: String, a: JSONObject): JSONObject = when (tool) {
        "agenda_write" -> agenda(a)
        "sleep_write" -> sleep(a)
        "ideas_write" -> {
            val id = a.optLong("id", 0)
            val action = a.getString("action")
            if (action != "create") guardRevision(record("idea", id), a)
            when (action) {
                "create", "edit" -> {
                    require((action == "create") == (id == 0L))
                    val before = if (id == 0L) db.ideaDao().snapshotRecords().map { it.id }.toSet() else emptySet()
                    data.ideasRepository.save(id, a.getString("text"))
                    val resultId = if (id == 0L) db.ideaDao().snapshotRecords().first { it.id !in before }.id else id
                    record("idea", resultId).withRevision()
                }
                "finish" -> { data.ideasRepository.finish(id); record("idea", id).withRevision() }
                else -> error("INVALID_ARGUMENT")
            }
        }
        "shopping_write" -> {
            val id = a.optLong("id", 0)
            val action = a.getString("action")
            if (action != "create") guardRevision(record("shopping", id), a)
            val before = if (id == 0L) db.shoppingDao().snapshotRecords().map { it.id }.toSet() else emptySet()
            when (action) {
                "create", "edit" -> {
                    require((action == "create") == (id == 0L))
                    data.shoppingRepository.save(id, a.getString("text"), a.getBoolean("longTerm"))
                }
                "set_purchased" -> if (requireNotNull(db.shoppingDao().find(id)).purchased != a.getBoolean("purchased")) data.shoppingRepository.toggle(id)
                else -> error("INVALID_ARGUMENT")
            }
            record("shopping", if (id == 0L) db.shoppingDao().snapshotRecords().first { it.id !in before }.id else id).withRevision()
        }
        "catalog_write" -> catalog(a)
        "private_write" -> {
            val action = a.getString("action")
            val id = a.optLong("id", 0)
            val before = db.privateActivityDao().snapshotRecords()
            when (action) {
                "start" -> { check(db.privateActivityDao().active() == null) { "ALREADY_ACTIVE" }; check(data.privateActivityRepository.start()) }
                "finish" -> {
                    val active = requireNotNull(db.privateActivityDao().active())
                    guardRevision(active.mcpJson(), a)
                    check(data.privateActivityRepository.finish(a.optLong("endMillis", System.currentTimeMillis())))
                }
                "create" -> check(data.privateActivityRepository.addRecord(a.getLong("startMillis"), a.getLong("endMillis")))
                "edit" -> { guardRevision(record("private", id), a); check(data.privateActivityRepository.updateRecord(id, a.getLong("startMillis"), a.getLong("endMillis"))) }
                else -> error("INVALID_ARGUMENT")
            }
            val updated = db.privateActivityDao().snapshotRecords().firstOrNull { row -> before.none { it == row } }
            requireNotNull(updated).mcpJson().withRevision()
        }
        "record_delete" -> {
            val kind = a.getString("kind"); val id = a.getLong("id")
            val before = record(kind, id)
            guardRevision(before, a)
            check(a.getString("confirmation") == mcpHash("delete:$kind:" + canonicalJson(before))) { "PREVIEW_REQUIRED" }
            when (kind) {
                "agenda" -> data.agendaRepository.delete(id)
                "sleep" -> data.sleepRepository.deleteRecord(id)
                "idea" -> data.ideasRepository.delete(id)
                "shopping" -> data.shoppingRepository.delete(id)
                "private" -> data.privateActivityRepository.deleteRecord(id)
            }
            json("deletedId" to id, "kind" to kind).apply {
                if (kind == "agenda" && before.getBoolean("notifyEnabled")) put("cancelAlarms", JSONArray().put(alarmCode(before)))
            }
        }
        else -> error("UNKNOWN_TOOL")
    }

    private suspend fun agenda(a: JSONObject): JSONObject {
        val action = a.getString("action")
        val id = a.optLong("id", 0)
        if (action == "create" || action == "create_batch") {
            require(id == 0L)
            require(!a.optBoolean("repeatDaily") || (a.optBoolean("isTask", true) && !a.optBoolean("isGeneralGoal")))
            val before = db.agendaDao().snapshotRecords().map { it.id }.toSet()
            if (action == "create") data.agendaRepository.add(day(a), a.getString("text"), a.optBoolean("isTask", true),
                repeatDaily = a.optBoolean("repeatDaily"), isGeneralGoal = a.optBoolean("isGeneralGoal"),
                category = a.optString("category", ""), subcategory = a.optString("subcategory", ""))
            else {
                val tasks = a.getJSONArray("tasks")
                data.agendaRepository.addTasks(day(a), List(tasks.length()) { i -> tasks.getJSONObject(i).let {
                    AgendaTaskSuggestion(it.getString("text"), it.optString("category", ""), it.optString("subcategory", ""))
                } })
            }
            return json("created" to JSONArray(db.agendaDao().snapshotRecords().filter { it.id !in before }.map { it.mcpJson().withRevision() }))
        }
        val entry = requireNotNull(db.agendaDao().find(id))
        guardRevision(entry.mcpJson(), a)
        val repo = data.agendaRepository
        when (action) {
            "edit" -> repo.edit(id, a.getString("text"), a.optString("category", entry.category), a.optString("subcategory", entry.subcategory))
            "status" -> {
                require(entry.isTask)
                when (a.getString("status")) {
                    "completed" -> if (entry.repeatDaily) repo.adjustDailyCount(id, day(a), 1) else repo.finishTask(id, day(a))
                    "planned" -> {
                        require(!entry.repeatDaily)
                        if (entry.projectPartsJson != "[]") repo.saveParts(id, decodeAgendaParts(entry.projectPartsJson).map { it.copy(progress = 0) })
                        else { repo.complete(id, false, day(a)); repo.markNotDone(id, false) }
                    }
                    "not_done" -> { require(!entry.repeatDaily && entry.projectPartsJson == "[]"); repo.markNotDone(id, true) }
                    else -> error("INVALID_ARGUMENT")
                }
            }
            "daily_count" -> repo.adjustDailyCount(id, day(a), a.getInt("delta"))
            "move" -> {
                require(entry.isTask && !entry.repeatDaily)
                if (a.getBoolean("general")) { require(!entry.isGeneralGoal); repo.promoteToGeneralGoal(id) }
                else if (entry.isGeneralGoal) repo.moveGeneralGoalToDay(id, day(a)) else repo.postponeToDay(id, day(a))
            }
            "parts" -> { require(entry.isTask && !entry.repeatDaily); repo.saveParts(id, decodeAgendaParts(a.getJSONArray("parts").toString())) }
            "timer" -> repo.setTimer(id, a.getBoolean("running"))
            "finish" -> repo.finishTask(id, day(a))
            "merge" -> {
                val targetId = a.getLong("targetId")
                guardRevision(record("agenda", targetId), a, "targetRevision")
                repo.mergeTasks(id, targetId)
                return record("agenda", targetId).withRevision().apply {
                    if (entry.notifyEnabled) put("cancelAlarms", JSONArray().put(alarmCode(entry.mcpJson())))
                }
            }
            else -> error("INVALID_ARGUMENT")
        }
        return record("agenda", id).withRevision()
    }

    private suspend fun sleep(a: JSONObject): JSONObject {
        val repo = data.sleepRepository
        val action = a.getString("action")
        val now = System.currentTimeMillis()
        return when (action) {
            "start" -> { check(db.sleepDao().snapshotActive() == null) { "ALREADY_ACTIVE" }; check(repo.start()); requireNotNull(db.sleepDao().snapshotActive()).mcpJson().withRevision() }
            "finish", "update_start" -> {
                guardRevision(requireNotNull(db.sleepDao().snapshotActive()).mcpJson(), a)
                if (action == "finish") {
                    val end = a.optLong("endMillis", now)
                    require(end <= now + 60_000)
                    val finished = requireNotNull(repo.finish(end))
                    record("sleep", finished.id).withRevision()
                } else { check(repo.updateActiveStart(a.getLong("startMillis"))); requireNotNull(db.sleepDao().snapshotActive()).mcpJson().withRevision() }
            }
            "create" -> {
                val record = requireNotNull(repo.addRecord(a.getLong("startMillis"), a.getLong("endMillis")))
                record("sleep", record.id).withRevision()
            }
            "edit" -> {
                val id = a.getLong("id"); guardRevision(record("sleep", id), a)
                requireNotNull(repo.updateRecord(id, a.getLong("startMillis"), a.getLong("endMillis")))
                record("sleep", id).withRevision()
            }
            else -> error("INVALID_ARGUMENT")
        }
    }

    private suspend fun catalog(a: JSONObject): JSONObject {
        val repo = data.agendaRepository
        when (a.getString("action")) {
            "template_save" -> repo.saveTaskTemplate(a.getString("text"), a.optString("category", ""), a.optString("subcategory", ""), a.optBoolean("favorite"))
            "template_favorite", "template_hide" -> {
                val id = a.getString("templateId")
                guardRevision(requireNotNull(db.agendaDao().snapshotTemplates().find { it.id == id }).mcpJson(), a)
                if (a.getString("action") == "template_hide") repo.removeTaskTemplate(id) else repo.setTaskTemplateFavorite(id, a.getBoolean("favorite"))
            }
            "category_save" -> { require(a.getString("category").isNotBlank()); repo.saveCategory(a.getString("category"), a.optString("subcategory", "")) }
            "category_hide" -> {
                val id = a.getLong("id")
                guardRevision(requireNotNull(db.agendaDao().snapshotCategories().find { it.id == id }).mcpJson(), a)
                repo.removeCategory(id)
            }
            "suggestion_hide" -> repo.hideTaskSuggestion(a.getString("text"), a.optString("category", ""), a.optString("subcategory", ""))
            else -> error("INVALID_ARGUMENT")
        }
        return json("changed" to true)
    }

    private fun alarmCode(record: JSONObject) = "${record.getString("day")}|${record.getString("text")}|${record.getInt("notifyHour")}:${record.getInt("notifyMinute")}".hashCode()
}
