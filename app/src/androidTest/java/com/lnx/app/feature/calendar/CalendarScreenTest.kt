package com.lnx.app.feature.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.common.formatTitle
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `默认周视图且标题存在`() {
        rule.onNodeWithTag("top_bar").assertExists()
        rule.onNodeWithTag("tab_WEEK").assertExists()
        // 不点击任何 tab,钉住初始 viewMode == WEEK
        // Task 5 起 WEEK 分支由 WeekView 承接(周占位文案已按计划移除)
        rule.onNodeWithTag("week_pager").assertExists()
        rule.onNodeWithText("月视图将在后续里程碑提供").assertDoesNotExist()
    }

    @Test
    fun `点击日Tab显示日占位再点周Tab回到周视图`() {
        rule.onNodeWithTag("tab_DAY").performClick()
        rule.onNodeWithText("日视图将在后续里程碑提供").assertExists()
        rule.onNodeWithTag("tab_WEEK").performClick()
        rule.onNodeWithTag("week_pager").assertExists()
        rule.onNodeWithText("日视图将在后续里程碑提供").assertDoesNotExist()
    }

    @Test
    fun `点击月Tab显示月占位再点周Tab回到周视图`() {
        rule.onNodeWithTag("tab_MONTH").performClick()
        rule.onNodeWithText("月视图将在后续里程碑提供").assertExists()
        rule.onNodeWithTag("tab_WEEK").performClick()
        rule.onNodeWithTag("week_grid").assertExists()
    }

    // 翻到非当前周后点"今天":当前周锚点规则应让标题回到今天
    @Test
    fun `翻到下周后点今天回到今天`() {
        val today = LocalDate.now()
        val todayTitle = formatTitle(today)
        val nextMonday = today.plusWeeks(1).with(DayOfWeek.MONDAY)
        rule.onNodeWithTag("week_pager").performTouchInput { swipeLeft() }
        rule.onNodeWithText(formatTitle(nextMonday)).assertExists()
        rule.onNodeWithTag("today_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(todayTitle).assertExists()
    }

    // WEEK 分支离开组合会销毁 pagerState,重挂载时不得把"今天"改写成本周周一
    @Test
    fun `点击今天后切日Tab再回周Tab标题仍是今天`() {
        val todayTitle = formatTitle(LocalDate.now())
        rule.onNodeWithTag("today_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(todayTitle).assertExists()
        rule.onNodeWithTag("tab_DAY").performClick()
        rule.onNodeWithTag("tab_WEEK").performClick()
        rule.onNodeWithText(todayTitle).assertExists()
    }

    // 注:测试方法名不得含空格(minSdk 26 → DEX 037,D8 拒绝 SimpleName 中的空格)
    @Test
    fun `标题跟随选中日期_点击今天按钮回到今天`() {
        val todayTitle = formatTitle(LocalDate.now())
        rule.onNodeWithTag("top_bar").assertExists()
        rule.onNodeWithText(todayTitle).assertExists()
        rule.onNodeWithTag("today_button").performClick()
        rule.waitForIdle()
        // 钉住 CalendarScreen 中 formatTitle(state.selectedDate) 的接线
        rule.onNodeWithText(todayTitle).assertExists()
    }
}
