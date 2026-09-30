package com.lnx.app.feature.calendar

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.common.LnxLocale
import com.lnx.app.core.common.formatTitle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WeekPagerTest {
    // 见 CalendarScreenTest:Hilt 规则必须先于 Compose 规则
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun inject() {
        hiltRule.inject()
    }

    // 注:testTag 为 week_header_{ISO 周一日期},故用 with(MONDAY) 而非 formatTitle
    private fun mondayOf(date: LocalDate) = date.with(DayOfWeek.MONDAY)

    // 注:测试方法名不得含空格(minSdk 26 → DEX 037,D8 拒绝 SimpleName 中的空格)
    @Test
    fun `左滑翻到下周右滑翻回`() {
        val today = LocalDate.now()
        val thisMonday = mondayOf(today)
        val nextMonday = mondayOf(today.plusWeeks(1))
        // 左滑到非当前周:头部切到下周一,选中日锚定为该周周一
        rule.onNodeWithTag("week_pager").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("week_header_$nextMonday").assertExists()
        rule.onNodeWithText(formatTitle(nextMonday, LnxLocale.resolve(LnxLocale.SYSTEM))).assertExists()
        // 右滑回到当前周:锚点规则要求回到"今天",而不是该周周一
        rule.onNodeWithTag("week_pager").performTouchInput { swipeRight() }
        rule.onNodeWithTag("week_header_$thisMonday").assertExists()
        rule.onNodeWithText(formatTitle(today, LnxLocale.resolve(LnxLocale.SYSTEM))).assertExists()
    }

    // 冷启动(spec §3.1):无任何交互时标题必须是"今天",不能被改写成本周周一
    @Test
    fun `冷启动标题为今天且不翻页`() {
        val today = LocalDate.now()
        rule.onNodeWithTag("week_pager").assertExists()
        rule.onNodeWithTag("week_header_${mondayOf(today)}").assertExists()
        rule.onNodeWithText(formatTitle(today, LnxLocale.resolve(LnxLocale.SYSTEM))).assertExists()
    }
}
