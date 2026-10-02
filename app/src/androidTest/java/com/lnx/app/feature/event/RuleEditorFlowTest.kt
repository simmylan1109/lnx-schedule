package com.lnx.app.feature.event

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lnx.app.MainActivity
import com.lnx.app.R
import com.lnx.app.core.common.LnxLocale
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.recurrence.RuleDescription
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.DayOfWeek
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
 * 规则编辑器 + 展开端到端(spec §3.7/§8.1 M4 ①):
 * 选"每周"保存 → 当周与下周都出现(数据层断言,避免被禁的滑动手势)。
 * 本机禁令:仪器测试不用 performTouchInput。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RuleEditorFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var repository: EventRepository

    /** 规则描述由 RuleDescription 生成(domain 层,故意不进资源):断言调同一个函数(终审 P3-3) */
    private val locale = LnxLocale.resolve(LnxLocale.SYSTEM)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /** 取节点显示文本;折叠行是 clickable(合并后代),其子节点的 testTag 只在未合并树里找得到 */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.textOf(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }
            ?: ""

    @Test
    fun `选每周保存后当周与下周都出现且详情卡显示规则描述`() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("fab_create").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("fab_create").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("field_title").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("field_title").performTextInput("每周例会")

        // 展开重复编辑区 → 选"每周"(默认预填系列起点的星期几)
        rule.onNodeWithTag("rule_row").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rule_type_WEEKLY").performClick()
        rule.waitForIdle()

        // 折叠行是 clickable(会合并后代),其子节点的 testTag 只在未合并树里找得到。
        // 描述文案由 RuleDescription 生成(domain 层,故意不进资源)—— 断言调同一个函数,
        // 与生产逐字相等,而不是去 strings.xml 碰巧相等(终审 P3-3)
        val summary = rule.onNodeWithTag("rule_summary", useUnmergedTree = true).textOf()
        val expectedSummary = RuleDescription.of(
            EventRule(type = RuleType.WEEKLY, weekdays = setOf(LocalDate.now().dayOfWeek)),
            locale,
        )
        check(summary == expectedSummary) { "描述异常:期望 $expectedSummary,实际 $summary" }

        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("save_button").fetchSemanticsNodes().isEmpty()
        }

        // 数据层验收:当周 + 下周 + 第 8 周都各有一份发生(spec §8.1 M4 ① 连续 8 周)
        val today = LocalDate.now()
        val thisMonday = today.with(DayOfWeek.MONDAY)
        repeat(8) { week ->
            val start = thisMonday.plusWeeks(week.toLong()).atStartOfDay()
            val end = start.plusWeeks(1)
            val hits = runBlocking { repository.observeOccurrences(start, end).first() }
                .filter { it.event.title == "每周例会" }
            assertEquals("第 ${week + 1} 周", 1, hits.size)
        }

        // 详情卡显示完整中文规则描述
        val first = runBlocking {
            repository.observeOccurrences(thisMonday.atStartOfDay(), thisMonday.plusWeeks(1).atStartOfDay()).first()
                .first { it.event.title == "每周例会" }
        }
        rule.onAllNodesWithTag("event_block_${first.event.id}").onFirst().assertIsDisplayed()
        rule.onNodeWithTag("event_block_${first.event.id}").performClick()
        rule.waitForIdle()
        // 详情卡"重复"行显示完整描述(spec §3.6,例:每周二重复,永不结束)—— 同上,调生产函数
        rule.onAllNodesWithText(expectedSummary, substring = false).onFirst().assertExists()
    }

    @Test
    fun `重复次数结束_第N次之后不再展开`() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("fab_create").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("fab_create").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("field_title").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("field_title").performTextInput("三次小会")
        rule.onNodeWithTag("rule_row").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rule_type_DAILY").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("rule_end_COUNT").performClick()
        rule.waitForIdle()
        // 步进器到 3:默认 10 → − → − → − …;直接点到 3 需要 7 次,用计数循环
        repeat(7) { rule.onNodeWithTag("rule_count_down").performClick() }
        assertEquals(
            "3",
            rule.onNodeWithTag("rule_count_value").textOf(),
        )

        rule.onNodeWithTag("save_button").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("save_button").fetchSemanticsNodes().isEmpty()
        }

        val today = LocalDate.now()
        val window = today.atStartOfDay()
        val hits = runBlocking { repository.observeOccurrences(window, window.plusDays(30)).first() }
            .filter { it.event.title == "三次小会" }
        // 从系列第一次起共 3 次(系列起点 = 今天):第 4 天起不再有
        assertEquals(3, hits.size)
    }
}
