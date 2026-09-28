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
        val hit = db.eventDao().observeBetween(200L, 300L).first()
        assertTrue(hit.isEmpty())
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
