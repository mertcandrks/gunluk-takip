package com.uykutakip.data.local

import androidx.room3.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ideas")
internal data class IdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    @ColumnInfo(defaultValue = "NULL") val completedAtMillis: Long? = null,
)

@Dao
internal interface IdeaDao {
    @Query("SELECT * FROM ideas ORDER BY updatedAtMillis DESC, id DESC")
    suspend fun snapshotRecords(): List<IdeaEntity>
    @Query("SELECT * FROM ideas ORDER BY updatedAtMillis DESC, id DESC")
    fun observe(): Flow<List<IdeaEntity>>
    @Insert
    suspend fun insert(idea: IdeaEntity)
    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun find(id: Long): IdeaEntity?
    @Insert
    suspend fun insertCompletedAgenda(entry: AgendaEntity)
    @Query("UPDATE ideas SET completedAtMillis = :completedAtMillis WHERE id = :id AND completedAtMillis IS NULL")
    suspend fun setCompleted(id: Long, completedAtMillis: Long): Int
    @Transaction
    suspend fun finish(id: Long, completedAtMillis: Long, day: String) {
        val idea = requireNotNull(find(id)) { "Idea no longer exists" }
        if (idea.completedAtMillis != null) return
        insertCompletedAgenda(
            AgendaEntity(
                day = day,
                text = idea.text.lineSequence().first().trim().take(4000),
                isTask = true,
                completed = true,
                repeatDaily = false,
                streak = 1,
                lastCompletedDay = day,
                notifyEnabled = false,
                notifyHour = 20,
                notifyMinute = 0,
                notDone = false,
                isGeneralGoal = false,
            ),
        )
        check(setCompleted(id, completedAtMillis) == 1)
    }
    @Query("UPDATE ideas SET text = :text, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun update(id: Long, text: String, updatedAtMillis: Long): Int
    @Query("DELETE FROM ideas WHERE id = :id")
    suspend fun delete(id: Long)
}
