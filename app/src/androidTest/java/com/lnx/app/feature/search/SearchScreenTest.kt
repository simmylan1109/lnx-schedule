package com.lnx.app.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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

    private fun seedToday(id: String, title: String) = runBlocking {
        val now = LocalDateTime.now()
        dao.upsert(
            EventEntity(
                id = id,
                title = title,
                allDay = false,
                startAt = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                // 结束取"一小时后"而不是当天 23:00:23 点之后跑的用例里,23:00 已经过去,
                // 这条事件就不与"今天"相交,块会消失,断言变成看时钟的 flaky
                endAt = now.plusHours(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
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
        rule.onNodeWithContentDescription("搜索").performClick()
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
    fun 返回按钮关掉搜索页回到日历() {
        openSearch()
        rule.onNodeWithTag("search_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("search_screen").assertDoesNotExist()
        rule.onNodeWithTag("week_pager").assertExists()
    }
}
