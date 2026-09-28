package com.lnx.app.feature.calendar.week

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lnx.app.core.common.dateToPage
import com.lnx.app.core.common.pageToDate
import com.lnx.app.feature.calendar.CalendarUiState
import java.time.DayOfWeek
import java.time.LocalDate

private val DOW_HEADER = listOf("一", "二", "三", "四", "五", "六", "日")

fun dayOfWeekCnShort(dow: DayOfWeek): String = DOW_HEADER[dow.value - 1]

@Composable
fun WeekView(
    state: CalendarUiState,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = dateToPage(state.selectedDate),
        pageCount = { 40001 },
    )
    // 选中日期变化(如"今天"按钮)时同步翻页
    // 注:M1 接受"今天"按钮在本周时不发射(值相同)的合流限制,故当前周内不滚动
    LaunchedEffect(state.selectedDate) {
        val target = dateToPage(state.selectedDate)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    // 翻页 → 选中日 = 该周周一
    LaunchedEffect(pagerState.currentPage) {
        onSelectDate(pageToDate(pagerState.currentPage))
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.testTag("week_pager"),
    ) { page ->
        val weekStart = pageToDate(page)
        Column(modifier = Modifier.testTag("week_header_${weekStart}")) {
            WeekHeader(weekStart = weekStart, selectedDate = state.selectedDate, today = today)
            Box(modifier = Modifier.weight(1f).testTag("week_grid")) // Task 6 起由 TimeGrid 承接此 tag
        }
    }
}

@Composable
private fun WeekHeader(
    weekStart: LocalDate,
    selectedDate: LocalDate,
    today: LocalDate,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        (0..6).forEach { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val isToday = date == today
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dayOfWeekCnShort(date.dayOfWeek),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (date == selectedDate) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = if (isToday) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
