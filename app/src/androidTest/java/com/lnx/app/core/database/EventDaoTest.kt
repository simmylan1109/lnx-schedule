package com.lnx.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.database.entity.EventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventDaoTest {
    private lateinit var db: LnxDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LnxDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun entity(
        id: String,
        start: Long,
        end: Long,
        allDay: Boolean = false,
    ) = EventEntity(
        id = id,
        title = "T$id",
        allDay = allDay,
        startAt = start,
        endAt = end,
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
        createdAt = 0L,
        updatedAt = 0L,
        isDeleted = false,
    )

    @Test
    fun `区间查询命中半开重叠`() = runBlocking {
        db.eventDao().upsert(entity("a", 100L, 200L))
        db.eventDao().upsert(entity("b", 300L, 400L))
        val hit = db.eventDao().observeBetween(150L, 250L).first()
        assertEquals(listOf("a"), hit.map { it.id })
    }

    @Test
    fun `贴边不算重叠`() = runBlocking {
        db.eventDao().upsert(entity("a", 100L, 200L))
        // 末端贴查询起点:endAt > startMillis 这一侧
        val hit = db.eventDao().observeBetween(200L, 300L).first()
        assertTrue(hit.isEmpty())
    }

    @Test
    fun `起点贴边不算重叠`() = runBlocking {
        db.eventDao().upsert(entity("a", 100L, 200L))
        // 末端贴查询终点:startAt < endMillis 这一侧(把 < 写成 <= 会命中并让本用例失败)
        val hit = db.eventDao().observeBetween(0L, 100L).first()
        assertTrue(hit.isEmpty())
    }

    @Test
    fun `全天事件按次日零点排他`() = runBlocking {
        // 全天 9-28 到 9-29(含):endAt = 9-30 00:00,故查询 9-29 当天应命中
        val day = 86_400_000L
        db.eventDao().upsert(entity("all", 100L * day, 102L * day, allDay = true))
        assertEquals(1, db.eventDao().observeBetween(101L * day, 102L * day).first().size)
        // 结束日次日不再命中
        assertTrue(db.eventDao().observeBetween(102L * day, 103L * day).first().isEmpty())
    }

    @Test
    fun `并列开始时间排序稳定`() = runBlocking {
        db.eventDao().upsert(entity("b", 100L, 300L))
        db.eventDao().upsert(entity("a", 100L, 200L))
        val hit = db.eventDao().observeBetween(0L, 999L).first()
        assertEquals(listOf("a", "b"), hit.map { it.id }) // 同起点按 endAt 再按 id 全序
    }

    @Test
    fun `软删除后不再出现`() = runBlocking {
        db.eventDao().upsert(entity("a", 100L, 200L))
        db.eventDao().softDelete("a", updatedAtMillis = 9L)
        assertEquals(0, db.eventDao().observeBetween(0L, 999L).first().size)
    }

    @Test
    fun `upsert同id覆盖而非新增`() = runBlocking {
        db.eventDao().upsert(entity("a", 100L, 200L))
        db.eventDao().upsert(entity("a", 100L, 200L).copy(title = "改名"))
        val hit = db.eventDao().observeBetween(0L, 999L).first()
        assertEquals(1, hit.size)
        assertEquals("改名", hit.first().title)
    }
}
