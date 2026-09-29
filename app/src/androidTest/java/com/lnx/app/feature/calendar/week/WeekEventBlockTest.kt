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
        rule.waitForIdle()
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
