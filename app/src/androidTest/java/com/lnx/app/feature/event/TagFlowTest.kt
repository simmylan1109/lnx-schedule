package com.lnx.app.feature.event

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.TagRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
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
 * M3 端到端:编辑器选标签保存(spec §3.5/§3.10)、全天跨天事件的横条渲染(spec §3.3)。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TagFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var repository: EventRepository

    @Inject
    lateinit var tagRepository: TagRepository

    @Inject
    lateinit var dao: EventDao

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `编辑器选两个标签保存后详情卡显示`() {
        val work = runBlocking { tagRepository.createTag("工作", 4) }
        val life = runBlocking { tagRepository.createTag("生活", 3) }
        rule.waitForIdle()

        rule.onNodeWithTag("fab_create").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("field_title").performTextInput("带标签的事")
        rule.onNodeWithTag("tag_chip_${work.id}").performClick()
        rule.onNodeWithTag("tag_chip_${life.id}").performClick()
        rule.onNodeWithTag("save_button").performClick()

        // 保存成功 = 编辑页关闭;然后取到事件 id,等块出现
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("save_button").fetchSemanticsNodes().isEmpty()
        }
        val id = runBlocking { dao.allOnce().first { it.title == "带标签的事" }.id }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("event_block_$id").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("event_block_$id").performClick()
        rule.waitForIdle()

        rule.onAllNodesWithText("工作").onFirst().assertExists()
        rule.onAllNodesWithText("生活").onFirst().assertExists()
    }

    @Test
    fun `新建标签对话框第8个色点可滑动到并选中`() {
        rule.waitForIdle()
        rule.onNodeWithTag("fab_create").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tag_create").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tag_name_field").performTextInput("第八色")

        // 回归:8 个 48dp 色点排不开,容器必须可横向滑动,否则第 8 个点用户永远点不到
        rule.onNode(
            hasScrollAction() and hasAnyDescendant(hasTestTag("tag_color_7")),
        ).performScrollToNode(hasTestTag("tag_color_7"))
        rule.onNodeWithTag("tag_color_7").performClick()
        rule.onNodeWithTag("tag_create_confirm").performClick()
        rule.waitForIdle()

        val created = runBlocking { tagRepository.observeTags().first { it.isNotEmpty() } }
            .first { it.name == "第八色" }
        assertEquals(7, created.colorSlot)
        rule.onNodeWithTag("tag_chip_${created.id}").assertIsDisplayed()
    }

    @Test
    fun `跨天全天事件在周视图显示连续横条`() {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        runBlocking {
            repository.save(
                Event(
                    id = "all-day-x",
                    title = "全天长事件",
                    allDay = true,
                    start = monday.atStartOfDay(),
                    end = monday.plusDays(3).atStartOfDay(), // 周一到周三,排他
                    location = null,
                    notes = null,
                    colorSlot = 2,
                    priority = Priority.P2,
                    reminderLeadMinutes = null,
                    rule = EventRule(RuleType.NONE, end = RuleEnd.Never),
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            )
        }
        rule.waitForIdle()

        rule.onNodeWithTag("all_day_bar_all-day-x").assertIsDisplayed()
        // 点横条 → 详情卡(spec §3.3:交互与事件块一致)
        rule.onNodeWithTag("all_day_bar_all-day-x").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("全天长事件").onFirst().assertExists()
    }
}
