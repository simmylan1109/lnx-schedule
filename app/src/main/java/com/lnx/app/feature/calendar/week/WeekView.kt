package com.lnx.app.feature.calendar.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lnx.app.core.common.dateToPage
import com.lnx.app.core.common.pageToDate
import com.lnx.app.feature.calendar.CalendarUiState
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

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
    // 首次组合不算"用户翻页":跳过首次发射,避免把 spec §3.1 的"进入周视图显示当前日期"
    // 改写成本周周一(今天非周一时标题/高亮会被抹掉;WEEK 分支重挂载同样受益)
    val userPaged = remember { mutableStateOf(false) }
    // 选中日期变化(如"今天"按钮)时同步翻页
    // 注:M1 接受"今天"按钮在本周时不发射(值相同)的合流限制,故当前周内不滚动
    LaunchedEffect(state.selectedDate) {
        val target = dateToPage(state.selectedDate)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    // 翻页 → 选中日锚点(仅用户驱动):当前周锚定"今天",其他周锚定"该周周一"
    LaunchedEffect(pagerState.currentPage) {
        if (userPaged.value) {
            val weekStart = pageToDate(pagerState.currentPage)
            val weekEnd = weekStart.plusDays(6)
            val anchor = if (!today.isBefore(weekStart) && !today.isAfter(weekEnd)) today else weekStart
            onSelectDate(anchor)
        } else {
            userPaged.value = true
        }
    }

    // 决策项:week_grid 挂在 pager 容器上(而非页面内部),避免滑动/动画期组合相邻页时命中 2 个节点。
    // 注:SemanticsProperties.TestTag 是单值属性,同一语义节点无法同时承载 week_pager + week_grid
    // (后写的 testTag 覆盖先写的),故用外层 Box 作为"pager 容器"承载 week_grid,两个 tag 各命中唯一节点。
    Box(modifier = modifier.testTag("week_grid")) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("week_pager"),
        ) { page ->
            val weekStart = pageToDate(page)
            Column(modifier = Modifier.testTag("week_header_${weekStart}")) {
                WeekHeader(weekStart = weekStart, selectedDate = state.selectedDate, today = today)
                AllDayStrip()
                TimeGrid(
                    selectedDate = state.selectedDate,
                    today = today,
                    modifier = Modifier.weight(1f),
                )
            }
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
    ) {
        // 前导占位 = TimeGrid 的刻度列宽(TimeGrid 把宽度扣掉 GUTTER_WIDTH 后再 7 等分),
        // 不留这个占位,星期头会整体左移半个刻度列,首列漂移最大
        Spacer(Modifier.width(GUTTER_WIDTH))
        (0..6).forEach { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val isToday = date == today
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
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
                            // 非选中日用透明:Material You 下 surface 与 background 有细微差异,
                            // 填色会让 7 个非今天格都显出浅色圆圈
                            if (date == selectedDate) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        // 边框必须条件性挂载:border(0.dp) 在本 Compose 版本仍会画出 1px 发丝圆环
                        .then(
                            if (isToday) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                )
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
    }
}

@Composable
private fun AllDayStrip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp),
    ) // M2 渲染全天/跨天事件块
}

private val HOUR_HEIGHT = 56.dp
private val GUTTER_WIDTH = 44.dp

@Composable
private fun TimeGrid(
    selectedDate: LocalDate,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val nowState = remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowState.value = LocalTime.now()
        }
    }
    // 打开时滚到当前时刻位于上部约 1/3 处(spec §3.2)
    val hourPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val nowFraction = (nowState.value.hour + nowState.value.minute / 60f) / 24f
            val target = (nowFraction * 24 * hourPx - scrollState.maxValue / 3f)
                .toInt().coerceIn(0, scrollState.maxValue)
            scrollState.scrollTo(target)
        }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState),
        ) {
            // 左侧刻度列:标签右对齐并留 6dp 末距,贴着网格左缘(左对齐会让首位数字被屏幕边缘切掉)
            Box(modifier = Modifier.width(GUTTER_WIDTH)) {
                repeat(24) { hour ->
                    Text(
                        text = "$hour:00",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .width(GUTTER_WIDTH)
                            .padding(end = 6.dp)
                            .offset(y = (hour * HOUR_HEIGHT.value - 6).dp),
                    )
                }
            }
            // 7 天列 + 小时横线 + 当前线
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(HOUR_HEIGHT * 24),
            ) {
                // 注:Canvas 的 onDraw 是 DrawScope(非 @Composable),颜色须在组合期取出;
                // 且必须 fillMaxSize —— Spacer 默认宽度包裹为 0,fillMaxHeight 会让 size.width=0
                // (横向刻度线退化成长度 0,纵向列线全部重叠在 x=0)
                val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val hourPxDraw = HOUR_HEIGHT.toPx()
                    repeat(25) { i ->
                        drawLine(
                            color = gridLineColor,
                            start = androidx.compose.ui.geometry.Offset(0f, i * hourPxDraw),
                            end = androidx.compose.ui.geometry.Offset(size.width, i * hourPxDraw),
                            strokeWidth = 1f,
                        )
                    }
                    val colWidth = size.width / 7f
                    repeat(8) { i ->
                        drawLine(
                            color = gridLineColor,
                            start = androidx.compose.ui.geometry.Offset(i * colWidth, 0f),
                            end = androidx.compose.ui.geometry.Offset(i * colWidth, size.height),
                            strokeWidth = 1f,
                        )
                    }
                }
                val now = nowState.value
                val nowY = HOUR_HEIGHT * (now.hour + now.minute / 60f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = nowY)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.error)
                        .testTag("now_line"),
                )
            }
        }
        // 空状态(spec §3.14;M1 恒为空)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (selectedDate == today) "今天没有日程,享受自由时光 🌤"
                else "这天没有日程,享受自由时光 🌤",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
