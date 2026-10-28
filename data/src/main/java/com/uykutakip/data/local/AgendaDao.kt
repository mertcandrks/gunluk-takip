package com.uykutakip.data.local

import androidx.room3.*
import com.uykutakip.data.AgendaCategoryOption
import com.uykutakip.data.AgendaTaskSuggestion
import com.uykutakip.data.AgendaTaskTemplate
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "agenda_entries", indices = [Index("day")])
internal data class AgendaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val text: String,
    val isTask: Boolean,
    val completed: Boolean,
    @ColumnInfo(defaultValue = "0") val repeatDaily: Boolean,
    @ColumnInfo(defaultValue = "0") val streak: Int,
    @ColumnInfo(defaultValue = "NULL") val lastCompletedDay: String?,
    @ColumnInfo(defaultValue = "0") val notifyEnabled: Boolean,
    @ColumnInfo(defaultValue = "20") val notifyHour: Int,
    @ColumnInfo(defaultValue = "0") val notifyMinute: Int,
    @ColumnInfo(defaultValue = "0") val notDone: Boolean,
    @ColumnInfo(defaultValue = "0") val isGeneralGoal: Boolean,
    @ColumnInfo(defaultValue = "''") val category: String = "",
    @ColumnInfo(defaultValue = "''") val subcategory: String = "",
    @ColumnInfo(defaultValue = "0") val updatedAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'[]'") val projectPartsJson: String = "[]",
    @ColumnInfo(defaultValue = "'{}'") val dailyCountsJson: String = "{}",
    @ColumnInfo(defaultValue = "0") val elapsedMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val timerStartedAtMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val priority: Int = 0,
)

@Dao
internal interface AgendaDao {
    @Transaction
    suspend fun finishTask(id: Long, day: String, now: Long) {
        val entry = requireNotNull(find(id))
        require(entry.isTask && !entry.repeatDaily)
        val parts = com.uykutakip.data.decodeAgendaParts(entry.projectPartsJson)
        if (parts.isEmpty()) finishTracked(id, true, day, now)
        else saveTrackedParts(id, com.uykutakip.data.encodeAgendaParts(parts.map { it.copy(progress = 100) }), true, now)
    }

    @Transaction
    suspend fun insertBatch(entries: List<AgendaEntity>) {
        entries.forEach {
            insert(it)
            if (it.category.isNotBlank() && restoreCategory(it.category, it.subcategory, it.updatedAtMillis) == 0) {
                insertCategory(AgendaCategoryEntity(category = it.category, subcategory = it.subcategory))
            }
        }
    }

    @Transaction
    suspend fun setTimer(id: Long, running: Boolean, now: Long) {
        val entry = requireNotNull(find(id))
        require(entry.isTask && !entry.repeatDaily && !entry.completed && !entry.notDone)
        if (running == (entry.timerStartedAtMillis > 0)) return
        insert(entry.copy(
            elapsedMillis = com.uykutakip.data.agendaElapsed(entry, now),
            timerStartedAtMillis = if (running) now else 0,
            updatedAtMillis = now,
        ))
    }

    @Transaction
    suspend fun pauseTimer(id: Long, now: Long) {
        val entry = find(id) ?: return
        if (entry.timerStartedAtMillis > 0) insert(entry.copy(
            elapsedMillis = com.uykutakip.data.agendaElapsed(entry, now),
            timerStartedAtMillis = 0, updatedAtMillis = now,
        ))
    }

    @Transaction
    suspend fun finishTracked(id: Long, completed: Boolean, day: String, now: Long) {
        if (completed) pauseTimer(id, now)
        complete(id, completed, day, now)
    }

    @Transaction
    suspend fun saveTrackedParts(id: Long, partsJson: String, completed: Boolean, now: Long) {
        if (completed) pauseTimer(id, now)
        saveParts(id, partsJson, completed, now)
    }

