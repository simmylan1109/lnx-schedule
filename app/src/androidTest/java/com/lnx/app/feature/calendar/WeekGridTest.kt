package com.lnx.app.feature.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WeekGridTest {
    // 见 CalendarScreenTest:Hilt 规则必须先于 Compose 规则
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @Test
    fun `网格与刻度存在`() {
        // time_grid 挂在时间轴自身(week_grid 是含表头的 pager 容器,只能证明"周视图在")
        rule.onNodeWithTag("time_grid").assertExists()
        // 注:必须 assertIsDisplayed —— assertExists 只证明节点在树里,performScrollTo 在
        // maxValue==0 时会空转通过;只有"真的被滚进可视区"才证明滚动容器可用。
        // 用 onAllNodes + onFirst:pager 滑动中会组合相邻页,刻度文案可能出现 2 份
        rule.onAllNodesWithText("9:00").onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `当前时刻线与左端圆点存在`() {
        // 同样用 onAllNodes:滑动中相邻页各有一套红线
        rule.onAllNodesWithTag("now_line").onFirst().assertExists()
        rule.onAllNodesWithTag("now_dot").onFirst().assertExists()
    }

    @Test
    fun `空状态文案显示`() {
        // 末尾的装饰图标已从 emoji 换成 Canvas 画的小太阳(spec §5.3),文案本身不再带符号
        rule.onNodeWithText("今天没有日程,享受自由时光", substring = true).assertExists()
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
