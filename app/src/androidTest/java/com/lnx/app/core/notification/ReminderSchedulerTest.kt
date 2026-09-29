package com.lnx.app.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 槽位模型(M5 终审要求补的测试;此前只靠一次人工 `dumpsys` 兜底)。
 *
 * 断言手段是 `PendingIntent.getBroadcast(..., FLAG_NO_CREATE)` —— 它能直接回答
 * "这个槽位上现在有没有排着的闹钟",比读 dumpsys 文本可靠。
 *
 * 要钉住的性质:
 * - 排期后槽位上确实有东西;
 * - 重排(先全量取消再排)不残留:清单变短时多出来的槽位必须被清掉 —— 否则
 *   被删事件的旧闹钟会一直响;
 * - 续排闹钟占独立槽位,取消时一并清掉;
 * - 50 条上限之外的发生不占槽位。
 */
@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {

    private lateinit var scheduler: ReminderScheduler
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 2026-12-01 09:45 起,每次间隔一小时(提醒时刻 = 发生开始前 15 分钟) */
    private val remindAt = LocalDateTime.of(2026, 12, 1, 9, 45)

    @Before
    fun setUp() {
        scheduler = ReminderScheduler(context)
        scheduler.cancelAll() // 上一个测试的残留清掉
    }

    private fun reminder(id: String, at: LocalDateTime) = ScheduledReminder(
        eventId = id,
        occurrenceStart = at.plusMinutes(15),
        remindAt = at,
        title = "会议",
        location = null,
    )

    private fun list(count: Int) = (0 until count).map { reminder("e$it", remindAt.plusHours(it.toLong())) }

    /** 某个槽位上此刻是否有排着的闹钟 */
    private fun slotExists(slot: Int, action: String): Boolean = PendingIntent.getBroadcast(
        context,
        slot,
        Intent(context, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    private fun fireSlot(slot: Int) = slotExists(slot, ReminderScheduler.ACTION_FIRE)

    @Test
    fun 排期后每个槽位上都有闹钟() {
        scheduler.schedule(list(3), null)

        repeat(3) { assertTrue("槽位 $it 应有闹钟", fireSlot(it)) }
        assertFalse(slotExists(ReminderScheduler.CONTINUE_SLOT, ReminderScheduler.ACTION_CONTINUE))
    }

    @Test
    fun 清单变短时多出来的槽位必须被清掉() {
        scheduler.schedule(list(3), null)
        assertTrue(fireSlot(2))

        // 只剩一条(典型场景:删除了两个带提醒的事件)
        scheduler.schedule(list(1), null)

        assertTrue(fireSlot(0))
        assertFalse("被删事件留下的旧闹钟必须清掉", fireSlot(1))
        assertFalse(fireSlot(2))
    }

    @Test
    fun 空清单把所有闹钟清干净() {
        scheduler.schedule(list(5), null)

        scheduler.schedule(emptyList(), null)

        repeat(5) { assertFalse("槽位 $it 应被清空", fireSlot(it)) }
    }

    @Test
    fun 续排闹钟占独立槽位_取消时一并清掉() {
        scheduler.schedule(list(1), remindAt.plusDays(30))

        assertTrue(slotExists(ReminderScheduler.CONTINUE_SLOT, ReminderScheduler.ACTION_CONTINUE))
        assertTrue(fireSlot(0))

        scheduler.schedule(emptyList(), null)

        assertFalse(slotExists(ReminderScheduler.CONTINUE_SLOT, ReminderScheduler.ACTION_CONTINUE))
        assertFalse(fireSlot(0))
    }

    @Test
    fun 槽位数不超过上限_超出的发生不占槽位() {
        scheduler.schedule(list(80), null)

        repeat(NextReminderCalculator.MAX_PER_RESCHEDULE) {
            assertTrue("槽位 $it 应有闹钟", fireSlot(it))
        }
        assertFalse(
            "超出 50 条的部分不该占用续排槽位",
            slotExists(ReminderScheduler.CONTINUE_SLOT, ReminderScheduler.ACTION_CONTINUE),
        )
    }
}
