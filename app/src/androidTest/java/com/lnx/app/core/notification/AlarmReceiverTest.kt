package com.lnx.app.core.notification

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 到点接收器的端到端回归。
 *
 * 这条测试是因为一次真崩溃才补的:`onReceive` 里 `goAsync()` 被调了两次
 * (fire 取一次、内部再调 reschedule 又取一次),而 AOSP 实现把 `mPendingResult` 置空后再取
 * 必然返回 null → `pending.finish()` NPE → **每次提醒到点 App 就崩**。
 * 实测堆栈:`NullPointerException ... at AlarmReceiver$reschedule$1.invokeSuspend(AlarmReceiver.kt:69)`。
 * 因为通知是先发出去的,走查看不出问题 —— 只有真的把广播打进去才能暴露。
 *
 * **`HiltAndroidRule` 必须有**:接收器靠 `EntryPointAccessors` 取依赖,而测试进程的组件要靠这个
 * rule 创建;少了它报 "The component was not created. Check that you have added the HiltAndroidRule",
 * 接收器会"打不到依赖就跳过"—— 测试看着是绿的假象或断言失败,却查不出原因。
 *
 * 断言方式:发广播前先把闹钟全清掉,发完之后等"续排闹钟"被重新排上 ——
 * 它是补排链路的终点。如果中途崩了,续排不会出现;而且崩溃会把整个测试进程带走,
 * 报告会直接是 "Process crashed"。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AlarmReceiverTest {

    /** 创建 Hilt 测试组件(见类注释:少了它接收器取不到依赖) */
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    private lateinit var scheduler: ReminderScheduler
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        hiltRule.inject()
        scheduler = ReminderScheduler(context)
        scheduler.cancelAll()
    }

    private fun continueSlotExists(): Boolean = PendingIntent.getBroadcast(
        context,
        ReminderScheduler.CONTINUE_SLOT,
        Intent(context, AlarmReceiver::class.java).setAction(ReminderScheduler.ACTION_CONTINUE),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    @Test
    fun 到点广播跑完弹通知和补排_不崩() {
        val occurrenceStart = LocalDateTime.now().plusMinutes(30)
        val reminder = ScheduledReminder(
            eventId = "receiver-test",
            occurrenceStart = occurrenceStart,
            remindAt = occurrenceStart.minusMinutes(15),
            title = "到点测试",
            location = "Room 1",
        )

        assertTrue("发之前必须没有续排闹钟", !continueSlotExists())

        val intent = Intent(ReminderScheduler.ACTION_FIRE)
            .setComponent(ComponentName(context, AlarmReceiver::class.java))
            .putExtra(ReminderScheduler.EXTRA_EVENT_ID, reminder.eventId)
            .putExtra(ReminderScheduler.EXTRA_OCCURRENCE_START, reminder.occurrenceStart.toEpochMillis())
            .putExtra(ReminderScheduler.EXTRA_REMIND_AT, reminder.remindAt.toEpochMillis())
            .putExtra(ReminderScheduler.EXTRA_TITLE, reminder.title)
            .putExtra(ReminderScheduler.EXTRA_LOCATION, reminder.location)
        context.sendBroadcast(intent)

        // 补排链路的终点:续排闹钟被重新排上 = 整条 fire 路径跑通了没崩
        val deadline = System.currentTimeMillis() + 5_000
        var ok = false
        while (System.currentTimeMillis() < deadline && !ok) {
            Thread.sleep(100)
            ok = continueSlotExists()
        }
        assertTrue("发完广播 5 秒内应重新排上续排闹钟(说明 fire 路径跑完没崩)", ok)
    }
}
