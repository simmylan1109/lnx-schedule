package com.lnx.app.feature.calendar.week

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.common.dateToPage
import com.lnx.app.core.common.pageToDate
import com.lnx.app.feature.calendar.CalendarUiState
import com.lnx.app.feature.calendar.ViewMode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 直接组合 [WeekView],用**固定的非周一日期**回归"首次组合不得改写选中日期"。
 * 端到端用例(CalendarScreenTest/WeekPagerTest)依赖 LocalDate.now(),在今天恰为周一时
 * 无法暴露该缺陷,故在此用 2026-09-30(周三)做与真实时钟无关的钉子。
 */
@RunWith(AndroidJUnit4::class)
class WeekViewSelectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val wednesday = LocalDate.of(2026, 9, 30) // 周三,非周一

    private fun setWeekView(onSelectDate: (LocalDate) -> Unit) {
        rule.setContent {
            WeekView(
                state = CalendarUiState(selectedDate = wednesday, viewMode = ViewMode.WEEK),
                today = wednesday,
                onSelectDate = onSelectDate,
            )
        }
    }

    @Test
    fun `首次组合不改写选中日期`() {
        var emitted: LocalDate? = null
        setWeekView { emitted = it }
        rule.waitForIdle()
        assertNull("首次组合不应发射 onSelectDate(spec §3.1:进入周视图显示当前日期)", emitted)
    }

    @Test
    fun `用户左滑后发射下周一`() {
        val start = pageToDate(dateToPage(wednesday))
        var emitted: LocalDate? = null
        setWeekView { emitted = it }
        rule.onNodeWithTag("week_pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals("左滑一整页后选中日应为下周周一", start.plusWeeks(1), emitted)
    }
}
