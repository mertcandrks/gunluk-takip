package com.uykutakip.data

import com.uykutakip.data.local.ShoppingDao
import com.uykutakip.data.local.ShoppingEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShoppingRepositoryTest {
    @Test fun separatesHorizonsAndPreservesPurchasedItemsWhenEditing() = runBlocking {
        val dao = MemoryShoppingDao()
        val repo = OfflineShoppingRepository(dao)
        repo.save(0, "  Soap  ", false)
        repo.save(0, "Computer", true)
        val soap = repo.items.first().first { !it.longTerm }
        assertEquals("Soap", soap.text)
        assertEquals("Computer", repo.items.first().single { it.longTerm }.text)
        repo.toggle(soap.id)
        repo.save(soap.id, "Soap refill", true)
        val edited = OfflineShoppingRepository(dao).items.first().first { it.id == soap.id }
        assertTrue(edited.purchased)
        assertTrue(edited.longTerm)
        assertEquals(soap.createdAtMillis, edited.createdAtMillis)
        repo.toggle(soap.id)
        assertFalse(repo.items.first().first { it.id == soap.id }.purchased)
        repo.delete(soap.id)
        assertEquals(1, repo.items.first().size)
    }

    @Test fun rejectsInvalidAndMissingItems() = runBlocking {
        val repo = OfflineShoppingRepository(MemoryShoppingDao())
        for (text in listOf("  ", "x".repeat(2001))) {
            try { repo.save(0, text, false); fail("Invalid item accepted") } catch (_: IllegalArgumentException) { }
        }
        try { repo.save(99, "Missing", true); fail("Missing item recreated") } catch (_: IllegalStateException) { }
        try { repo.toggle(99); fail("Missing item toggled") } catch (_: IllegalStateException) { }
        assertTrue(repo.items.first().isEmpty())
    }
}

private class MemoryShoppingDao : ShoppingDao {
    private val rows = MutableStateFlow<List<ShoppingEntity>>(emptyList())
    private var nextId = 1L
    override fun observe() = rows
    override suspend fun snapshotRecords() = rows.value
    override suspend fun find(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun insert(item: ShoppingEntity) { rows.value += item.copy(id = nextId++) }
    override suspend fun update(id: Long, text: String, longTerm: Boolean, updatedAtMillis: Long): Int {
        if (rows.value.none { it.id == id }) return 0
        rows.value = rows.value.map { if (it.id == id) it.copy(text = text, longTerm = longTerm, updatedAtMillis = updatedAtMillis) else it }
        return 1
    }
    override suspend fun toggle(id: Long, updatedAtMillis: Long): Int {
        if (rows.value.none { it.id == id }) return 0
        rows.value = rows.value.map { if (it.id == id) it.copy(purchased = !it.purchased, updatedAtMillis = updatedAtMillis) else it }
        return 1
    }
    override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
}
