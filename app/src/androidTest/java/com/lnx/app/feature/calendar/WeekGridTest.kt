package com.lnx.app.feature.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekGridTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `网格与刻度存在`() {
        rule.onNodeWithTag("week_grid").assertExists()
        rule.onNodeWithText("9:00").performScrollTo().assertExists()
    }

    @Test
    fun `当前时刻线存在`() {
        rule.onNodeWithTag("now_line").assertExists()
    }

    @Test
    fun `空状态文案显示`() {
        rule.onNodeWithText("今天没有日程,享受自由时光 🌤", substring = true).assertExists()
    }
}
