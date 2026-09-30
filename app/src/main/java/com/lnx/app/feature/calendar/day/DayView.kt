package com.lnx.app.feature.calendar.day

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lnx.app.core.common.LocalLnxLocale
import com.lnx.app.core.common.LnxLocale
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.search.HighlightTarget
import com.lnx.app.feature.calendar.CalendarUiState
import com.lnx.app.feature.calendar.week.AllDayStrip
import com.lnx.app.feature.calendar.week.TimeGrid
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * 这条发生是否落在指定日期上(含跨天)。
 * 结束为零点是排他存储,不占当天(与 AllDaySpan 同一约定)。
 */
internal fun Occurrence.touchesDay(date: LocalDate): Boolean {
    val lastCovered =
        if (end.toLocalTime() == LocalTime.MIDNIGHT) end.toLocalDate().minusDays(1) else end.toLocalDate()
    return !start.toLocalDate().isAfter(date) && !lastCovered.isBefore(date)
}

/** 日分页锚点:与周 pager 同锚(1970-01-05 周一),页 = 天 */
private val DAY_PAGE_EPOCH = LocalDate.of(1970, 1, 5).toEpochDay()

/** 400 年完整周期(±200 年),与周 pager 的 PAGE_COUNT 同量级 */
private const val DAY_PAGE_COUNT = 146_097

/** 日期条以今天为中心各覆盖 STRIP_SPAN_DAYS 天 */
private const val STRIP_SPAN_DAYS = 370

/**
 * 日视图(spec §3.3):与周视图同款时间轴、单列显示;
 * 顶部日期条可点选,左右滑切换前后一天。
 */
@Composable
fun DayView(
    state: CalendarUiState,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    occurrences: List<Occurrence> = emptyList(),
    onEventClick: (Occurrence) -> Unit = {},
    onEmptySlotClick: (LocalDateTime) -> Unit = {},
    /** 搜索跳转后的高亮(spec §3.9) */
    highlight: HighlightTarget? = null,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLnxLocale.current
    val pageOf = { date: LocalDate -> (date.toEpochDay() - DAY_PAGE_EPOCH).toInt() }
    val pagerState = rememberPagerState(
        initialPage = pageOf(state.selectedDate),
        pageCount = { DAY_PAGE_COUNT },
    )
    // 与 WeekView 相同的锚点协议:首次组合不算用户翻页,程序化选择 → 翻页,用户滑动 → 回写选择
    val userPaged = remember { mutableStateOf(false) }
    LaunchedEffect(state.selectedDate) {
        val target = pageOf(state.selectedDate)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    LaunchedEffect(pagerState.currentPage) {
        if (userPaged.value) {
            onSelectDate(LocalDate.ofEpochDay(DAY_PAGE_EPOCH + pagerState.currentPage))
        } else {
            userPaged.value = true
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag("day_grid")) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("day_pager"),
        ) { page ->
            val date = LocalDate.ofEpochDay(DAY_PAGE_EPOCH + page)
            // 查询窗口是 ±1 天,判空和画条带都只看当天,否则"昨天有事件"会盖掉今天的空态
            val dayOccurrences = occurrences.filter { it.touchesDay(date) }
            Column(modifier = Modifier.fillMaxSize()) {
                DayStrip(
                    selectedDate = state.selectedDate,
                    today = today,
                    onSelectDate = onSelectDate,
                    locale = locale,
                )
                // 全天/跨天事件(spec §3.3"其余交互与周视图一致"):日视图也得有,
                // 否则全天事件所在那天整页空白,连空态都不出现
                AllDayStrip(
                    occurrences = dayOccurrences,
                    weekStart = date,
                    onEventClick = onEventClick,
                    dayCount = 1,
                    highlight = highlight,
                )
                TimeGrid(
                    selectedDate = state.selectedDate,
                    today = today,
                    weekStart = date,
                    occurrences = occurrences,
                    onEventClick = onEventClick,
                    onEmptySlotClick = onEmptySlotClick,
                    dayCount = 1,
                    emptyCheck = dayOccurrences,
                    highlight = highlight,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 顶部横向日期条(spec §3.3):一小格一天,可点选;以今天为中心 ±370 天。
 * 左右滑动由 LazyRow 自带(条内横滑 ≠ 翻日,翻日由时间轴区的横滑负责)。
 */
@Composable
private fun DayStrip(
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    locale: Locale,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedDate) {
        // 选中变化时把该格滚进视野;索引 = 相对今天的天数偏移
        val index = (selectedDate.toEpochDay() - today.toEpochDay() + STRIP_SPAN_DAYS)
            .toInt()
            .coerceIn(0, STRIP_SPAN_DAYS * 2)
        listState.animateScrollToItem(index.coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("day_strip"),
    ) {
        items(count = STRIP_SPAN_DAYS * 2 + 1) { offset ->
            val date = today.plusDays((offset - STRIP_SPAN_DAYS).toLong())
            DayStripCell(
                date = date,
                selected = date == selectedDate,
                isToday = date == today,
                onClick = { onSelectDate(date) },
                locale = locale,
            )
        }
    }
}

@Composable
private fun DayStripCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    locale: Locale,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("day_cell_$date"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = LnxLocale.weekday(date, locale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .then(
                    if (isToday) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
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
