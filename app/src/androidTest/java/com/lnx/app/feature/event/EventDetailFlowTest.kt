package com.lnx.app.feature.event

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 端到端:详情卡(spec §3.6)与重叠轻提示(spec §3.5)。
 * 事件种在"现在"所在的时刻:周视图打开时自动把当前时刻滚到视口上部 1/3,块必然可见可点。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EventDetailFlowTest {
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

    private fun todayAt(hour: Int, minute: Int = 0) =
        LocalDate.now().atTime(hour, minute)

    private fun event(
        id: String,
        title: String,
        start: java.time.LocalDateTime,
        end: java.time.LocalDateTime,
        location: String? = null,
        notes: String? = null,
    ) = Event(
        id = id,
        title = title,
        allDay = false,
        start = start,
        end = end,
        location = location,
        notes = notes,
        colorSlot = 3,
        priority = Priority.P1,
        reminderLeadMinutes = 15,
        rule = EventRule(RuleType.NONE, end = RuleEnd.Never),
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `点事件块弹详情删除后事件消失`() {
        val now = LocalTime.now()
        runBlocking {
            repository.save(
                event(
                    "e-detail",
                    "详情删除用",
                    todayAt(now.hour),
                    todayAt(now.hour).plusMinutes(60),
                    location = "会议室",
                    notes = "备注内容",
                ),
            )
        }
        rule.waitForIdle()

        rule.onNodeWithTag("event_block_e-detail").performClick()
        rule.waitForIdle()

        // 详情卡展示字段(spec §3.6)
        rule.onAllNodesWithText("详情删除用").onFirst().assertExists()
        rule.onAllNodesWithText("会议室").onFirst().assertExists()
        rule.onAllNodesWithText("备注内容").onFirst().assertExists()
        rule.onAllNodesWithText("P1").onFirst().assertExists()
        rule.onAllNodesWithText("不重复").onFirst().assertExists()

        // 删除 → 确认弹窗 → 周视图即时消失(Flow 自动)
        rule.onNodeWithTag("detail_delete").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("delete_confirm").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("event_block_e-detail").fetchSemanticsNodes().isEmpty()
        }
        // 当周已无事件:空态回归(文案尾部有 emoji,用子串匹配);库里只剩墓碑(软删除,spec §4.5)
        rule.onAllNodesWithText("今天没有日程", substring = true).onFirst().assertExists()
        assertEquals(1, runBlocking { dao.allOnce().count { it.isDeleted } })
    }

    @Test
    fun `详情点编辑改标题保存后周视图更新`() {
        val now = LocalTime.now()
        runBlocking {
            repository.save(event("e-edit", "编辑前标题", todayAt(now.hour), todayAt(now.hour).plusMinutes(60)))
        }
        rule.waitForIdle()
        val createdBefore = runBlocking { dao.allOnce().first().createdAt }

        rule.onNodeWithTag("event_block_e-edit").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("detail_edit").performClick()
        rule.waitForIdle()

        // 编辑页载入了既有事件
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("field_title").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("field_title").assertTextContains("编辑前标题")

        rule.onNodeWithTag("field_title").performTextReplacement("编辑后标题")
        rule.onNodeWithTag("save_button").performClick()
        // 保存成功 = 编辑页自己关掉(标题为空时不会关,见 Task 4 的裁决)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("save_button").fetchSemanticsNodes().isEmpty()
        }
        // 编辑器已关,周视图里的块更新为新标题(Room 异步发射,轮询等)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("编辑后标题").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("编辑前标题").assertDoesNotExist()
        val rows = runBlocking { dao.allOnce() }
        assertEquals(1, rows.size)
        assertEquals("编辑后标题", rows.first().title)
        // 编辑不得重写创建时间(仓库契约;终审发现草稿曾把 createdAt 丢成 0 导致每次编辑被当新建)
        assertEquals(createdBefore, rows.first().createdAt)
        assertTrue(rows.first().updatedAt >= createdBefore)
    }

    @Test
    fun `保存与已有事件重叠时出现轻提示且不阻止保存`() {
        // 占满今天的既有事件:无论 FAB 预填落在哪个半点都必然重叠
        val day = LocalDate.now()
        runBlocking {
            repository.save(event("e-blocker", "占满今天的事", day.atStartOfDay(), day.atTime(23, 59)))
        }
        rule.waitForIdle()

        rule.onNodeWithTag("fab_create").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("field_title").performTextInput("重叠测试")
        rule.onNodeWithTag("save_button").performClick()

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("与\"占满今天的事\"时间重叠").fetchSemanticsNodes().isNotEmpty()
        }
        // 轻提示不阻止保存:编辑页关闭,事件照常落库并出现在周视图
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("save_button").fetchSemanticsNodes().isEmpty()
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("重叠测试").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, runBlocking { dao.allOnce().size })
    }
}
