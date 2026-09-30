package com.lnx.app.core.data

import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.dao.EventExceptionDao
import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.EventExceptionEntity
import com.lnx.app.core.domain.DefaultOccurrenceExpander
import com.lnx.app.core.domain.OccurrenceExpander
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.Occurrence
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 用假 DAO 覆盖仓库契约,不依赖 Room(spec §4.5 的 createdAt 语义值得钉住) */
class EventRepositoryImplTest {

    private class FakeEventDao : EventDao {
        val upserted = mutableListOf<EventEntity>()
        val softDeleted = mutableListOf<Pair<String, Long>>()
        var stored: MutableStateFlow<List<EventEntity>> = MutableStateFlow(emptyList())
        var lastQuery: Pair<Long, Long>? = null
        var lastExpansionQuery: Pair<Long, Long>? = null

        override fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<EventEntity>> {
            lastQuery = startMillis to endMillis
            return stored
        }

        override fun observeForExpansion(startMillis: Long, endMillis: Long): Flow<List<EventEntity>> {
            lastExpansionQuery = startMillis to endMillis
            return stored
        }

        override suspend fun getById(id: String): EventEntity? = stored.value.firstOrNull { it.id == id }

        // 搜索(spec §3.9)归 SearchRepositoryImpl 用,这里只求能编译
        override fun observeMatching(pattern: String): Flow<List<EventEntity>> = stored

        override suspend fun upsert(entity: EventEntity) {
            upserted += entity
        }

        override suspend fun softDelete(id: String, updatedAtMillis: Long) {
            softDeleted += id to updatedAtMillis
        }

        override suspend fun allOnce(): List<EventEntity> = stored.value
    }

    private class FakeEventExceptionDao : EventExceptionDao {
        val upserted = mutableListOf<EventExceptionEntity>()
        val deletedForMaster = mutableListOf<String>()
        val deletedFrom = mutableListOf<Pair<String, Long>>()
        var stored: MutableStateFlow<List<EventExceptionEntity>> = MutableStateFlow(emptyList())

        override fun observeAll(): Flow<List<EventExceptionEntity>> = stored

        override suspend fun getFor(masterId: String, epochDay: Long): EventExceptionEntity? =
            stored.value.firstOrNull { it.masterEventId == masterId && it.originalDate == epochDay }

        override suspend fun upsert(entity: EventExceptionEntity) {
            upserted += entity
        }

        override suspend fun deleteForMaster(masterId: String) {
            deletedForMaster += masterId
        }

        override suspend fun deleteFrom(masterId: String, fromEpochDay: Long) {
            deletedFrom += masterId to fromEpochDay
        }
    }

    /** 录参数的假展开器:断言仓库真的把事件和例外都递给了展开器 */
    private class RecordingExpander : OccurrenceExpander {
        var lastEvents: List<Event> = emptyList()
        var lastExceptions: List<EventException> = emptyList()

        override fun expand(
            events: List<Event>,
            exceptions: List<EventException>,
            rangeStart: LocalDateTime,
            rangeEnd: LocalDateTime,
        ): List<Occurrence> {
            lastEvents = events
            lastExceptions = exceptions
            return emptyList()
        }
    }

    private val dao = FakeEventDao()
    private val exceptionDao = FakeEventExceptionDao()
    private val expander = RecordingExpander()
    private val repo = EventRepositoryImpl(dao, exceptionDao, expander)

    private fun entity(id: String = "e1", createdAt: Long = 0L) = EventEntity(
        id = id,
        title = "T$id",
        allDay = false,
        startAt = 0L,
        endAt = 3_600_000L,
        location = null,
        notes = null,
        colorSlot = 0,
        priority = "P2",
        reminderLeadMinutes = null,
        ruleType = "NONE",
        ruleInterval = 1,
        ruleWeekdays = null,
        ruleMonthlyMode = null,
        ruleMonthlyDay = null,
        ruleMonthlyNth = null,
        ruleMonthlyWeekday = null,
        ruleEndType = null,
        ruleEndDate = null,
        ruleCount = null,
        createdAt = createdAt,
        updatedAt = 0L,
        isDeleted = false,
    )

    @Test
    fun `新事件写入当前时间作为createdAt`() = runTest {
        repo.save(EventDefaults.event(id = "e1")) // createdAt = 0 → 仓库补当前时间
        assertNotEquals(0L, dao.upserted.single().createdAt)
    }

