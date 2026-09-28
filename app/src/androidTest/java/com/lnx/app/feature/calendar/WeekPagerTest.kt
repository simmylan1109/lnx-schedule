package com.lnx.app.feature.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.common.formatTitle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WeekPagerTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    // 注:testTag 为 week_header_{ISO 周一日期},故用 with(MONDAY) 而非 formatTitle
    private fun mondayOf(date: LocalDate) = date.with(DayOfWeek.MONDAY)

    // 注:测试方法名不得含空格(minSdk 26 → DEX 037,D8 拒绝 SimpleName 中的空格)
    @Test
    fun `左滑翻到下周右滑翻回`() {
        val today = LocalDate.now()
        val thisMonday = mondayOf(today)
        val nextMonday = mondayOf(today.plusWeeks(1))
        // 左滑:头部与顶栏标题都应切到下周一(spec §3.2 翻页 → 选中日 = 该周周一)
        rule.onNodeWithTag("week_pager").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("week_header_$nextMonday").assertExists()
        rule.onNodeWithText(formatTitle(nextMonday)).assertExists()
        // 右滑回到本周:选中日回到该周周一(今天恰为周一时等于 formatTitle(今天))
        rule.onNodeWithTag("week_pager").performTouchInput { swipeRight() }
        rule.onNodeWithTag("week_header_$thisMonday").assertExists()
        rule.onNodeWithText(formatTitle(thisMonday)).assertExists()
    }

    // 冷启动(spec §3.1):无任何交互时标题必须是"今天",不能被改写成本周周一
    @Test
    fun `冷启动标题为今天且不翻页`() {
        val today = LocalDate.now()
        rule.onNodeWithTag("week_pager").assertExists()
        rule.onNodeWithTag("week_header_${mondayOf(today)}").assertExists()
        rule.onNodeWithText(formatTitle(today)).assertExists()
    }
}
