package com.lnx.app.feature.calendar.day

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.common.formatTitle
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 日视图(spec §3.3):日期条点选、单列时间轴、点事件详情。
 * 本机禁令:仪器测试不用 performTouchInput 真实手势 —— 翻日手势留给人工走查。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DayViewTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var repository: EventRepository

    @Inject
    lateinit var dao: EventDao

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun event(id: String, title: String, day: LocalDate, hour: Int) = Event(
        id = id,
        title = title,
        allDay = false,
        start = day.atTime(hour, 0),
        end = day.atTime(hour, 0).plusMinutes(60),
        location = null,
        notes = null,
        colorSlot = 5,
        priority = Priority.P2,
        reminderLeadMinutes = null,
        rule = EventRule(RuleType.NONE, end = RuleEnd.Never),
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `切到日Tab显示日期条与单列时间轴`() {
        rule.onNodeWithTag("tab_DAY").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("day_pager").assertExists()
        rule.onNodeWithTag("day_strip").assertExists()
        // 今天的日期格存在(高亮与否由视觉走查)
        rule.onNodeWithTag("day_cell_${LocalDate.now()}").assertExists()
        // 周视图的占位文案不应再出现
        rule.onNodeWithText("日视图将在后续里程碑提供").assertDoesNotExist()
    }

    @Test
    fun `点日期条选明天后标题切换且该日事件可见`() {
        val tomorrow = LocalDate.now().plusDays(1)
        val hour = LocalTime.now().hour
        runBlocking { repository.save(event("e-day", "明天的事", tomorrow, hour)) }
        rule.waitForIdle()

        rule.onNodeWithTag("tab_DAY").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("day_cell_$tomorrow").performClick()
        rule.waitForIdle()

        // 顶栏标题跟随所选日期(与 M1 CalendarScreenTest 同款断言)
        rule.onNodeWithText(formatTitle(tomorrow)).assertExists()
        // 该日的事件块进入组合(数据窗口按所选日查询)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("event_block_e-day").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `选中的非今天日期显示该天空态文案`() {
        val tomorrow = LocalDate.now().plusDays(1)
        rule.onNodeWithTag("tab_DAY").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("day_cell_$tomorrow").performClick()
        // 数据窗口按所选日重查是异步的,轮询等文案出现(裸 waitForIdle 会抢跑)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("这天没有日程,享受自由时光", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("这天没有日程,享受自由时光", substring = true).onFirst().assertExists()
    }
}
