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
 * 用 `ActivityScenario.launch(intent)` 带 extra 启动,等价于系统点通知的路径。
 * 这里**不能**用 `createAndroidComposeRule<MainActivity>` + 再 startActivity:
 * 规则自己持有一个 ActivityScenario,另起一个 Activity 会让它收尾时等不到 DESTROYED
 * (实测报 "Activity never becomes requested state [DESTROYED]"),失败原因跟被测逻辑无关。
 *
 * 这条测试是被一次真 bug 逼出来的:CalendarScreen 里两个 LaunchedEffect 被合并成一个后,
 * openTarget 读到的是初始 null,详情卡永远不弹 —— 而当时仪器测试一条都没覆盖这条路径。
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

    @Test
    fun 带事件extra的intent打开那次发生的详情卡() {
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
    fun 指向已删除事件的intent不崩也不弹卡() {
        ActivityScenario.launch<MainActivity>(intentFor("不存在的事件")).use {
            // 什么都不该发生:主界面照常在,详情卡不弹
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag("fab_create").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(0, rule.onAllNodesWithTag("detail_edit").fetchSemanticsNodes().size)
        }
    }
}