    @Transaction
    suspend fun markTrackedNotDone(id: Long, notDone: Boolean, now: Long) {
        if (notDone) pauseTimer(id, now)
        markNotDone(id, notDone, now)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordMergeDeletion(entry: SyncDeletionEntity)

    @Transaction
    suspend fun mergeTasks(sourceId: Long, targetId: Long, now: Long) {
        val source = requireNotNull(find(sourceId))
        val target = requireNotNull(find(targetId))
        val merged = com.uykutakip.data.mergeAgendaTasks(source, target, now)
        insert(merged)
        listOf(source, target).forEach {
            upsertTemplate(AgendaTaskTemplateEntity("merged-${it.id}-$now", it.text, it.category, it.subcategory, updatedAtMillis = now))
        }
        recordMergeDeletion(SyncDeletionEntity("agenda", source.id, now))
        delete(source.id)
    }

    @Query("SELECT * FROM agenda_entries WHERE id = :id")
    suspend fun find(id: Long): AgendaEntity?
    @Transaction
    suspend fun adjustDailyCount(id: Long, day: String, delta: Int) {
        require(delta == 1 || delta == -1)
        require(java.time.LocalDate.parse(day).toString() == day)
        val entry = requireNotNull(find(id))
        require(entry.isTask && entry.repeatDaily)
        val counts = com.uykutakip.data.decodeDailyCounts(entry.dailyCountsJson).toMutableMap()
        val count = ((counts[day] ?: 0) + delta).coerceAtLeast(0)
        require(count <= 9999)
        if (count == 0) counts.remove(day) else counts[day] = count
        require(counts.size <= 10_000)
        insert(entry.copy(dailyCountsJson = org.json.JSONObject(counts as Map<*, *>).toString(), updatedAtMillis = System.currentTimeMillis()))
    }
    @Query("SELECT * FROM agenda_entries WHERE (isGeneralGoal = 0 AND (day = :day OR repeatDaily = 1)) OR (isGeneralGoal = 1 AND (completed = 0 OR projectPartsJson <> '[]')) ORDER BY isGeneralGoal, id")
    fun observe(day: String): Flow<List<AgendaEntity>>
    @Query("SELECT text, category, subcategory FROM agenda_entries WHERE isTask = 1 AND text <> '' AND NOT EXISTS (SELECT 1 FROM agenda_hidden_task_suggestions hidden WHERE hidden.suggestionKey = trim(text) || char(31) || trim(category) || char(31) || trim(subcategory)) GROUP BY text, category, subcategory ORDER BY MAX(updatedAtMillis) DESC LIMIT 100")
    fun observeTaskSuggestions(): Flow<List<AgendaTaskSuggestion>>
    @Query("SELECT id, text, category, subcategory, favorite, builtIn FROM agenda_task_templates WHERE hidden = 0 ORDER BY favorite DESC, updatedAtMillis DESC")
    fun observeTaskTemplates(): Flow<List<AgendaTaskTemplate>>
    @Query("SELECT id, category, subcategory FROM agenda_categories WHERE hidden = 0 ORDER BY category COLLATE NOCASE, subcategory COLLATE NOCASE")
    fun observeCategories(): Flow<List<AgendaCategoryOption>>
    @Query("SELECT * FROM agenda_entries ORDER BY day DESC, id DESC")
    suspend fun snapshotRecords(): List<AgendaEntity>
    @Query("SELECT * FROM agenda_task_templates ORDER BY id")
    suspend fun snapshotTemplates(): List<AgendaTaskTemplateEntity>
    @Query("SELECT * FROM agenda_categories ORDER BY id")
    suspend fun snapshotCategories(): List<AgendaCategoryEntity>
    @Query("SELECT * FROM agenda_hidden_task_suggestions ORDER BY suggestionKey")
    suspend fun snapshotHiddenSuggestions(): List<AgendaHiddenTaskSuggestionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AgendaEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemplates(entries: List<AgendaTaskTemplateEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(entry: AgendaCategoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(entry: AgendaCategoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(entry: AgendaTaskTemplateEntity)
    @Query("UPDATE agenda_task_templates SET favorite = :favorite, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setTemplateFavorite(id: String, favorite: Boolean, updatedAtMillis: Long)
    @Query("UPDATE agenda_task_templates SET hidden = 1, updatedAtMillis = :updatedAtMillis WHERE id = :id AND builtIn = 1")
    suspend fun hideBuiltInTemplate(id: String, updatedAtMillis: Long): Int
    @Query("UPDATE agenda_task_templates SET hidden = 1, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun hideTemplate(id: String, updatedAtMillis: Long)
    @Query("UPDATE agenda_categories SET hidden = 1, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun hideCategory(id: Long, updatedAtMillis: Long)
    @Query("UPDATE agenda_categories SET hidden = 0, updatedAtMillis = :updatedAtMillis WHERE category = :category AND subcategory = :subcategory")
    suspend fun restoreCategory(category: String, subcategory: String, updatedAtMillis: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideTaskSuggestion(entry: AgendaHiddenTaskSuggestionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHiddenSuggestion(entry: AgendaHiddenTaskSuggestionEntity)
    @Query("UPDATE agenda_entries SET text = :text, category = :category, subcategory = :subcategory, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun edit(id: Long, text: String, category: String, subcategory: String, updatedAtMillis: Long)
    @Query("UPDATE agenda_entries SET projectPartsJson = :partsJson, completed = :completed, notDone = 0, updatedAtMillis = :updatedAtMillis WHERE id = :id AND isTask = 1")
    suspend fun saveParts(id: Long, partsJson: String, completed: Boolean, updatedAtMillis: Long)
    @Query("UPDATE agenda_entries SET completed = :completed, notDone = CASE WHEN :completed = 1 THEN 0 ELSE notDone END, streak = CASE WHEN :completed = 0 THEN streak WHEN lastCompletedDay = date(:day, '-1 day') THEN streak + 1 WHEN lastCompletedDay = :day THEN streak ELSE 1 END, lastCompletedDay = CASE WHEN :completed = 1 THEN :day ELSE lastCompletedDay END, updatedAtMillis = :updatedAtMillis WHERE id = :id AND isTask = 1 AND projectPartsJson = '[]'")
    suspend fun complete(id: Long, completed: Boolean, day: String, updatedAtMillis: Long)
    @Query("UPDATE agenda_entries SET notDone = :notDone, completed = 0, updatedAtMillis = :updatedAtMillis WHERE id = :id AND isTask = 1 AND projectPartsJson = '[]'")
    suspend fun markNotDone(id: Long, notDone: Boolean, updatedAtMillis: Long)
    @Query("UPDATE agenda_entries SET priority = :priority, updatedAtMillis = :updatedAtMillis WHERE id = :id AND isTask = 1 AND isGeneralGoal = 0 AND repeatDaily = 0")
    suspend fun setPriority(id: Long, priority: Int, updatedAtMillis: Long)
    @Query("UPDATE agenda_entries SET isGeneralGoal = 1, repeatDaily = 0, notifyEnabled = 0, completed = CASE WHEN projectPartsJson = '[]' THEN 0 ELSE completed END, notDone = 0, updatedAtMillis = :updatedAtMillis WHERE id = :id AND isTask = 1 AND isGeneralGoal = 0")
    suspend fun promoteToGeneralGoal(id: Long, updatedAtMillis: Long)
    @Query("UPDATE agenda_entries SET isGeneralGoal = 0, day = :day, repeatDaily = 0, updatedAtMillis = :updatedAtMillis WHERE id = :id AND isTask = 1 AND isGeneralGoal = 1")
    suspend fun moveGeneralGoalToDay(id: Long, day: String, updatedAtMillis: Long)
    @Query("UPDATE agenda_entries SET day = :day, completed = CASE WHEN projectPartsJson = '[]' THEN 0 ELSE completed END, notDone = 0, updatedAtMillis = :updatedAtMillis WHERE id = :id AND isTask = 1 AND isGeneralGoal = 0 AND repeatDaily = 0")
    suspend fun postponeToDay(id: Long, day: String, updatedAtMillis: Long)
    @Query("DELETE FROM agenda_entries WHERE id = :id")
    suspend fun delete(id: Long)

    suspend fun removeTemplate(id: String) = hideTemplate(id, System.currentTimeMillis())
}
