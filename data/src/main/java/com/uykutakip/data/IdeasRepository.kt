package com.uykutakip.data

import com.uykutakip.core.model.Idea
import com.uykutakip.data.local.IdeaDao
import com.uykutakip.data.local.IdeaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

interface IdeasRepository {
    val ideas: Flow<List<Idea>>
    suspend fun save(id: Long, text: String)
    suspend fun delete(id: Long)
    suspend fun finish(id: Long)
}

internal class OfflineIdeasRepository(private val dao: IdeaDao) : IdeasRepository {
    override val ideas = dao.observe().map { rows -> rows.map { Idea(it.id, it.text, it.createdAtMillis, it.updatedAtMillis, it.completedAtMillis) } }
    override suspend fun save(id: Long, text: String) {
        require(id >= 0 && text.isNotBlank() && text.length <= 20_000)
        val now = System.currentTimeMillis()
        if (id == 0L) dao.insert(IdeaEntity(text = text.trim(), createdAtMillis = now, updatedAtMillis = now))
        else check(dao.update(id, text.trim(), now) == 1) { "Idea no longer exists" }
    }
    override suspend fun delete(id: Long) { require(id > 0); dao.delete(id) }
    override suspend fun finish(id: Long) {
        require(id > 0)
        val now = System.currentTimeMillis()
        val day = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        dao.finish(id, now, day)
    }
}
