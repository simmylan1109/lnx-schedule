package com.lnx.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 标签数据层(spec §3.10):标签多对多关联事件;删标签只解除关联,不动事件。
 */
@RunWith(AndroidJUnit4::class)
class TagDaoTest {
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

    private fun tag(id: String, name: String, colorSlot: Int = 0, createdAt: Long = 0L) = TagEntity(
        id = id,
        name = name,
        colorSlot = colorSlot,
        createdAt = createdAt,
        updatedAt = 0L,
        isDeleted = false,
    )

    private fun event(id: String) = EventEntity(
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
        createdAt = 0L,
        updatedAt = 0L,
        isDeleted = false,
    )

    @Test
    fun `建标签可观察且按创建时间排序`() = runBlocking {
        db.tagDao().upsert(tag("t2", "工作", colorSlot = 3, createdAt = 1L))
        db.tagDao().upsert(tag("t1", "生活", colorSlot = 4, createdAt = 2L))
        val tags = db.tagDao().observeAll().first()
        assertEquals(listOf("t2", "t1"), tags.map { it.id }) // 先建的排前面
    }

    @Test
    fun `关联事件后能查出事件标签且重设为清后插`() = runBlocking {
        db.eventDao().upsert(event("e1"))
        val dao = db.tagDao()
        dao.upsert(tag("a", "工作"))
        dao.upsert(tag("b", "生活"))
        dao.setEventTags("e1", listOf("a", "b"))
        assertEquals(listOf("a", "b"), dao.tagsOfEvent("e1").map { it.id })
        // 重设 = 清后插:只剩 b
        dao.setEventTags("e1", listOf("b"))
        assertEquals(listOf("b"), dao.tagsOfEvent("e1").map { it.id })
        assertEquals(2, dao.observeAll().first().size) // 标签本身不受影响
    }

    @Test
    fun `删除标签后观察不到但事件仍在`() = runBlocking {
        db.eventDao().upsert(event("e1"))
        val dao = db.tagDao()
        dao.upsert(tag("a", "工作"))
        dao.setEventTags("e1", listOf("a"))
        dao.softDelete("a", updatedAtMillis = 1L)
        assertTrue(dao.observeAll().first().isEmpty())
        assertEquals("Te1", db.eventDao().getById("e1")!!.title) // 事件不受标签删除影响
        assertTrue(dao.tagsOfEvent("e1").isEmpty()) // 已删标签不再属于任何事件
    }

    @Test
    fun `事件与标签关联对可观察`() = runBlocking {
        db.eventDao().upsert(event("e1"))
        db.eventDao().upsert(event("e2"))
        val dao = db.tagDao()
        dao.upsert(tag("a", "工作"))
        dao.setEventTags("e1", listOf("a"))
        val pairs = dao.observeEventTagPairs().first()
        assertEquals(1, pairs.size)
        assertEquals("e1", pairs.first().eventId)
        assertEquals("a", pairs.first().tagId)
    }
}
