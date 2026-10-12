package com.lnx.app.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.database.LnxDatabase
import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.EventExceptionEntity
import com.lnx.app.core.network.SyncRowDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 例外墓碑在 Room 同步存储里的行为(v0.3 例外墓碑补做)。
 *
 * 与事件同一套:删除 = 软删除 + 墓碑行,删除要能推出去、拉回来整行应用,
 * 本地更新靠 updatedAt 挡住旧墓碑。跨设备的完整故事线在 [ExceptionCutJourneyTest]。
 */
@RunWith(AndroidJUnit4::class)
class RoomSyncStoreTest {

    private lateinit var db: LnxDatabase
    private lateinit var store: RoomSyncStore

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LnxDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = RoomSyncStore(db.eventDao(), db.tagDao(), db.eventExceptionDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun eventRow(id: String = "m1", updatedAt: Long = 100L) = EventEntity(
        id = id,
        title = "周会",
        allDay = false,
        startAt = 1_760_000_000_000,
        endAt = 1_760_000_360_000,
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
        createdAt = 1L,
        updatedAt = updatedAt,
        isDeleted = false,
    )

    private fun exceptionRow(
        masterId: String = "m1",
        epochDay: Long = 10L,
        updatedAt: Long = 100L,
    ) = EventExceptionEntity(
        id = "$masterId:$epochDay",
        masterEventId = masterId,
        originalDate = epochDay,
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
        createdAt = 1L,
        updatedAt = updatedAt,
    )

    @Test
    fun `例外墓碑进推送流_行带deleted且载荷带isDeleted`() = runBlocking {
        db.eventExceptionDao().upsert(exceptionRow())
        db.eventExceptionDao().tombstoneFrom("m1", fromEpochDay = 0, now = 500L)

        val row = store.currentRows().single { it.entity == SyncEntity.EXCEPTION }
        assertTrue(row.deleted)
        assertEquals(500L, row.updatedAt)
        assertTrue(SyncPayloads.decodeException(row.payload).isDeleted)
    }

    @Test
    fun `拉到例外墓碑_本地变墓碑而不是硬删_重复应用幂等`() = runBlocking {
        db.eventExceptionDao().upsert(exceptionRow(updatedAt = 100L))

        val tombstone = SyncRowDto(
            entity = SyncEntity.EXCEPTION,
            id = "m1:10",
            payload = SyncPayloads.exception(exceptionRow(updatedAt = 200L).copy(isDeleted = true)),
            updatedAt = 200L,
            deleted = true,
        )
        store.applyPulled(listOf(tombstone))
        store.applyPulled(listOf(tombstone)) // 幂等:第二次应用结果一样

        // 行还在(墓碑是行),只是不再参与显示
        val row = db.eventExceptionDao().allOnce().single()
        assertTrue(row.isDeleted)
        assertEquals(200L, row.updatedAt)
        assertEquals(0, db.eventExceptionDao().observeAll().first().size)
    }

    @Test
    fun `本地更新的例外不被旧墓碑盖掉`() = runBlocking {
        db.eventExceptionDao().upsert(exceptionRow(updatedAt = 300L))

        val staleTombstone = SyncRowDto(
            entity = SyncEntity.EXCEPTION,
            id = "m1:10",
            payload = SyncPayloads.exception(exceptionRow(updatedAt = 200L).copy(isDeleted = true)),
            updatedAt = 200L,
            deleted = true,
        )
        val result = store.applyPulled(listOf(staleTombstone))

        // 本地更新 = 跳过(不进 applied 也不进 skipped —— skipped 只记解码/落库异常)
        assertEquals(0, result.applied.size)
        assertEquals(0, result.skipped.size)
        assertFalse(db.eventExceptionDao().allOnce().single().isDeleted)
    }

    @Test
    fun `镜像孤儿墓碑_行标deleted但载荷还是旧的活着版本_本地仍按删除应用`() = runBlocking {
        // SyncEngine 的孤儿识别路径推墓碑时复用镜像里的旧载荷(无 isDeleted 字段)——
        // 接收端必须以行上的 deleted 标志为准,否则删除被读成"复活"(审查抓到的回归)
        db.eventExceptionDao().upsert(exceptionRow(updatedAt = 100L))

        val orphanTombstone = SyncRowDto(
            entity = SyncEntity.EXCEPTION,
            id = "m1:10",
            payload = SyncPayloads.exception(exceptionRow(updatedAt = 200L)), // 旧载荷:没有 isDeleted
            updatedAt = 200L,
            deleted = true,
        )
        store.applyPulled(listOf(orphanTombstone))

        assertTrue(db.eventExceptionDao().allOnce().single().isDeleted)
        assertEquals(0, db.eventExceptionDao().observeAll().first().size)
    }

    @Test
    fun `事件墓碑级联把例外打成墓碑而不是硬删`() = runBlocking {
        db.eventDao().upsert(eventRow())
        db.eventExceptionDao().upsert(exceptionRow())

        val masterTombstone = SyncRowDto(
            entity = SyncEntity.EVENT,
            id = "m1",
            payload = SyncPayloads.event(eventRow(updatedAt = 400L).copy(isDeleted = true), emptyList()),
            updatedAt = 400L,
            deleted = true,
        )
        store.applyPulled(listOf(masterTombstone))

        val exception = db.eventExceptionDao().allOnce().single()
        assertTrue("母事件没了,它的例外要打墓碑(删除也要同步出去)", exception.isDeleted)
        assertEquals(400L, exception.updatedAt)
        assertEquals(0, db.eventExceptionDao().observeAll().first().size)
    }
}
