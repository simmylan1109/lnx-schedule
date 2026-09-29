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
 * 系统把这条通知标没标成静音 —— `dumpsys notification` 会把 flags 打印出来。
 * 所以这里:发一条 `silent = true` 的通知 → shell 读 dumpsys → 断言该记录带 SILENT。
 *
 * 策略侧的正确性(跨午夜窗口、边界排他)由 `DndPolicyTest` 9 条单测覆盖;
 * "到点那一刻才判断静音"由 `AlarmReceiver.fire` 的一行接线覆盖。
 */
@RunWith(AndroidJUnit4::class)
class ReminderNotifierTest {

    private lateinit var notifier: ReminderNotifier

    @Before
    fun setUp() {
        notifier = ReminderNotifier(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    private fun reminder(id: String, silentTitle: String) = ScheduledReminder(
        eventId = id,
        occurrenceStart = LocalDateTime.now().plusMinutes(10),
        remindAt = LocalDateTime.now(),
        title = silentTitle,
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

    @Test
    fun 静音通知会被系统标成SILENT() {
        notifier.show(reminder("silent-probe", "静音测试"), silent = true)

        val deadline = System.currentTimeMillis() + 5_000
        var found = false
        var dump = ""
        while (System.currentTimeMillis() < deadline && !found) {
            Thread.sleep(200)
            dump = dumpsys()
            // 按 id 记录定位:dumpsys 里同一行同时有 pkg/id 和 flags
            found = dump.contains("pkg=com.lnx.app")
        }
        assertTrue("dumpsys 里应能看到本应用的通知记录", found)
        val seg = dump.substringAfter("pkg=com.lnx.app").substringBefore("pkg=")
        // API 35 实测:`setSilent(true)` 的通知在 dumpsys 里会带 `groupKey=silent`,
        // 而 flags 里并不会出现 "SILENT" 字样(第一版断言就是想当然写错了)
        android.util.Log.i("lnx-notify", "SILENT-RECORD >>>$seg<<<")
        assertTrue("静音通知的 dump 应带 groupKey=silent", seg.contains("groupKey=silent"))
    }

    @Test
    fun 非静音通知不带SILENT标志() {
        notifier.show(reminder("normal-probe", "正常通知"), silent = false)

        val deadline = System.currentTimeMillis() + 5_000
        var dump = ""
        while (System.currentTimeMillis() < deadline && !dump.contains("pkg=com.lnx.app")) {
            Thread.sleep(200)
            dump = dumpsys()
        }
        val seg = dump.substringAfter("pkg=com.lnx.app").substringBefore("pkg=")
        android.util.Log.i("lnx-notify", "NORMAL-RECORD >>>$seg<<<")
        assertTrue("本应用的通知应在 dumpsys 里", dump.contains("pkg=com.lnx.app"))
        assertTrue("对照组不该带静音标记", !seg.contains("groupKey=silent"))
    }
}
