package com.lnx.app.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.entity.EventEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 搜索页端到端(spec §3.9):顶栏图标进入 → 输入 → 出结果 → 点结果跳日视图。
 * 搜索页是 M7 新入口,这几条钉住"入口接上了、结果出得来、跳转落得准"。
 *
 * 事件一律种在今天:日视图时间轴打开时自动滚到当前时刻,块一定在屏内(种在固定 09:00 会 flaky)。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var dao: EventDao

    @Before
    fun inject() {
        hiltRule.inject()
    }

    private fun seedToday(id: String, title: String) = seedAt(id, title, LocalDateTime.now())

    private fun seedAt(id: String, title: String, start: LocalDateTime) = runBlocking {
        dao.upsert(
            EventEntity(
                id = id,
                title = title,
                allDay = false,
                startAt = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                // 结束取"一小时后"而不是当天 23:00:23 点之后跑的用例里,23:00 已经过去,
                // 这条事件就不与"今天"相交,块会消失,断言变成看时钟的 flaky
                endAt = start.plusHours(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
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

    private fun openSearch() {
        // 用 testTag 而不是中文 contentDescription:后者只在"进程默认语言被钉成中文"时成立
        // (靠 HiltTestRunner),换台英文模拟器或哪天去掉钉死就会整类挂掉
        rule.onNodeWithTag("search_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("search_screen").assertIsDisplayed()
    }

    @Test
    fun 顶栏搜索图标能打开搜索页() {
        rule.onNodeWithTag("top_bar").assertExists()
        rule.onNodeWithTag("search_screen").assertDoesNotExist()
        openSearch()
    }

    @Test
    fun 输入关键词出结果() {
        seedToday("ss-hit", "SERP 复盘")
        openSearch()
        rule.onNodeWithTag("search_field").performTextInput("SERP")
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("search_result_ss-hit").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("search_result_ss-hit").assertIsDisplayed()
    }

    @Test
    fun 没有结果时显示空态文案() {
        openSearch()
        rule.onNodeWithTag("search_field").performTextInput("ZZZQQQ查无此会")
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("search_empty").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("search_empty").assertIsDisplayed()
    }

    @Test
    fun 点结果跳日视图并落在那天() {
        seedToday("ss-jump", "JUMP 评审")
        openSearch()
        rule.onNodeWithTag("search_field").performTextInput("JUMP")
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("search_result_ss-jump").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("search_result_ss-jump").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("search_screen").assertDoesNotExist()
        rule.onNodeWithTag("day_pager").assertIsDisplayed()
        // 落点必须是这条事件所在的那天:块出现在日视图里,才说明"定位"真的生效
        rule.onNodeWithTag("event_block_ss-jump").assertExists()
    }

    @Test
    fun 跳转后自动滚到那条事件的位置() {
        // 种一条"离现在最远"的今天事件:12 点前种在 23:00,12 点后种在 00:30。
        // 时间轴默认锚在"当前时刻",不滚的话这条在屏幕外 —— 高亮等于白高亮
        // (spec §3.9 要的是"定位 / 高亮",两个都得有)。
        val now = LocalDateTime.now()
        val target = if (now.hour < 12) now.toLocalDate().atTime(23, 0)
        else now.toLocalDate().atTime(0, 30)
        seedAt("ss-scroll", "SCROLL 评审", target)

        openSearch()
        rule.onNodeWithTag("search_field").performTextInput("SCROLL")
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("search_result_ss-scroll").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("search_result_ss-scroll").performClick()

        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("event_block_ss-scroll").fetchSemanticsNodes().isNotEmpty()
        }
        // exists 不够:块在屏外也 exists。assertIsDisplayed 要求它真的落在视口里
        rule.onNodeWithTag("event_block_ss-scroll").assertIsDisplayed()
    }

    @Test
    fun 返回按钮关掉搜索页回到日历() {
        openSearch()
        rule.onNodeWithTag("search_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("search_screen").assertDoesNotExist()
        rule.onNodeWithTag("week_pager").assertExists()
    }
}
