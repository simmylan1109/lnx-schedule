package com.lnx.app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.database.entity.EventExceptionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 例外表(spec §4.3):确定性 id 幂等覆盖;按母事件清理;按日期剪断 */
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
    fun `按母事件清理`() = runBlocking {
        val dao = db.eventExceptionDao()
        dao.upsert(row("m1", 10))
        dao.upsert(row("m1", 17))
        dao.upsert(row("m2", 10))
        dao.deleteForMaster("m1")
        assertEquals(1, dao.observeAll().first().size)
        assertEquals("m2", dao.observeAll().first().single().masterEventId)
    }

    @Test
    fun `按日期剪断_只删该日及以后`() = runBlocking {
        val dao = db.eventExceptionDao()
        dao.upsert(row("m1", 10))
        dao.upsert(row("m1", 14))
        dao.upsert(row("m1", 21))
        dao.deleteFrom("m1", fromEpochDay = 14)
        assertNull(dao.getFor("m1", 14))
        assertNull(dao.getFor("m1", 21))
        assertNotNull(dao.getFor("m1", 10))
    }
}
