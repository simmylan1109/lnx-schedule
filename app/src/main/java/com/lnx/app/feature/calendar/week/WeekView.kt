package com.lnx.app.feature.calendar.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lnx.app.core.common.dateToPage
import com.lnx.app.core.common.pageToDate
import com.lnx.app.core.designsystem.EventColors
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.feature.calendar.CalendarUiState
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val DOW_HEADER = listOf("一", "二", "三", "四", "五", "六", "日")

fun dayOfWeekCnShort(dow: DayOfWeek): String = DOW_HEADER[dow.value - 1]

/**
 * M2 起 WeekView 接收事件与两个点击回调。
 * 新参数全部带默认值:M1 的测试与预览只传前四个参数仍可编译。
 */
@Composable
fun WeekView(
    state: CalendarUiState,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    occurrences: List<Occurrence> = emptyList(),
    onEventClick: (Occurrence) -> Unit = {},
    onEmptySlotClick: (LocalDateTime) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = dateToPage(state.selectedDate),
        // 40001 页 ≈ 以 1970-01-05 为中心的前后各约 200 年,足够任何现实日期
        pageCount = { PAGE_COUNT },
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
    // 不变量:锚点永远落在 currentPage 所在的那一周内,因此 dateToPage(anchor) == currentPage,
    // 上面的 selectedDate→翻页 effect 不会反向触发,两个 effect 不会互相打架。
    // (M3 若加"点别周日期跳转",需重新审视这条不变量)
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
                    weekStart = weekStart,
                    occurrences = occurrences,
                    onEventClick = onEventClick,
                    onEmptySlotClick = onEmptySlotClick,
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
    ) // M3 渲染全天/跨天事件条(spec §8.1 M3 跨天多天显示;M2 只有定时段)
}

private val HOUR_HEIGHT = 56.dp
private val GUTTER_WIDTH = 44.dp

/** 周分页总数:以 1970-01-05(第 0 页)为中心,前后各约 200 年 */
private const val PAGE_COUNT = 40001

/** 点空白新建时的时间吸附粒度(spec §3.2/§3.5:吸附到 30 分钟格开头) */
private const val SNAP_MINUTES = 30

/** 一个事件在某一天的渲染块(跨天事件按天裁剪) */
private data class DayBlock(
    val occurrence: Occurrence,
    val topMinutes: Float,
    val heightMinutes: Float,
    val lane: Int,
    val lanes: Int,
)

private fun minutesFromMidnight(time: LocalDateTime): Float =
    time.hour * 60f + time.minute + time.second / 60f

/**
 * 把发生按天裁剪 + 分配车道,返回 7 天 × 每天的块列表。
 * 全天事件不进时间轴(它们在 AllDayStrip,spec §3.2);跨天事件按天切开各画一段。
 */
private fun buildDayBlocks(
    occurrences: List<Occurrence>,
    weekStart: LocalDate,
): List<List<DayBlock>> = (0..6).map { dayIndex ->
    val date = weekStart.plusDays(dayIndex.toLong())
    val dayStart = date.atStartOfDay()
    val dayEnd = dayStart.plusDays(1)
    val timed = occurrences.filter { !it.event.allDay }

    val clipped = timed.mapNotNull { occ ->
        val start = maxOf(occ.start, dayStart)
        val end = minOf(occ.end, dayEnd)
        if (start >= end) null else occ to (start to end)
    }
    val spans = clipped.map { (occ, range) ->
        TimeSpan(
            id = "${occ.event.id}@$date@${occ.start}",
            startMinute = minutesFromMidnight(range.first),
            endMinute = minutesFromMidnight(range.second),
        )
    }
    val occurrenceBySpanId = spans.mapIndexed { i, span -> span.id to clipped[i].first }.toMap()
    val slots = LaneLayout.assign(spans).associateBy { it.id }

    spans.mapNotNull { span ->
        val slot = slots[span.id] ?: return@mapNotNull null
        val occ = occurrenceBySpanId[span.id] ?: return@mapNotNull null
        DayBlock(
            occurrence = occ,
            topMinutes = span.startMinute,
            heightMinutes = maxOf(span.endMinute - span.startMinute, 15f),
            lane = slot.lane,
            lanes = slot.lanes,
        )
    }
}

