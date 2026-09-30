package com.lnx.app.feature.calendar.week

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.entity.EventEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 周视图事件块渲染(plan Task 3 Step 6 的要求)。
 * 用 Hilt 内存库预置真实数据,验证块真的按时间位置出现在网格里。
 *
 * 事件一律种在"当前时刻"附近:TimeGrid 打开时会自动滚到当前时刻,
 * 固定种在 09:00 会让断言随时钟漂移而 flaky。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WeekEventBlockTest {
    // Hilt 规则必须最先跑:它负责创建测试组件,Activity 才能拿到注入好的依赖
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var dao: EventDao

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun seed(
        id: String,
        title: String,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Long,
    ) = runBlocking {
        val base = LocalDate.now().atStartOfDay()
        val start = base.plusHours(startHour.toLong()).plusMinutes(startMinute.toLong())
        dao.upsert(
            EventEntity(
                id = id,
                title = title,
                allDay = false,
                startAt = start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                endAt = start.plusMinutes(durationMinutes)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
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
            ),
        )
    }

    @Test
    fun 事件块与标题出现在周视图() {
        val now = java.time.LocalTime.now()
        seed("e-standup", "每日站会", now.hour, now.minute, 60)
        rule.waitForIdle()
        rule.onAllNodesWithTag("event_block_e-standup").onFirst().assertExists()
        rule.onAllNodesWithText("每日站会").onFirst().assertIsDisplayed()
    }

    @Test
    fun `事件块的顶端与当前时刻线对齐`() {
        // 位置断言:块从"现在"开始,它的上沿必须与 now_line 重合。
        // 只断言"存在"无法区分"画在了正确的时间位置"和"画在了别处"。
        val now = java.time.LocalTime.now()
        seed("e-align", "对齐校验", now.hour, now.minute, 60)
        rule.waitForIdle()
        val blockTop = rule.onAllNodesWithTag("event_block_e-align").onFirst()
            .getUnclippedBoundsInRoot().top.value.toDouble()
        val lineTop = rule.onAllNodesWithTag("now_line").onFirst()
            .getUnclippedBoundsInRoot().top.value.toDouble()
        assertEquals(lineTop, blockTop, 2.0)
    }

    @Test
    fun `块显示时间行`() {
        val now = java.time.LocalTime.now()
        seed("e-time", "带时间", now.hour, now.minute, 60)
        rule.waitForIdle()
        rule.onAllNodesWithText("%02d:%02d".format(now.hour, now.minute), substring = true)
            .onFirst().assertExists()
    }

    @Test
    fun `有事件时不再显示空状态文案`() {
        seed("e-standup", "每日站会", 9, 0, 60)
        rule.waitForIdle()
        rule.onAllNodesWithText("没有日程", substring = true).onFirst().assertDoesNotExist()
    }

    @Test
    fun `两个同时段事件各占一块`() {
        val now = java.time.LocalTime.now()
        seed("e-a", "评审A", now.hour, now.minute, 60)
        seed("e-b", "评审B", now.hour, now.minute, 60)
        // 等数据库回流(写库 → Flow → 重组是异步的,只 waitForIdle 会在慢机器上偶发空断言)
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("event_block_e-a").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithTag("event_block_e-a").onFirst().assertExists()
        rule.onAllNodesWithTag("event_block_e-b").onFirst().assertExists()
    }

    @Test
    fun `全天事件不进时间轴`() {
        runBlocking {
            val base = LocalDate.now().atStartOfDay()
            dao.upsert(
                seededAllDay("e-allday", "团建日", base),
            )
        }
        rule.waitForIdle()
        // 全天块由 AllDayStrip 承载(M3 渲染),时间轴里不应出现
        rule.onAllNodesWithTag("event_block_e-allday").onFirst().assertDoesNotExist()
        assertEquals(1, runBlocking { dao.getById("e-allday") }?.let { 1 } ?: 0)
    }

    @Test
    fun `不同天的事件块画在各自的列里`() {
        // 回归(M9 抓到的真 bug):渲染循环写成 `blocks.forEach { day -> … }`,
        // 外层那个"第几天"被丢掉,横向位置只由**当天内的车道**决定 ——
        // 于是整周的日程全叠在第一列里,彼此按车道并排。
        // 单日事件的截图看不出来,九个里程碑的走查截图恰好都是单日事件。
        //
        // 判据取"两块不许横向重叠":出 bug 时两者位置完全相同,必然重叠;
        // 修好后中间隔着整整一列。比硬编码像素值稳。
        val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        seedOn(monday, "跨列校验·周一", 9, 0, 60)
        seedOn(monday.plusDays(2), "跨列校验·周三", 10, 0, 60)
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("event_block_e-col-wed").fetchSemanticsNodes().isNotEmpty()
        }

        val mon = rule.onAllNodesWithTag("event_block_e-col-mon").onFirst()
            .getUnclippedBoundsInRoot()
        val wed = rule.onAllNodesWithTag("event_block_e-col-wed").onFirst()
            .getUnclippedBoundsInRoot()

        assertTrue(
            "周三的块(${wed.left})应当在周一的块(${mon.left})右侧,而不是叠在同一列",
            wed.left.value > mon.left.value,
        )
        assertTrue(
            "两块不该横向重叠:周一 [${mon.left}, ${mon.right}] 周三 [${wed.left}, ${wed.right}]",
            wed.left.value >= mon.right.value - 1f,
        )
    }

    private fun seedOn(
        date: LocalDate,
        title: String,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Long,
    ) = runBlocking {
        val id = when {
            title.endsWith("周一") -> "e-col-mon"
            title.endsWith("周三") -> "e-col-wed"
            else -> "e-${date}"
        }
        val start = date.atStartOfDay().plusHours(startHour.toLong()).plusMinutes(startMinute.toLong())
        dao.upsert(
            EventEntity(
                id = id,
                title = title,
                allDay = false,
                startAt = start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                endAt = start.plusMinutes(durationMinutes)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
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
            ),
        )
    }

    private fun seededAllDay(
        id: String,
        title: String,
        dayStart: java.time.LocalDateTime,
    ) = EventEntity(
        id = id,
        title = title,
        allDay = true,
        startAt = dayStart.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        endAt = dayStart.plusDays(1).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
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
}
