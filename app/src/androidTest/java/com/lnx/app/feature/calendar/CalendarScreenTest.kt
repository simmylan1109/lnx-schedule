package com.lnx.app.feature.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.common.formatTitle
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
        rule.onNodeWithText("周视图将在本里程碑内实现").assertExists()
        rule.onNodeWithText("月视图将在后续里程碑提供").assertDoesNotExist()
    }

    @Test
    fun `点击日Tab显示日占位再点周Tab回到周视图`() {
        rule.onNodeWithTag("tab_DAY").performClick()
        rule.onNodeWithText("日视图将在后续里程碑提供").assertExists()
        rule.onNodeWithTag("tab_WEEK").performClick()
        rule.onNodeWithText("周视图将在本里程碑内实现").assertExists()
    }

    @Test
    fun `点击月Tab显示月占位再点周Tab回到周视图`() {
        rule.onNodeWithTag("tab_MONTH").performClick()
        rule.onNodeWithText("月视图将在后续里程碑提供").assertExists()
        rule.onNodeWithTag("tab_WEEK").performClick()
        rule.onNodeWithTag("week_grid").assertExists()
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
