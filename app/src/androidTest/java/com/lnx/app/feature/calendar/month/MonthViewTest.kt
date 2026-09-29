package com.lnx.app.feature.calendar.month

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 月视图(spec §3.4):点日期 → 下方列表联动;点列表条目 → 详情;空态 → 无日程 + ＋ 新建日程。
 * 同一日期可能同时出现在相邻两页的补位里,pager 相邻页都会进语义树,
 * 断言一律用 onAllNodesWithTag(...).onFirst();点哪一份都触发同一回调。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MonthViewTest {
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
        colorSlot = 4,
        priority = Priority.P2,
        reminderLeadMinutes = null,
        rule = EventRule(RuleType.NONE, end = RuleEnd.Never),
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `切月Tab显示月历与今天的格子`() {
        rule.onNodeWithTag("tab_MONTH").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("month_pager").assertExists()
        rule.onNodeWithTag("month_agenda").assertExists()
        rule.onAllNodesWithTag("month_cell_${LocalDate.now()}").onFirst().assertExists()
    }

    @Test
    fun `点有事件的日期下方列表联动且点条目弹详情`() {
        val target = LocalDate.now().plusDays(2)
        val hour = LocalTime.now().hour
        runBlocking { repository.save(event("e-month", "月列表事件", target, hour)) }
        rule.waitForIdle()

        rule.onNodeWithTag("tab_MONTH").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("month_cell_$target").onFirst().performClick()

        // 下方列表切换为该天的事件(等异步数据窗口跟上)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("agenda_item_e-month").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("月列表事件").onFirst().assertExists()

        // 点列表条目 → 详情卡(spec §3.4)
        rule.onAllNodesWithTag("agenda_item_e-month").onFirst().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("月列表事件").onFirst().assertExists()
        rule.onAllNodesWithText("时间").onFirst().assertExists()
    }

    @Test
    fun `空日期显示无日程并可从空态新建`() {
        val emptyDay = LocalDate.now().plusDays(5)
        rule.onNodeWithTag("tab_MONTH").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag("month_cell_$emptyDay").onFirst().performClick()

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("${emptyDay.monthValue}月${emptyDay.dayOfMonth}日 · 无日程")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("month_empty_create").performClick()
        rule.waitForIdle()

        // 编辑页打开,开始时间预填该日 09:00(spec §3.5 月视图入口)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("field_title").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("新建事件").assertExists()
        // 月历 pager 会把相邻页一起组合进来,同一个日期在多处出现,取第一份即可
        rule.onAllNodesWithText("${emptyDay.monthValue}月${emptyDay.dayOfMonth}日", substring = true)
            .onFirst().assertExists()
        rule.onAllNodesWithText("09:00", substring = true).onFirst().assertExists()

        // 预填真的落进了草稿:存下来查库,而不是只看界面上有这几个字
        rule.onNodeWithTag("field_title").performTextInput("月视图预填")
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("save_button").fetchSemanticsNodes().isEmpty()
        }
        val saved = runBlocking { dao.allOnce().first { it.title == "月视图预填" } }
        assertEquals(
            emptyDay.atTime(9, 0),
            java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(saved.startAt),
                java.time.ZoneId.systemDefault(),
            ),
        )
    }
}
