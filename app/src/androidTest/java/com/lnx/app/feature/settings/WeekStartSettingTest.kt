package com.lnx.app.feature.settings

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.common.weekStartOf
import com.lnx.app.core.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 周起始日设置真的影响视图(spec §3.11 ①)。
 *
 * **这条是 M6 验收走查抓出来的洞**:设置项能存能读、页面上也能点,但周视图/月视图
 * 从来没消费它 —— 用户改成"周日"之后日历纹丝不动。单元测试只覆盖到"存了没"(数据层),
 * 只有端到端断言"表头首位变了"才守得住这条链路。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WeekStartSettingTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    /** 当前周视图页的起始日(表头 testTag 里带 ISO 日期) */
    private fun visibleWeekStart(): LocalDate? {
        val today = LocalDate.now()
        // 页锚点 = 本周起始日;两种设置下各试一次,谁存在就是当前生效的那个。
        // 注意用生产同款 weekStartOf 算期望值:不能手写 today.with(DayOfWeek.SUNDAY)
        // —— 那是"本周(周一起算)里的周日"(对周三会给出之后的周日),
        // 与"周日起始时本周的周首"不是一回事(这个坑 DateFormatter 与我一版测试各踩过一次)
        val mondayBased = weekStartOf(today, mondayFirst = true)
        val sundayBased = weekStartOf(today, mondayFirst = false)
        return listOf(mondayBased, sundayBased).firstOrNull { candidate ->
            rule.onAllNodesWithTag("week_header_$candidate").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun 改成周日起始后周视图的表头首位变成周日() {
        val today = LocalDate.now()
        // 出厂是周一
        rule.waitUntil(timeoutMillis = 10_000) {
            visibleWeekStart() == weekStartOf(today, mondayFirst = true)
        }

        rule.onNodeWithTag("menu_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_settings").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("week_1").performScrollTo().performClick() // week_1 = 周日
        rule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { settings.current().weekStartMonday } == false
        }

        rule.onNodeWithTag("settings_back").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            visibleWeekStart() == weekStartOf(today, mondayFirst = false)
        }
        assertEquals(
            "设置成周日起始后,周视图第一格应是周日",
            weekStartOf(today, mondayFirst = false),
            visibleWeekStart(),
        )
    }
}
