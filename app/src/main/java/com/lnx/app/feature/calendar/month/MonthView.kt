package com.lnx.app.feature.calendar.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.lnx.app.core.common.dayOfWeekCn
import com.lnx.app.core.designsystem.EventColors
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.feature.calendar.CalendarUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_PAGE_EPOCH = YearMonth.of(1970, 1)

/** ±200 年,与周/日 pager 同量级 */
private const val MONTH_PAGE_COUNT = 4_801

private val DOW_HEADER = listOf("一", "二", "三", "四", "五", "六", "日")

private val AGENDA_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

/**
 * 月视图(spec §3.4):上半 6×7 月历(今天强调圈、事件彩点≤3),下半为所选日期的事件列表;
 * 点日期切换列表,左右滑切月,空态提供「＋ 新建日程」(预填该日 09:00)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthView(
    state: CalendarUiState,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    occurrences: List<Occurrence> = emptyList(),
    onEventClick: (Occurrence) -> Unit = {},
    onCreateAt: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pageOf = { month: YearMonth ->
        (month.year - MONTH_PAGE_EPOCH.year) * 12 + month.monthValue - 1
    }
    val pagerState = rememberPagerState(
        initialPage = pageOf(YearMonth.from(state.selectedDate)),
        pageCount = { MONTH_PAGE_COUNT },
    )
    // 与周/日视图相同的锚点协议:程序化选择 → 翻页;用户滑月 → 选中当月 1 日(标题/列表随之联动)。
    // 真正兜住"跨月补位格丢选择"的是下面那条同月判断,不是任何标志位:
    // 点相邻月补位格会先写 selectedDate、再由上面翻页,此时所选日期已经落在新月份里,
    // 所以 currentPage 回写必须识别出"这个月正是所选月"并跳过,否则会把用户选的 11 月 5 号改成 11 月 1 号。
    val userPaged = remember { mutableStateOf(false) }
    LaunchedEffect(YearMonth.from(state.selectedDate)) {
        val target = pageOf(YearMonth.from(state.selectedDate))
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (!userPaged.value) {
            userPaged.value = true // 首次组合不算用户翻页
            return@LaunchedEffect
        }
        val month = MONTH_PAGE_EPOCH.plusMonths(pagerState.currentPage.toLong())
        if (YearMonth.from(state.selectedDate) != month) {
            onSelectDate(month.atDay(1))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 星期表头(周一起始,spec §3.4)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            DOW_HEADER.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        // spec §3.4 上下两半(Google Calendar 经典布局):月历 1.5、列表 1,
        // 固定比例而不靠内容撑高 —— 否则月历被挤到 48%、列表只剩两三行
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.5f)
                .testTag("month_pager"),
        ) { page ->
            val month = MONTH_PAGE_EPOCH.plusMonths(page.toLong())
            val cells = remember(month) { monthCells(month) }
            // 彩点按日索引一次算好(记得 occurrences),别在 42 个格子里各 filter 一遍
            val dayIndex = remember(occurrences) { indexByDay(occurrences) }
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight().testTag("month_page_$month")) {
                cells.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        week.forEach { date ->
                            MonthCell(
                                date = date,
                                month = month,
                                selectedDate = state.selectedDate,
                                today = today,
                                dots = dayIndex[date].orEmpty().take(3),
                                onSelectDate = onSelectDate,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
        DayAgendaList(
            selectedDate = state.selectedDate,
            occurrences = occurrences,
            onEventClick = onEventClick,
            onCreateAt = onCreateAt,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 按日索引(月视图 42 格的彩点/列表共用):每个事件挂到它覆盖的每一天上。
 * 覆盖天数上限 366:脏数据(跨年的全天事件)不许把内存拖垮。
 */
internal fun indexByDay(occurrences: List<Occurrence>): Map<LocalDate, List<Occurrence>> {
    val map = HashMap<LocalDate, MutableList<Occurrence>>()
    for (occ in occurrences) {
        val last = if (occ.end.toLocalTime() == java.time.LocalTime.MIDNIGHT) {
            occ.end.toLocalDate().minusDays(1)
        } else {
            occ.end.toLocalDate()
        }
        var d = occ.start.toLocalDate()
        var guard = 0
        while (!d.isAfter(last) && guard++ < 366) {
            map.getOrPut(d) { mutableListOf() }.add(occ)
            d = d.plusDays(1)
        }
    }
    return map
}

/** 当日有"份"的事件:与 [dayStart, day+1) 半开相交(全天排他存储自然正确) */
internal fun eventsOn(date: LocalDate, occurrences: List<Occurrence>): List<Occurrence> {
    val dayStart = date.atStartOfDay()
    val dayEnd = dayStart.plusDays(1)
    return occurrences.filter { it.start < dayEnd && it.end > dayStart }
}

@Composable
private fun MonthCell(
    date: LocalDate,
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    dots: List<Occurrence>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inMonth = YearMonth.from(date) == month
    // 高度由外层行(weight)分配,这里只管填满并把日期圆点垂直居中:
    // 写死 44dp 既撑不满行,也顶不到 Material 的 48dp 触达底线
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onSelectDate(date) }
            .testTag("month_cell_$date"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(if (date == selectedDate) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .then(
                    if (date == today) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (inMonth) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        // 彩点:颜色 = 事件色位翻译值,最多 3 个(spec §3.4)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dots.forEach { occ ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(EventColors.of(occ.event.colorSlot)),
                )
            }
        }
    }
}

/** 下半:所选日期的事件列表(spec §3.4);空态 = 无日程 + ＋ 新建日程 */
@Composable
private fun DayAgendaList(
    selectedDate: LocalDate,
    occurrences: List<Occurrence>,
    onEventClick: (Occurrence) -> Unit,
    onCreateAt: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayEvents = eventsOn(selectedDate, occurrences).sortedBy { it.start }
    Column(modifier = modifier.fillMaxWidth().testTag("month_agenda")) {
        Text(
            text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 ${dayOfWeekCn(selectedDate)}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (dayEvents.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 · 无日程",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onCreateAt(selectedDate) },
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag("month_empty_create"),
                ) {
                    Text("＋ 新建日程")
                }
            }
        } else {
            LazyColumn {
                items(dayEvents, key = { "${it.event.id}@${it.start}" }) { occ ->
                    AgendaItem(occ = occ, onClick = { onEventClick(occ) })
                }
            }
        }
    }
}

@Composable
private fun AgendaItem(occ: Occurrence, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("agenda_item_${occ.event.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(EventColors.of(occ.event.colorSlot)),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = occ.event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (occ.event.allDay) "全天"
                       else "${occ.start.format(AGENDA_TIME_FMT)} – ${occ.end.format(AGENDA_TIME_FMT)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
