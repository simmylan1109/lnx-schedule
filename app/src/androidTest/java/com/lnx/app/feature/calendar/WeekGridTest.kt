package com.lnx.app.feature.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
        // 注:必须 assertIsDisplayed —— assertExists 只证明节点在树里,performScrollTo 在
        // maxValue==0 时会空转通过;只有"真的被滚进可视区"才证明滚动容器可用。
        // 用 onAllNodes + onFirst:pager 会组合相邻页,刻度文案可能有 2 份(见下方用例注释)
        rule.onAllNodesWithText("9:00").onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `当前时刻线存在`() {
        rule.onNodeWithTag("now_line").assertExists()
    }

    @Test
    fun `空状态文案显示`() {
        rule.onNodeWithText("今天没有日程,享受自由时光 🌤", substring = true).assertExists()
    }

    // 滚动可滚性:把远离当前时刻的底部刻度(23:00)滚进可视区。
    // 这条断言在容器无法滚动(maxValue==0)时会失败,因此真正证明了"时间轴可滚"。
    // 不用真实手势:本机上 performTouchInput { swipeUp() } 会让整类测试挂起
    // (Starting 4 tests / 0/4 completed,CPU 归零);真实拖拽的手感验证放在 Task 8 模拟器人工走查。
    // 注:小时刻度文案在语义树里可能有 2 份 —— HorizontalPager 会组合相邻页,每页各有一套刻度
    // (这正是 week_grid 不能挂页面内的原因),故用 onAllNodes 而非 onNodeWithText。
    @Test
    fun `时间轴可滚动`() {
        rule.onAllNodesWithText("23:00").onFirst().performScrollTo().assertIsDisplayed()
    }
}
