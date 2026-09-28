package com.lnx.app.feature.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
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
        val nextWeek = mondayOf(LocalDate.now().plusWeeks(1))
        rule.onNodeWithTag("week_pager").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("week_header_${nextWeek}").assertExists()
        rule.onNodeWithTag("week_pager").performTouchInput { swipeRight() }
    }
}
