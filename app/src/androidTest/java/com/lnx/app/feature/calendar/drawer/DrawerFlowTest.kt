package com.lnx.app.feature.calendar.drawer

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lnx.app.MainActivity
import com.lnx.app.R
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.TagRepository
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 抽屉标签筛选(spec §3.10):勾掉标签/未分类 → 对应事件从视图消失;勾回恢复。
 * 断言走语义树(块存在性),不依赖抽屉开合 —— 筛选在 VM 数据流生效,树必然反映。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DrawerFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var repository: EventRepository

    @Inject
    lateinit var tagRepository: TagRepository

    /** 框架文案从资源现取(补欠账③);"工作"是本测试自己 seed 的数据,不属于此类 */
    private val strings = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun event(id: String, title: String, hour: Int) = Event(
        id = id,
        title = title,
        allDay = false,
        start = LocalDate.now().atTime(hour, 0),
        end = LocalDate.now().atTime(hour, 0).plusMinutes(60),
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
    fun `勾掉未分类后无标签事件消失_勾回恢复`() {
        runBlocking {
            tagRepository.createTag("工作", 4)
            repository.save(event("tagged-x", "有标签的", 10))
            repository.save(event("untagged-x", "没标签的", 12))
            tagRepository.setEventTags("tagged-x", listOf(tagRepository.observeTags().first().first { it.name == "工作" }.id))
        }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("event_block_tagged-x").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithTag("event_block_untagged-x").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithTag("menu_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("工作").assertExists()
        rule.onNodeWithText(strings.getString(R.string.drawer_untagged)).assertExists()

        // 勾掉"未分类"
        rule.onNodeWithTag("drawer_untagged").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("event_block_untagged-x").fetchSemanticsNodes().isEmpty()
        }
        rule.onAllNodesWithTag("event_block_tagged-x").onFirst().assertExists() // 有标签的不受影响

        // 勾回"未分类" → 恢复
        rule.onNodeWithTag("drawer_untagged").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("event_block_untagged-x").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `勾掉工作标签后含该标签的事件隐藏`() {
        val workId = runBlocking {
            val work = tagRepository.createTag("工作", 4).getOrThrow()
            repository.save(event("tagged-x", "有标签的", 10))
            repository.save(event("untagged-x", "没标签的", 12))
            tagRepository.setEventTags("tagged-x", listOf(work.id))
            work.id
        }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("event_block_tagged-x").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithTag("menu_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_tag_$workId").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("event_block_tagged-x").fetchSemanticsNodes().isEmpty()
        }
        rule.onAllNodesWithTag("event_block_untagged-x").onFirst().assertExists() // 无标签的不受影响
    }
}
