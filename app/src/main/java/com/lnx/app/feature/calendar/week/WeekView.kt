package com.lnx.app.feature.calendar.week

import androidx.compose.animation.fadeIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lnx.app.R
import com.lnx.app.core.common.LocalLnxLocale
import com.lnx.app.core.common.LnxLocale
import com.lnx.app.core.common.dateToPage
import com.lnx.app.core.common.pageToDate
import com.lnx.app.core.designsystem.EventColors
import com.lnx.app.core.designsystem.LocalLnxTheme
import com.lnx.app.core.designsystem.SereneHeaderBackground
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.designsystem.headerContentColor
import com.lnx.app.core.designsystem.headerSecondaryContentColor
import com.lnx.app.core.designsystem.motion
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.search.HighlightTarget
import com.lnx.app.feature.calendar.CalendarUiState
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * 星期头表(周一起始,ISO 1..7):按当前 App 语言生成,不再写死中文字面量。
 * 中文走 LnxLocale 的 周一…周日 表,英文走 `EEE`(Mon…Sun)。
 */
internal fun dowHeader(locale: Locale): List<String> =
    (1..7).map { LnxLocale.weekday(it, locale) }

fun dayOfWeekCnShort(dow: DayOfWeek, locale: Locale): String =
    dowHeader(locale)[dow.value - 1]

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
    /** spec §3.11 ①:周一起始(默认)或周日起始,来自设置 */
    weekStartMonday: Boolean = true,
    /** 搜索跳转后的高亮(spec §3.9);搜索总是跳日视图,这里是给"跳转后手动切回周视图"兜的 */
    highlight: HighlightTarget? = null,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLnxLocale.current
    val pagerState = rememberPagerState(
        initialPage = dateToPage(state.selectedDate, weekStartMonday),
        // 40001 页 ≈ 以 1970-01-05 为中心的前后各约 200 年,足够任何现实日期
        pageCount = { PAGE_COUNT },
    )
    // 首次组合不算"用户翻页":跳过首次发射,避免把 spec §3.1 的"进入周视图显示当前日期"
    // 改写成本周周一(今天非周一时标题/高亮会被抹掉;WEEK 分支重挂载同样受益)
    val userPaged = remember { mutableStateOf(false) }
    // 选中日期变化(如"今天"按钮)时同步翻页
    // 注:M1 接受"今天"按钮在本周时不发射(值相同)的合流限制,故当前周内不滚动
    LaunchedEffect(state.selectedDate, weekStartMonday) {
        val target = dateToPage(state.selectedDate, weekStartMonday)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    // 翻页 → 选中日锚点(仅用户驱动):当前周锚定"今天",其他周锚定"该周起始日"
    // 不变量:锚点永远落在 currentPage 所在的那一周内,因此 dateToPage(anchor) == currentPage,
    // 上面的 selectedDate→翻页 effect 不会反向触发,两个 effect 不会互相打架。
    // (M3 若加"点别周日期跳转",需重新审视这条不变量)
    LaunchedEffect(pagerState.currentPage, weekStartMonday) {
        if (userPaged.value) {
            val weekStart = pageToDate(pagerState.currentPage, weekStartMonday)
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
            val weekStart = pageToDate(page, weekStartMonday)
            Column(modifier = Modifier.testTag("week_header_${weekStart}")) {
                WeekHeader(
                    weekStart = weekStart,
                    selectedDate = state.selectedDate,
                    today = today,
                    locale = locale,
                )
                AllDayStrip(
                    occurrences = occurrences,
                    weekStart = weekStart,
                    onEventClick = onEventClick,
                    highlight = highlight,
                )
                TimeGrid(
                    selectedDate = state.selectedDate,
                    today = today,
                    weekStart = weekStart,
                    occurrences = occurrences,
                    onEventClick = onEventClick,
                    onEmptySlotClick = onEmptySlotClick,
                    highlight = highlight,
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
    locale: Locale,
) {
    // 星期头属于 spec §5.2 主题 4 的"头部区"(顶栏 + Tab + 星期头),与顶栏同一条渐变。
    // 非 SERENE 主题下 SereneHeaderBackground 就是普通 Box,行为不变。
    // 头部内的文字/圆圈色全部取 header*Color:SERENE 落在渐变上,得用它自己的前景色。
    val serene = LocalLnxTheme.current.slot == ThemeSlot.SERENE
    val primaryColor = if (serene) headerContentColor() else MaterialTheme.colorScheme.primary
    val selectedBg = if (serene) Color.White.copy(alpha = 0.28f)
    else MaterialTheme.colorScheme.primaryContainer
    SereneHeaderBackground {
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
                        text = dayOfWeekCnShort(date.dayOfWeek, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = headerSecondaryContentColor(),
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                // 非选中日用透明:Material You 下 surface 与 background 有细微差异,
                                // 填色会让 7 个非今天格都显出浅色圆圈
                                if (date == selectedDate) selectedBg else Color.Transparent
                            )
                            // 边框必须条件性挂载:border(0.dp) 在本 Compose 版本仍会画出 1px 发丝圆环
                            .then(
                                if (isToday) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = primaryColor,
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
                            color = headerContentColor(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AllDayStrip(
    occurrences: List<Occurrence>,
    weekStart: LocalDate,
    onEventClick: (Occurrence) -> Unit,
    modifier: Modifier = Modifier,
    /** 7 = 周条带,1 = 日视图顶部那一行(同一套布局,按参数推列宽) */
    dayCount: Int = 7,
    /** 搜索跳转后的高亮(spec §3.9);null = 不高亮 */
    highlight: HighlightTarget? = null,
) {
    val bars = remember(occurrences, weekStart, dayCount) {
        AllDaySpan.layout(occurrences, weekStart, dayCount)
    }
    // M3:全天/跨天条带(spec §3.3/§3.4)。无全天事件时不占高度。
    if (bars.isEmpty()) return

    // 超过 2 行折叠为 +N(条带不能无限吃掉时间轴的高度)
    val visibleRows = bars.filter { it.row < MAX_ALL_DAY_ROWS }
    val overflowCount = bars.size - visibleRows.size
    val rowIndices = visibleRows.map { it.row }.distinct().sorted()

    BoxWithConstraints(modifier = modifier.fillMaxWidth().testTag("all_day_strip")) {
        val colWidth = (maxWidth - GUTTER_WIDTH) / dayCount.toFloat()
        Column(modifier = Modifier.fillMaxWidth()) {
            rowIndices.forEach { row ->
                Row(modifier = Modifier.height(ALL_DAY_ROW_HEIGHT)) {
                    Spacer(Modifier.width(GUTTER_WIDTH))
                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        visibleRows.filter { it.row == row }.forEach { bar ->
                            val barWidth = colWidth * (bar.endColExclusive - bar.startCol)
                            Box(
                                modifier = Modifier
                                    .offset(x = colWidth * bar.startCol + 1.dp)
                                    .width(barWidth - 2.dp)
                                    .height(ALL_DAY_ROW_HEIGHT - 4.dp)
                                    .padding(top = 1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(EventColors.of(bar.occurrence.event.colorSlot))
                                    .then(
                                        if (bar.occurrence.event.id == highlight?.eventId &&
                                            bar.occurrence.start == highlight.start
                                        ) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(4.dp),
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onEventClick(bar.occurrence) }
                                    .testTag("all_day_bar_${bar.occurrence.event.id}"),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = bar.occurrence.event.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EventColors.on(bar.occurrence.event.colorSlot),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (overflowCount > 0) {
                Text(
                    text = "+$overflowCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = GUTTER_WIDTH + 4.dp, bottom = 2.dp)
                        .testTag("all_day_overflow"),
                )
            }
        }
    }
}

private val HOUR_HEIGHT = 56.dp
private val GUTTER_WIDTH = 44.dp
private val ALL_DAY_ROW_HEIGHT = 24.dp

/** 全天条最多可见行数,更多的折叠成 +N(spec §3.4) */
private const val MAX_ALL_DAY_ROWS = 2

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
    dayCount: Int = 7,
): List<List<DayBlock>> = (0 until dayCount).map { dayIndex ->
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
internal fun TimeGrid(
    selectedDate: LocalDate,
    today: LocalDate,
    weekStart: LocalDate,
    occurrences: List<Occurrence>,
    onEventClick: (Occurrence) -> Unit,
    onEmptySlotClick: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    /** M3 日视图复用同一条时间轴:7 = 周,1 = 日(列宽/吸附/网格线全部按此参数推) */
    dayCount: Int = 7,
    /**
     * 判空用的那一份发生。默认就是本页的 occurrences;日视图传的是"只算当天"的那份,
     * 因为它的查询窗口是 ±1 天——直接用窗口判空会出现"昨天有事件、今天没有"却什么都不显示。
     */
    emptyCheck: List<Occurrence> = occurrences,
    /** 搜索跳转后的高亮(spec §3.9);按 id **和**开始时间匹配 —— 同一天里被改期过的重复事件会占两块 */
    highlight: HighlightTarget? = null,
) {
    val locale = LocalLnxLocale.current
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
    LaunchedEffect(scrollState.maxValue, highlight?.eventId) {
        // 有搜索高亮时**主动让位**给下面那条"滚到高亮":两条都会在 maxValue 首次就绪时触发,
        // 不让位的话用户会看到时间轴先瞬移到当前时刻、再动画过去,中间闪一帧。
        if (highlight != null) return@LaunchedEffect
        if (scrollState.maxValue > 0) {
            val nowFraction = (nowState.value.hour + nowState.value.minute / 60f) / 24f
            val viewportPx = 24 * hourPx - scrollState.maxValue
            val target = (nowFraction * 24 * hourPx - viewportPx / 3f)
                .toInt().coerceIn(0, scrollState.maxValue)
            scrollState.scrollTo(target)
        }
    }

    // 搜索跳转进来时滚到高亮那次发生的位置(spec §3.9「定位」)。
    // 没有这段的话,时间轴仍锚在"当前时刻",目标块在今天 9 点、当前是晚上时
    // 高亮就在屏幕外 —— 用户只看到日历翻了页,看不到亮的是哪一条。
    // **key 里必须带 maxValue**:第一帧还没测量出内容高度,maxValue == 0 会直接返回;
    // 不带这个 key 的话它永远不会为"布局完成"再跑一次,滚动就静默失效(自己踩过)。
    LaunchedEffect(highlight?.eventId, highlight?.start, scrollState.maxValue) {
        val target = highlight?.start ?: return@LaunchedEffect
        if (scrollState.maxValue <= 0) return@LaunchedEffect
        val fraction = (target.hour + target.minute / 60f) / 24f
        val viewportPx = 24 * hourPx - scrollState.maxValue
        // 让目标块落在视口的上 1/3,和"滚到当前时刻"同一套构图
        val y = (fraction * 24 * hourPx - viewportPx / 3f).toInt().coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(y)
    }

    val now = nowState.value
    // 注意乘法顺序:可用的是 Dp.times(Float),不是 Float.times(Dp)
    val nowY = HOUR_HEIGHT * (now.hour + now.minute / 60f)
    // 当前时刻线只画在包含"今天"的页面(周=当前周,日=今天);翻到别周/别日不画
    val showNowLine = !today.isBefore(weekStart) &&
        !today.isAfter(weekStart.plusDays((dayCount - 1).toLong()))

    Box(modifier = modifier.testTag("time_grid")) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState),
        ) {
            // 左侧刻度列:标签右对齐并留 6dp 末距,贴着网格左缘(左对齐会让首位数字被屏幕边缘切掉)。
            // 0 点那行也一样上移半行:时间轴是竖向滚动的,滚到顶时 0 点被视口上缘切一半属于
            // 正常滚动裁剪(所有整点都如此),不必为它破坏"标签中心对齐整点线"的一致性。
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
                if (showNowLine) {
                    Box(
                        modifier = Modifier
                            .offset(x = GUTTER_WIDTH - 12.dp, y = nowY - 4.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .testTag("now_dot"),
                    )
                }
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
                            val col = (offset.x / (size.width / dayCount.toFloat()))
                                .toInt()
                                .coerceIn(0, dayCount - 1)
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
                val colWidth = maxWidth / dayCount
                val blocks = remember(occurrences, weekStart, dayCount) {
                    buildDayBlocks(occurrences, weekStart, dayCount)
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
                    val cw = size.width / dayCount
                    repeat(dayCount + 1) { i ->
                        drawLine(
                            color = gridLineColor,
                            start = androidx.compose.ui.geometry.Offset(i * cw, 0f),
                            end = androidx.compose.ui.geometry.Offset(i * cw, size.height),
                            strokeWidth = 1f,
                        )
                    }
                }
                // **必须用 forEachIndexed**:横向位置 = 第几列 × 列宽 + 当天内的车道偏移。
                // 曾经写成 `forEach { day -> ... offset(x = w * b.lane) }`,把外层那个"第几天"
                // 彻底丢了 —— 于是**所有天的日程都画在第一列**,只是彼此按车道并排。
                // 单日事件的截图完全看不出来(9 个里程碑的走查截图恰好都是单日事件),
                // 一旦一周里有两天的日程就露馅。M9 补测试时才发现。
                blocks.forEachIndexed { dayIndex, day ->
                    day.forEach { b ->
                        val w = colWidth / b.lanes
                        val occ = b.occurrence
                        // 读屏读完整时间段和地点(M9):界面上只画了标题和开始时间,
                        // 光靠那两个 Text 读出来是"标题""09:00"两截,拼不成一个事件
                        val spoken = buildString {
                            append(occ.event.title)
                            append(",")
                            append(
                                if (occ.event.allDay) {
                                    stringResource(R.string.all_day)
                                } else {
                                    stringResource(
                                        R.string.detail_time_range,
                                        LnxLocale.time(occ.start.toLocalTime(), locale),
                                        LnxLocale.time(occ.end.toLocalTime(), locale),
                                    )
                                },
                            )
                            occ.event.location?.takeIf { it.isNotBlank() }?.let {
                                append(",").append(it)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = colWidth * dayIndex + w * b.lane,
                                    y = (b.topMinutes / 60f * HOUR_HEIGHT.value).dp,
                                )
                                .width((w - 1.dp).coerceAtLeast(1.dp))
                                .height((b.heightMinutes / 60f * HOUR_HEIGHT.value).dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(EventColors.of(occ.event.colorSlot))
                                    .then(
                                        if (occ.event.id == highlight?.eventId &&
                                            occ.start == highlight.start
                                        ) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(4.dp),
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onEventClick(occ) }
                                    .semantics(mergeDescendants = true) { contentDescription = spoken }
                                    .testTag("event_block_${occ.event.id}"),
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
                if (showNowLine) {
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
        }
        // 空状态(spec §3.14):仅当这一周确实没有事件时才显示,
        // 否则会盖在事件块上面(M1 时网格恒空,这个遮罩是无害的)
        if (emptyCheck.isEmpty()) {
            // 入场动画按主题走(spec §5.2/§5.3 动效基线):
            // 宁静冷色慢慢淡入(呼吸感)、暖橙活力弹簧、其余 300ms。
            // 这是主题动效唯一对外可见的落点 —— 只定义不用的 motion 只是死代码。
            val enter = LocalLnxTheme.current.motion
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = enter),
                ) {
                    // 装饰用矢量图标(spec §5.3:不拿 emoji 当图标)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SunGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                if (selectedDate == today) R.string.empty_today else R.string.empty_day
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 空状态那枚小太阳(spec §5.3:不拿 emoji 当图标)。
 * 用 Canvas 画而不是 `Icons.Outlined.WbSunny`:后者在 material-icons-extended 里,
 * 为了一个装饰图案把整个图标包拖进 APK 不划算。
 */
@Composable
private fun SunGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.26f
        val stroke = 1.6.dp.toPx()
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = stroke))
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val dx = kotlin.math.cos(angle).toFloat()
            val dy = kotlin.math.sin(angle).toFloat()
            drawLine(
                color = color,
                start = Offset(center.x + dx * radius * 1.7f, center.y + dy * radius * 1.7f),
                end = Offset(center.x + dx * radius * 2.6f, center.y + dy * radius * 2.6f),
                strokeWidth = stroke,
            )
        }
    }
}
