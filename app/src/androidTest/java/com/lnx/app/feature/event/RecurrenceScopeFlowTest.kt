package com.lnx.app.feature.event

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 三选一作用范围端到端(spec §8.1 M4 ②③):
 * 预置"每周今天"的重复事件 → 详情卡点编辑/删除 → 选作用范围 → 断言数据层只影响该动的部分。
 * 断言走 repository 窗口查询而非滑动翻周(本机禁 performTouchInput)。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecurrenceScopeFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var repository: EventRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private val title = "范围测试"

    /** 预置"每周今天 10:00-10:30、永不结束"的母事件 */
    private fun seed() {
        val today = LocalDate.now()
        runBlocking {
            repository.save(
                Event(
                    id = "scope-master",
                    title = title,
                    allDay = false,
                    start = today.atTime(10, 0),
                    end = today.atTime(10, 30),
                    location = null,
                    notes = null,
                    colorSlot = 3,
                    priority = Priority.P2,
                    reminderLeadMinutes = null,
                    rule = EventRule(RuleType.WEEKLY, weekdays = setOf(today.dayOfWeek), end = RuleEnd.Never),
                    createdAt = 1_000L,
                    updatedAt = 0L,
                ),
            )
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("event_block_scope-master").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 开详情卡(点块) → 等详情按钮出现 */
    private fun openDetail() {
        rule.onNodeWithTag("event_block_scope-master").performClick()
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("detail_edit").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 窗口内该标题的发生次数(直接问数据层,绕开翻页手势) */
    private fun countIn(weeks: Long, wantedTitle: String): Int {
        val start = LocalDate.now().plusWeeks(weeks).atStartOfDay()
        return runBlocking { repository.observeOccurrences(start, start.plusWeeks(1)).first() }
            .count { it.event.title == wantedTitle }
    }

    @Test
    fun `仅本次删除_当周这次消失_下周仍在`() {
        seed()
        openDetail()
        rule.onNodeWithTag("detail_delete").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("scope_THIS_ONLY").performClick()

        rule.waitUntil(timeoutMillis = 5_000) { countIn(0, title) == 0 }
        assertEquals(0, countIn(0, title))
        assertEquals(1, countIn(1, title)) // 其余周照常
    }

    @Test
    fun `仅本次修改_只改这次_下周还是原标题`() {
        seed()
        openDetail()
        rule.onNodeWithTag("detail_edit").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("scope_THIS_ONLY").performClick()
        // 编辑器带着该次发生打开("重复"区只读)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("field_title").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("field_title").performTextClearance()
        rule.onNodeWithTag("field_title").performTextInput("只改这次")
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("save_button").fetchSemanticsNodes().isEmpty()
        }

        rule.waitUntil(timeoutMillis = 5_000) { countIn(0, "只改这次") == 1 }
        assertEquals(1, countIn(0, "只改这次"))
        assertEquals(0, countIn(0, title)) // 这次被覆盖了,原标题这周没了
        assertEquals(1, countIn(1, title)) // 下周仍是母事件原标题
    }

    @Test
    fun `本次及以后修改_从这次起全变_旧系列不再延续`() {
        seed()
        openDetail()
        rule.onNodeWithTag("detail_edit").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("scope_THIS_AND_FUTURE").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("field_title").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("field_title").performTextClearance()
        rule.onNodeWithTag("field_title").performTextInput("新系列")
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("save_button").fetchSemanticsNodes().isEmpty()
        }

        rule.waitUntil(timeoutMillis = 5_000) { countIn(1, "新系列") == 1 }
        // 这次起 = 新系列(本周这次也是新标题,旧系列在它之前截止)
        assertEquals(1, countIn(0, "新系列"))
        assertEquals(0, countIn(1, title))
        // 新系列往后再走 4 周都还在(母事件没断)
        assertEquals(1, countIn(4, "新系列"))
    }

    @Test
    fun `全部修改_母事件本身被改`() {
        seed()
        openDetail()
        rule.onNodeWithTag("detail_edit").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("scope_ALL").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("field_title").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("field_title").performTextClearance()
        rule.onNodeWithTag("field_title").performTextInput("全改标题")
        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("save_button").fetchSemanticsNodes().isEmpty()
        }

        rule.waitUntil(timeoutMillis = 5_000) { countIn(1, "全改标题") == 1 }
        assertEquals(1, countIn(1, "全改标题"))
        assertEquals(1, countIn(4, "全改标题"))
        assertEquals(0, countIn(1, title))
    }
}