    @Test
    fun `已有事件的createdAt不被覆盖`() = runTest {
        repo.save(EventDefaults.event(id = "e1", createdAt = 111L))
        assertEquals(111L, dao.upserted.single().createdAt)
    }

    @Test
    fun `保存总是刷新updatedAt`() = runTest {
        repo.save(EventDefaults.event(id = "e1", createdAt = 111L))
        assertNotEquals(0L, dao.upserted.single().updatedAt)
    }

    @Test
    fun `删除走软删除并盖时间戳`() = runTest {
        repo.delete("e1")
        assertEquals("e1", dao.softDeleted.single().first)
        assertNotEquals(0L, dao.softDeleted.single().second)
    }

    @Test
    fun `查询区间换算成毫秒传给DAO`() = runTest {
        val start = LocalDateTime.parse("2026-09-28T00:00")
        val end = LocalDateTime.parse("2026-10-05T00:00")
        repo.observeEvents(start, end).first()
        val (qStart, qEnd) = dao.lastQuery!!
        assertEquals(start.toEpochMillis(), qStart)
        assertEquals(end.toEpochMillis(), qEnd)
    }

    @Test
    fun `发生查询走展开专用SQL并把事件与例外一起递给展开器`() = runTest {
        val start = LocalDateTime.parse("2026-10-01T00:00")
        val end = LocalDateTime.parse("2026-10-08T00:00")
        repo.observeOccurrences(start, end).first()

        // 展开必须用 observeForExpansion(重复母事件起点在窗口前也不能漏),不是 observeBetween
        assertEquals(start.toEpochMillis(), dao.lastExpansionQuery!!.first)
        assertEquals(end.toEpochMillis(), dao.lastExpansionQuery!!.second)
        assertEquals(dao.lastQuery, null) // 原始事件查询不受影响

        exceptionDao.stored.value = listOf(
            EventExceptionEntity(
                id = "m1:100",
                masterEventId = "m1",
                originalDate = 100L,
                isCancelled = true,
                overrideTitle = null,
                overrideAllDay = null,
                overrideStartAt = null,
                overrideEndAt = null,
                overrideLocation = null,
                overrideNotes = null,
                overrideColorSlot = null,
                overridePriority = null,
                overrideReminderLeadMinutes = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        repo.observeOccurrences(start, end).first()
        assertEquals(1, expander.lastExceptions.size)
        assertEquals(LocalDate.ofEpochDay(100L), expander.lastExceptions[0].originalDate)
    }

    @Test
    fun `取消某一次写入取消例外`() = runTest {
        repo.cancelOccurrence("m1", LocalDate.parse("2026-10-14"))
        val row = exceptionDao.upserted.single()
        assertEquals(true, row.isCancelled)
        assertEquals("m1", row.masterEventId)
        // 确定性 id:同一天再取消 = 覆盖同一行,不会留两条例外
        assertEquals("m1:${LocalDate.parse("2026-10-14").toEpochDay()}", row.id)
    }

    @Test
    fun `写override例外带完整字段组`() = runTest {
        val override = EventDefaults.event(id = "m1", title = "改过的")
        repo.upsertException(EventException(masterId = "m1", originalDate = LocalDate.parse("2026-10-14"), override = override))
        val row = exceptionDao.upserted.single()
        assertEquals(false, row.isCancelled)
        assertEquals("改过的", row.overrideTitle)
        assertEquals(override.start.toEpochMillis(), row.overrideStartAt)
    }

    @Test
    fun `清理例外走对应DAO`() = runTest {
        repo.deleteExceptionsFor("m1")
        repo.deleteExceptionsFrom("m1", LocalDate.parse("2026-10-14"))
        assertEquals("m1", exceptionDao.deletedForMaster.single())
        assertEquals("m1" to LocalDate.parse("2026-10-14").toEpochDay(), exceptionDao.deletedFrom.single())
    }
}

/** 测试局部的事件构造,避免在各用例里铺 20 个字段 */
private object EventDefaults {
    fun event(id: String, title: String = "T$id", createdAt: Long = 0L): Event =
        Event(
            id = id,
            title = title,
            allDay = false,
            start = LocalDateTime.parse("2026-10-01T09:00"),
            end = LocalDateTime.parse("2026-10-01T10:00"),
            location = null,
            notes = null,
            colorSlot = 0,
            priority = com.lnx.app.core.domain.model.Priority.P2,
            reminderLeadMinutes = null,
            rule = com.lnx.app.core.domain.model.EventRule(),
            createdAt = createdAt,
            updatedAt = 0L,
        )
}
