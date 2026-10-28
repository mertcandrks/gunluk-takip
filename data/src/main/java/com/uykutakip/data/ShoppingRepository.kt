package com.uykutakip.data

import com.uykutakip.core.model.ShoppingItem
import com.uykutakip.data.local.ShoppingDao
import com.uykutakip.data.local.ShoppingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ShoppingRepository {
    val items: Flow<List<ShoppingItem>>
    suspend fun save(id: Long, text: String, longTerm: Boolean)
    suspend fun toggle(id: Long)
    suspend fun delete(id: Long)
}

internal class OfflineShoppingRepository(private val dao: ShoppingDao) : ShoppingRepository {
    override val items = dao.observe().map { rows -> rows.map { ShoppingItem(it.id, it.text, it.longTerm, it.purchased, it.createdAtMillis, it.updatedAtMillis) } }
    override suspend fun save(id: Long, text: String, longTerm: Boolean) {
        require(id >= 0 && text.isNotBlank() && text.length <= 2000)
        val now = System.currentTimeMillis()
        if (id == 0L) dao.insert(ShoppingEntity(text = text.trim(), longTerm = longTerm, createdAtMillis = now, updatedAtMillis = now))
        else check(dao.update(id, text.trim(), longTerm, now) == 1) { "Shopping item no longer exists" }
    }
    override suspend fun toggle(id: Long) { require(id > 0); check(dao.toggle(id, System.currentTimeMillis()) == 1) }
    override suspend fun delete(id: Long) { require(id > 0); dao.delete(id) }
}