@Composable
private fun TimeGrid(
    selectedDate: LocalDate,
    today: LocalDate,
    weekStart: LocalDate,
    occurrences: List<Occurrence>,
    onEventClick: (Occurrence) -> Unit,
    onEmptySlotClick: (LocalDateTime) -> Unit,
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
    // 打开时滚到当前时刻位于上部约 1/3 处(spec §3.2)。
    // 注意分母必须是"视口高"而非 maxValue(内容高−视口高),否则红线会落到约 47% 处。
    val hourPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }
    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            val nowFraction = (nowState.value.hour + nowState.value.minute / 60f) / 24f
            val viewportPx = 24 * hourPx - scrollState.maxValue
            val target = (nowFraction * 24 * hourPx - viewportPx / 3f)
                .toInt().coerceIn(0, scrollState.maxValue)
            scrollState.scrollTo(target)
        }
    }

    val now = nowState.value
    // 注意乘法顺序:可用的是 Dp.times(Float),不是 Float.times(Dp)
    val nowY = HOUR_HEIGHT * (now.hour + now.minute / 60f)

    Box(modifier = modifier.testTag("time_grid")) {
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
                // 当前时刻线的左端圆点(spec §3.2:细线 + 左端圆点),与红线同在滚动内容内故同步移动。
                // x 定位到刻度列右缘(即红线起点)左侧 4dp,视觉上与红线连成一体。
                Box(
                    modifier = Modifier
                        .offset(x = GUTTER_WIDTH - 12.dp, y = nowY - 4.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                        .testTag("now_dot"),
                )
            }
            // 7 天列 + 小时横线 + 事件块 + 当前线
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(HOUR_HEIGHT * 24)
                    .pointerInput(weekStart, onEmptySlotClick) {
                        // 点空白 = 新建事件(spec §3.2/§3.5):横向定位到日列,纵向吸附到 30 分钟格。
                        // 事件块是子节点且自带 clickable,会先消费点击,不会误触发这里。
                        val hourPx = HOUR_HEIGHT.toPx()
                        detectTapGestures { offset ->
                            val col = (offset.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                            val rawMinute = offset.y / hourPx * 60f
                            // spec §3.2:吸附到"所在 30 分钟格的开头"= 向下取整,
                            // 不能四舍五入(那会把每格的上半段推到下一格,一半点击报错时间)
                            // 注意括号:coerceIn 必须钳乘积,绑到常量上是 no-op
                            val minute = ((rawMinute / SNAP_MINUTES).toInt() * SNAP_MINUTES)
                                .coerceIn(0, 24 * 60 - SNAP_MINUTES)
                            onEmptySlotClick(
                                weekStart.plusDays(col.toLong())
                                    .atTime(minute / 60, minute % 60),
                            )
                        }
                    },
            ) {
                val colWidth = maxWidth / 7
                val blocks = remember(occurrences, weekStart) {
                    buildDayBlocks(occurrences, weekStart)
                }
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
                    val cw = size.width / 7f
                    repeat(8) { i ->
                        drawLine(
                            color = gridLineColor,
                            start = androidx.compose.ui.geometry.Offset(i * cw, 0f),
                            end = androidx.compose.ui.geometry.Offset(i * cw, size.height),
                            strokeWidth = 1f,
                        )
                    }
                }
                blocks.forEach { day ->
                    day.forEach { b ->
                        val w = colWidth / b.lanes
                        Box(
                            modifier = Modifier
                                .offset(x = w * b.lane, y = (b.topMinutes / 60f * HOUR_HEIGHT.value).dp)
                                .width((w - 1.dp).coerceAtLeast(1.dp))
                                .height((b.heightMinutes / 60f * HOUR_HEIGHT.value).dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(EventColors.of(b.occurrence.event.colorSlot))
                                    .clickable { onEventClick(b.occurrence) }
                                    .testTag("event_block_${b.occurrence.event.id}"),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = b.occurrence.event.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EventColors.on(b.occurrence.event.colorSlot),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                )
                                if (b.heightMinutes >= 30f) {
                                    Text(
                                        text = "%02d:%02d".format(
                                            b.occurrence.start.hour,
                                            b.occurrence.start.minute,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EventColors.on(b.occurrence.event.colorSlot)
                                            .copy(alpha = 0.75f),
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 3.dp),
                                    )
                                }
                            }
                            // P0/P1 优先级色点:叠在块右上角,1dp 描边保证在任何色位上可见
                            if (b.occurrence.event.priority == Priority.P0 ||
                                b.occurrence.event.priority == Priority.P1
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(EventColors.priorityColor(b.occurrence.event.priority))
                                        .border(1.dp, EventColors.on(b.occurrence.event.colorSlot), CircleShape),
                                )
                            }
                        }
                    }
                }
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
        // 空状态(spec §3.14):仅当这一周确实没有事件时才显示,
        // 否则会盖在事件块上面(M1 时网格恒空,这个遮罩是无害的)
        if (occurrences.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (selectedDate == today) "今天没有日程,享受自由时光 🌤"
                           else "这天没有日程,享受自由时光 🌤",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
