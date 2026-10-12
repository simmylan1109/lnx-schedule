package com.lnx.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.database.entity.EventExceptionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 例外表(spec §4.3):确定性 id 幂等覆盖;按母事件/按日期**打墓碑**(v0.3 例外墓碑补做) */
@RunWith(AndroidJUnit4::class)
class EventExceptionDaoTest {
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

    private fun row(masterId: String, epochDay: Long, cancelled: Boolean = false) = EventExceptionEntity(
        id = "$masterId:$epochDay",
        masterEventId = masterId,
        originalDate = epochDay,
        isCancelled = cancelled,
        overrideTitle = if (cancelled) null else "改过的",
        overrideAllDay = if (cancelled) null else false,
        overrideStartAt = if (cancelled) null else 0L,
        overrideEndAt = if (cancelled) null else 3_600_000L,
        overrideLocation = null,
        overrideNotes = null,
        overrideColorSlot = null,
        overridePriority = null,
        overrideReminderLeadMinutes = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `同一母事件同一日重复写只留一行`() = runBlocking {
        val dao = db.eventExceptionDao()
        dao.upsert(row("m1", 10))
        dao.upsert(row("m1", 10, cancelled = true)) // 同 id 再写 = 覆盖(如先改后取消)
        assertEquals(1, dao.observeAll().first().size)
        assertEquals(true, dao.getFor("m1", 10)!!.isCancelled)
    }

    @Test
    fun `按母事件清理是打墓碑_行还在只是不再显示`() = runBlocking {
        val dao = db.eventExceptionDao()
        dao.upsert(row("m1", 10))
        dao.upsert(row("m1", 17))
        dao.upsert(row("m2", 10))
        dao.tombstoneForMaster("m1", now = 999L)

        // 显示侧(展开/搜索)只看活着的:只剩 m2 那条
        assertEquals(1, dao.observeAll().first().size)
        assertEquals("m2", dao.observeAll().first().single().masterEventId)
        // 行没有消失:全量里能看到墓碑,且时间戳已推进(同步靠它把删除传出去)
        val all = dao.allOnce()
        assertEquals(3, all.size)
        val tombstoned = all.filter { it.isDeleted }
        assertEquals(2, tombstoned.size)
        assertEquals(setOf("m1:10", "m1:17"), tombstoned.map { it.id }.toSet())
        assertEquals(setOf(999L), tombstoned.map { it.updatedAt }.toSet())
    }

    @Test
    fun `按日期剪断_只给该日及以后的打墓碑`() = runBlocking {
        val dao = db.eventExceptionDao()
        dao.upsert(row("m1", 10))
        dao.upsert(row("m1", 14))
        dao.upsert(row("m1", 21))
        dao.tombstoneFrom("m1", fromEpochDay = 14, now = 500L)

        // 显示侧:剪断日前的还在,剪断日起的消失
        assertEquals(listOf(10L), dao.observeAll().first().map { it.originalDate })
        // 行仍在库中,以墓碑形态存在 —— getFor(同步的 LWW 比较用)仍能找到
        assertEquals(true, dao.getFor("m1", 14)!!.isDeleted)
        assertEquals(true, dao.getFor("m1", 21)!!.isDeleted)
        assertEquals(false, dao.getFor("m1", 10)!!.isDeleted)
        assertEquals(500L, dao.getFor("m1", 14)!!.updatedAt)
    }

    @Test
    fun `墓碑行可以被更新的写覆盖回来_与事件同一套LWW`() = runBlocking {
        val dao = db.eventExceptionDao()
        dao.upsert(row("m1", 10))
        dao.tombstoneForMaster("m1", now = 999L)
        dao.upsert(row("m1", 10).copy(updatedAt = 1_000L, isDeleted = false))
        assertEquals(1, dao.observeAll().first().size)
        assertEquals(false, dao.getFor("m1", 10)!!.isDeleted)
    }
}
