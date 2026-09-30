package com.lnx.app.core.notification

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lnx.app.core.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    /** 免打扰链路测试要改设置(M6 起接收器到点读设置) */
    @Inject
    lateinit var settings: SettingsRepository

    private lateinit var scheduler: ReminderScheduler
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        hiltRule.inject()
        scheduler = ReminderScheduler(context)
        scheduler.cancelAll()
    }

    /**
     * 这个套件用的是**真的**系统闹钟,不清理就会把续排闹钟留在机器上,
     * 并污染之后跑的 dumpsys 走查证据(第二轮复核对 ReminderSchedulerTest 也提过同款问题)。
     */
    @After
    fun tearDown() {
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

    // —— 免打扰:设置 → 接收器的整条链路(spec §3.11 ② + §3.8) ——
    // 这是 M6「去双源」的核心改动(免打扰窗口从编译期常量改成运行时读设置),
    // 但此前只有纯函数单测(参数直传),"改了设置对到点提醒真的生效"没人验过(终审点名)。

    private fun dumpsys(): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys notification --noredact")
        return java.io.FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
            .also { pfd.close() }
    }

    /** 按通知 id 定位该条记录的 dump 片段(拿不到时返回空串) */
    private fun awaitRecordOf(eventId: String, timeoutMillis: Long = 5_000): String {
        val id = ReminderNotifier.notificationId(
            ScheduledReminder(eventId, LocalDateTime.now(), LocalDateTime.now(), "", null),
        )
        val marker = "key=0|com.lnx.app|$id|"
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val dump = dumpsys()
            val from = dump.indexOf(marker)
            if (from >= 0) {
                val start = dump.lastIndexOf("NotificationRecord", from)
                val end = dump.indexOf("pkg=", from)
                return dump.substring(start, if (end < 0) dump.length else end)
            }
            Thread.sleep(200)
        }
        return ""
    }

    /** 发一条"到点"广播并等它的通知出现 */
    private fun fireAndAwaitRecord(eventId: String): String {
        val occurrenceStart = LocalDateTime.now().plusMinutes(5)
        val intent = Intent(ReminderScheduler.ACTION_FIRE)
            .setComponent(ComponentName(context, AlarmReceiver::class.java))
            .putExtra(ReminderScheduler.EXTRA_EVENT_ID, eventId)
            .putExtra(ReminderScheduler.EXTRA_OCCURRENCE_START, occurrenceStart.toEpochMillis())
            .putExtra(ReminderScheduler.EXTRA_REMIND_AT, System.currentTimeMillis())
            .putExtra(ReminderScheduler.EXTRA_TITLE, "免打扰链路测试")
        context.sendBroadcast(intent)
        return awaitRecordOf(eventId)
    }

    @Test
    fun 免打扰开着时到点通知被标静音_关掉后不静音() {
        val nowMinute = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        // 开一个"覆盖此刻"的窗口(前后各 1 分钟),不管跑测试时是几点几分
        runBlocking {
            settings.setDnd(
                enabled = true,
                startMinute = (nowMinute + 1439) % 1440,
                endMinute = (nowMinute + 2) % 1440,
            )
        }

        val quietRecord = fireAndAwaitRecord("dnd-quiet-probe")
        assertTrue("开着免打扰时应能看到通知记录", quietRecord.isNotEmpty())
        assertTrue(
            "免打扰窗口内,接收器应把通知标成静音(groupKey=silent);实际 dump:${quietRecord.take(200)}",
            quietRecord.contains("groupKey=silent"),
        )

        // 关掉免打扰再发一条:同一条链路,标记应该反过来
        runBlocking {
            settings.setDnd(
                enabled = false,
                startMinute = (nowMinute + 1439) % 1440,
                endMinute = (nowMinute + 2) % 1440,
            )
        }
        val loudRecord = fireAndAwaitRecord("dnd-loud-probe")
        assertTrue("关掉免打扰后也应能看到通知记录", loudRecord.isNotEmpty())
        assertTrue(
            "免打扰关掉后不该再带静音标记;实际 dump:${loudRecord.take(200)}",
            !loudRecord.contains("groupKey=silent"),
        )
    }
}
