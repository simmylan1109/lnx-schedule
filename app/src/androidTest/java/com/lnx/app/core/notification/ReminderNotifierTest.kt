package com.lnx.app.core.notification

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 通知构建与**静音标志**的端到端断言(spec §3.8 免打扰:时段内照发但不响铃)。
 *
 * 免打扰这条验收在模拟器上没法"听见"有没有声音(模拟器本来就没声音),能验的硬信号是
 * 系统把这条通知标没标成静音 —— `dumpsys notification` 会把 group 打印出来。
 * 实测:API 35 上 `setSilent(true)` 的通知记录带 `groupKey=silent`,非静音的不带
 * (不设 group 时系统根本不打印这个字段),所以正反两条断言都成立。
 * 静音链路的下游是系统按 `groupAlertBehavior` 抑制响铃 —— 这里验的是"该走的标记写对了"。
 *
 * 策略侧正确性(跨午夜窗口、边界排他)由 `DndPolicyTest` 9 条单测覆盖;
 * "到点那一刻才判断静音"由 `AlarmReceiver.fire` 的接线覆盖。
 *
 * **按通知 id 定位记录**,不用"第一条 lnx 记录":前面的 `AlarmReceiverTest` 也会真发通知,
 * 按位置取会读到别人的残留,顺序一变就 flaky。
 */
@RunWith(AndroidJUnit4::class)
class ReminderNotifierTest {

    private lateinit var notifier: ReminderNotifier

    @Before
    fun setUp() {
        notifier = ReminderNotifier(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun reminder(id: String) = ScheduledReminder(
        eventId = id,
        occurrenceStart = LocalDateTime.now().plusMinutes(10),
        remindAt = LocalDateTime.now(),
        title = "通知测试",
        location = null,
    )

    /**
     * 用 shell 读系统通知 dump。
     * 测试进程直接 `Runtime.exec("dumpsys ...")` 拿不到 dumpsys 权限(实测输出里没有本应用记录),
     * 必须走 `UiAutomation.executeShellCommand` —— 它以 shell uid 执行。
     */
    private fun dumpsys(): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys notification --noredact")
        // 不能写 pfd.inputStream:ParcelFileDescriptor 有同名重载,Kotlin 解析不了(HiltTestRunner 同坑)
        return java.io.FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
            .also { pfd.close() }
    }

    /** 按通知 id 定位该条记录的 dump 片段(拿不到时返回空串) */
    private fun recordOf(id: Int): String {
        val marker = "key=0|com.lnx.app|$id|"
        val dump = dumpsys()
        val from = dump.indexOf(marker)
        if (from < 0) return ""
        val start = dump.lastIndexOf("NotificationRecord", from)
        val end = dump.indexOf("pkg=", from)
        return dump.substring(start, if (end < 0) dump.length else end)
    }

    private fun awaitRecord(id: Int, timeoutMillis: Long = 5_000): String {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var record = ""
        while (System.currentTimeMillis() < deadline && record.isEmpty()) {
            Thread.sleep(200)
            record = recordOf(id)
        }
        return record
    }

    @Test
    fun 静音通知会被系统标成silent() {
        val r = reminder("silent-probe")
        val id = ReminderNotifier.notificationId(r)
        notifier.show(r, silent = true)

        val record = awaitRecord(id)
        android.util.Log.i("lnx-notify", "SILENT-RECORD >>>$record<<<")
        assertTrue("dumpsys 里应能看到本应用刚发的那条通知", record.isNotEmpty())
        assertTrue("静音通知的 dump 应带 groupKey=silent", record.contains("groupKey=silent"))
    }

    @Test
    fun 非静音通知不带silent标志() {
        val r = reminder("normal-probe")
        val id = ReminderNotifier.notificationId(r)
        notifier.show(r, silent = false)

        val record = awaitRecord(id)
        android.util.Log.i("lnx-notify", "NORMAL-RECORD >>>$record<<<")
        assertTrue("dumpsys 里应能看到本应用刚发的那条通知", record.isNotEmpty())
        assertTrue("对照组不该带静音标记", !record.contains("groupKey=silent"))
    }
}
