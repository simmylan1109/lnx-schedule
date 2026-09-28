package com.lnx.app.feature.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
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
    }

    @Test
    // 注:brief 原文方法名含空格,D8 在 minSdk 26(DEX 037)下拒绝 SimpleName 中的空格,故去掉空格
    fun `点击月Tab显示月占位再点周Tab回到周视图`() {
        rule.onNodeWithTag("tab_MONTH").performClick()
        rule.onNodeWithText("月视图将在后续里程碑提供").assertExists()
        rule.onNodeWithTag("tab_WEEK").performClick()
        rule.onNodeWithTag("week_grid").assertExists()
    }

    @Test
    fun `点击今天按钮不崩溃`() {
        rule.onNodeWithTag("today_button").performClick()
        rule.onNodeWithTag("top_bar").assertExists()
    }
}
