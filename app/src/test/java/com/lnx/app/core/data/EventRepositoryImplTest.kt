package com.lnx.app.core.data

import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.domain.BasicOccurrenceExpander
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime

/** 用假 DAO 覆盖仓库契约,不依赖 Room(spec §4.5 的 createdAt 语义值得钉住) */
class EventRepositoryImplTest {

    private class FakeEventDao : EventDao {
        val upserted = mutableListOf<EventEntity>()
        val softDeleted = mutableListOf<Pair<String, Long>>()
        var stored: MutableStateFlow<List<EventEntity>> = MutableStateFlow(emptyList())
        var lastQuery: Pair<Long, Long>? = null

        override fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<EventEntity>> {
            lastQuery = startMillis to endMillis
            return stored
        }

        override suspend fun getById(id: String): EventEntity? = stored.value.firstOrNull { it.id == id }

        override suspend fun upsert(entity: EventEntity) {
            upserted += entity
        }

        override suspend fun softDelete(id: String, updatedAtMillis: Long) {
            softDeleted += id to updatedAtMillis
        }

        override suspend fun allOnce(): List<EventEntity> = stored.value
    }

    private val dao = FakeEventDao()
    private val repo = EventRepositoryImpl(dao, BasicOccurrenceExpander())

    private fun event(
        id: String = "e1",
        createdAt: Long = 0L,
        start: String = "2026-09-30T09:00",
        end: String = "2026-09-30T10:00",
    ) = Event(
        id = id,
        title = "团队周会",
        allDay = false,
        start = LocalDateTime.parse(start),
        end = LocalDateTime.parse(end),
        location = null,
        notes = null,
        colorSlot = 0,
        priority = Priority.P2,
        reminderLeadMinutes = null,
        rule = EventRule(),
        createdAt = createdAt,
        updatedAt = 0L,
    )

    @Test
    fun `新事件写入当前时间作为createdAt`() = runTest {
        repo.save(event())
        val saved = dao.upserted.single()
        assertNotEquals(0L, saved.createdAt)
    }

    @Test
    fun `已有事件的createdAt不被覆盖`() = runTest {
        repo.save(event(createdAt = 111L))
        assertEquals(111L, dao.upserted.single().createdAt)
    }

    @Test
    fun `保存总是刷新updatedAt`() = runTest {
        repo.save(event())
        assertNotEquals(0L, dao.upserted.single().updatedAt)
    }

    @Test
    fun `删除走软删除并盖时间戳`() = runTest {
        repo.delete("e1")
        val (id, at) = dao.softDeleted.single()
        assertEquals("e1", id)
        assertNotEquals(0L, at)
    }

    @Test
    fun `查询区间按半开语义传给DAO`() = runTest {
        val start = LocalDateTime.parse("2026-09-28T00:00")
        val end = LocalDateTime.parse("2026-10-05T00:00")
        repo.observeEvents(start, end).first()
        val (qStart, qEnd) = dao.lastQuery!!
        assertEquals(start.toEpochMillis(), qStart)
        assertEquals(end.toEpochMillis(), qEnd)
    }
}
