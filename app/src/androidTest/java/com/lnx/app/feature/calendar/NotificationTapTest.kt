package com.lnx.app.feature.calendar

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lnx.app.MainActivity
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.notification.toEpochMillis
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 点提醒通知 → 打开该次发生的详情卡(spec §3.8)。
 *
 * 三条测试覆盖两种真实路径:
 * - **冷启动**:系统点通知时进程不在 → `ActivityScenario.launch(intent)` 直接带 extra 启动;
 * - **已启动**:App 在后台、点通知 → Activity 是 `singleTask`,走 `onNewIntent`(最常见的情形);
 * - 反面:指向已不存在的事件时,详情卡**不能**开(且要先等一个正信号,否则否定断言恒真)。
 *
 * 这组测试是被一次真 bug 逼出来的:CalendarScreen 里两个 LaunchedEffect 被合并成一个后,
 * openTarget 读到的是初始 null,详情卡永远不弹 —— 而当时仪器测试一条都没覆盖这条路径。
 *
 * 写法上**不能**用 `createAndroidComposeRule<MainActivity>` + 再 startActivity:
 * 规则自持一个 ActivityScenario,另起一个 Activity 会让它收尾时等不到 DESTROYED。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NotificationTapTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    @Inject
    lateinit var repository: EventRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private val title = "通知跳转测试"

    /** 今天 10:00 起半小时(按整天窗口查询,当天几点建都能查到) */
    private val start = LocalDate.now().atTime(10, 0)

    private fun seed() {
        runBlocking {
            repository.save(
                Event(
                    id = "tap-target",
                    title = title,
                    allDay = false,
                    start = start,
                    end = start.plusMinutes(30),
                    location = null,
                    notes = null,
                    colorSlot = 0,
                    priority = Priority.P2,
                    reminderLeadMinutes = 15,
                    rule = EventRule(),
                    createdAt = 1_000L,
                    updatedAt = 0L,
                ),
            )
        }
    }

    private fun intentFor(eventId: String) = Intent(
        InstrumentationRegistry.getInstrumentation().targetContext,
        MainActivity::class.java,
    )
        .putExtra("event_id", eventId)
        .putExtra("occurrence_start", start.toEpochMillis())

    /** 等日历把该次发生渲染出来 —— 这是"数据已经查完"的正信号,否定断言才有意义 */
    private fun waitForEventBlock() {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("event_block_tap-target").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    @Test
    fun 冷启动时带extra的intent打开那次发生的详情卡() {
        seed()

        ActivityScenario.launch<MainActivity>(intentFor("tap-target")).use {
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag("detail_edit").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, rule.onAllNodesWithTag("detail_edit").fetchSemanticsNodes().size)
            // 标题会出现两次(详情卡 + 背后周视图的事件块),这里只要求"看得到"
            assertTrue(
                rule.onAllNodesWithText(title, substring = true).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun 已启动时点通知打开详情卡走的是onNewIntent同一段代码() {
        // 最常见的路径:App 在后台,点通知唤醒。Activity 是 singleTask,不重建。
        // Activity.onNewIntent 是受保护的、测试碰不到,所以直接调它内部的 handleOpenRequest
        // —— 那正是 onNewIntent 全部的行为。
        seed()

        ActivityScenario.launch<MainActivity>(Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java,
        )).use { scenario ->
            waitForEventBlock()
            assertEquals(
                "启动时不该有详情卡",
                0,
                rule.onAllNodesWithTag("detail_edit").fetchSemanticsNodes().size,
            )
            scenario.onActivity { it.handleOpenRequest(intentFor("tap-target")) }


            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag("detail_edit").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, rule.onAllNodesWithTag("detail_edit").fetchSemanticsNodes().size)
        }
    }

    @Test
    fun 指向已删除事件的intent不崩也不弹卡() {
        seed()

        ActivityScenario.launch<MainActivity>(intentFor("不存在的事件")).use {
            // 先等到同一窗口里的真实事件渲染出来:这说明日历的数据查询已经跑完一轮,
            // 之后再断言"没弹卡"才是有效断言(否则否定断言在查询返回前就执行完,恒真)
            waitForEventBlock()
            assertEquals(
                "查询已跑完,详情卡仍不该出现",
                0,
                rule.onAllNodesWithTag("detail_edit").fetchSemanticsNodes().size,
            )
        }
    }
}
