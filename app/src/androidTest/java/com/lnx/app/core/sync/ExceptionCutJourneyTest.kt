package com.lnx.app.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.database.LnxDatabase
import com.lnx.app.core.domain.DefaultOccurrenceExpander
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.recurrence.EditScope
import com.lnx.app.core.domain.recurrence.RecurrenceEditHandler
import com.lnx.app.core.data.EventRepositoryImpl
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 跨设备完整旅程(v0.3 例外墓碑补做,A4 已知限制的验收测试):
 * A 手机对重复事件做"本次及以后"剪断,同步之后 **B 手机的例外表收敛到同一状态**。
 *
 * 修这条限制之前的断链:例外行本地是硬删除,删除永远不进同步流 ——
 * A 清掉的例外 B 毫不知情,会一直显示过期的"仅本次"改动。
 * 这里用两个真库(Room 内存库)各演一台设备,经真实的 [RoomSyncStore] 推拉。
 */
@RunWith(AndroidJUnit4::class)
class ExceptionCutJourneyTest {

    private lateinit var dbA: LnxDatabase
    private lateinit var dbB: LnxDatabase

    /** 一台"手机":真仓库 + 真同步存储,底层各自一个库 */
    private inner class Phone(db: LnxDatabase) {
        val repository = EventRepositoryImpl(
            db.eventDao(),
            db.eventExceptionDao(),
            DefaultOccurrenceExpander(),
        )
        val store = RoomSyncStore(db.eventDao(), db.tagDao(), db.eventExceptionDao())
    }

    private lateinit var phoneA: Phone
    private lateinit var phoneB: Phone

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbA = Room.inMemoryDatabaseBuilder(app, LnxDatabase::class.java).allowMainThreadQueries().build()
        dbB = Room.inMemoryDatabaseBuilder(app, LnxDatabase::class.java).allowMainThreadQueries().build()
        phoneA = Phone(dbA)
        phoneB = Phone(dbB)
    }

    @After
    fun teardown() {
        dbA.close()
        dbB.close()
    }

    /** 2026-09-28 起每周一 09:00,永不结束 */
    private val master = Event(
        id = "m1",
        title = "周会",
        allDay = false,
        start = LocalDateTime.parse("2026-09-28T09:00"),
        end = LocalDateTime.parse("2026-09-28T10:00"),
        location = null,
        notes = null,
        colorSlot = 2,
        priority = Priority.P2,
        reminderLeadMinutes = null,
        rule = EventRule(RuleType.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY)),
        createdAt = 111L,
        updatedAt = 0L,
    )

    private val week3 = LocalDate.parse("2026-10-12")
    private val week5 = LocalDate.parse("2026-10-26")
    private val week6 = LocalDate.parse("2026-11-02")
    private val windowStart = LocalDateTime.parse("2026-09-28T00:00")
    private val windowEnd = LocalDateTime.parse("2026-11-09T00:00")

    private suspend fun sync(from: Phone, to: Phone) {
        to.store.applyPulled(from.store.currentRows())
    }

    private suspend fun Phone.visibleCount(): Int =
        repository.observeOccurrences(windowStart, windowEnd).first().size

    @Test
    fun `本次及以后剪断_例外的清理靠同步传播到另一台设备`() = runBlocking {
        // —— A 上建系列 + 两处"仅本次"取消(第3周、第6周)——
        phoneA.repository.save(master)
        phoneA.repository.cancelOccurrence("m1", week3)
        phoneA.repository.cancelOccurrence("m1", week6)
        sync(phoneA, phoneB)

        // 第一次同步后:B 有两条活跃例外;窗口内 6 次发生,第 3、6 周被取消 → 可见 4 次
        assertEquals(2, dbB.eventExceptionDao().allOnce().count { !it.isDeleted })
        assertEquals(4, phoneB.visibleCount())

        // —— A 在第 5 周剪断("本次及以后"删除):旧系列截止到第 4 周,第 6 周的例外被清掉 ——
        val handler = RecurrenceEditHandler(phoneA.repository)
        val masterRow = phoneA.repository.getEvent("m1")!!
        handler.apply(masterRow, week5, null, EditScope.THIS_AND_FUTURE)

        // 推送侧:被清的例外以墓碑形态出现在行流里(硬删除时代这里什么都没有)
        val pushed = phoneA.store.currentRows()
            .single { it.entity == SyncEntity.EXCEPTION && it.id == "m1:${week6.toEpochDay()}" }
        assertTrue("剪断清掉的例外必须以墓碑推出去", pushed.deleted)

        // —— 同步,B 收敛 ——
        sync(phoneA, phoneB)

        // 例外表:B 的第 6 周例外变成墓碑行(还在库里,删除是个事实),第 3 周例外不受影响
        val w6 = dbB.eventExceptionDao().getFor("m1", week6.toEpochDay())!!
        assertTrue("B 必须知道第 6 周例外已被 A 清掉", w6.isDeleted)
        assertEquals(false, dbB.eventExceptionDao().getFor("m1", week3.toEpochDay())!!.isDeleted)
        assertEquals(1, dbB.eventExceptionDao().observeAll().first().size)

        // 系列本身:B 的旧系列也截止到第 4 周;窗口内可见 = 第1/2/4 周(第3周被取消)
        assertEquals(RuleEnd.Until(week5.minusDays(1)), phoneB.repository.getEvent("m1")!!.rule.end)
        assertEquals(3, phoneB.visibleCount())

        // A 侧同样收敛(两台最终一致)
        assertEquals(1, dbA.eventExceptionDao().observeAll().first().size)
        assertEquals(3, phoneA.visibleCount())
    }
}
