package com.lnx.app.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.core.common.formatTitle
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.recurrence.EditScope
import com.lnx.app.feature.calendar.components.CalendarTopBar
import com.lnx.app.feature.calendar.components.ViewModeTabs
import com.lnx.app.feature.calendar.day.DayView
import com.lnx.app.feature.calendar.drawer.CalendarDrawer
import com.lnx.app.feature.calendar.month.MonthView
import com.lnx.app.feature.calendar.week.WeekView
import kotlinx.coroutines.launch
import com.lnx.app.feature.event.EventDefaults
import com.lnx.app.feature.event.EventDetailContent
import com.lnx.app.feature.event.EventEditScreen
import com.lnx.app.feature.event.LnxDetailSheet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 编辑页入口:M2 无导航图的临时路由,eventId(编辑)与 start(新建)恰好给一个 */
private data class EditorTarget(
    val eventId: String? = null,
    val start: LocalDateTime? = null,
    /** 编辑重复事件的某一次时给:携带该次发生与作用范围(spec §4.4) */
    val occurrence: Occurrence? = null,
    val scope: EditScope? = null,
)

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val hiddenTagIds by viewModel.hiddenTagIds.collectAsStateWithLifecycle()
    val hideUntagged by viewModel.hideUntagged.collectAsStateWithLifecycle()

    // 抽屉(spec §3.10):汉堡菜单打开,勾选即隐藏对应事件
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // M2: 编辑页/详情卡的临时态;M3 引入导航图后改为 NavHost 路由
    var detailTarget by remember { mutableStateOf<Occurrence?>(null) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    val onEventClick: (Occurrence) -> Unit = remember { { detailTarget = it } }
    val onEmptySlotClick: (LocalDateTime) -> Unit = remember { { editorTarget = EditorTarget(start = it) } }
    val onFabClick: () -> Unit = remember {
        {
            val selected = state.selectedDate
            editorTarget = EditorTarget(start = EventDefaults.startFor(selected, LocalTime.now()))
        }
    }
    // 重叠提示落在日历页:编辑页保存后即关闭,提示得由这里弹(spec §3.5:不阻止保存)
    val snackbar = remember { SnackbarHostState() }
    var overlapNotice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(overlapNotice) {
        overlapNotice?.let {
            snackbar.showSnackbar("与\"$it\"时间重叠")
            overlapNotice = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CalendarDrawer(
                    tags = tags,
                    hiddenTagIds = hiddenTagIds,
                    hideUntagged = hideUntagged,
                    onToggleTag = viewModel::toggleTag,
                    onToggleUntagged = viewModel::toggleUntagged,
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CalendarTopBar(
                    title = formatTitle(state.selectedDate),
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onTodayClick = viewModel::backToToday,
                    onSearchClick = { /* 搜索在 M7 接入 */ },
                )
            ViewModeTabs(
                current = state.viewMode,
                onSelect = viewModel::selectViewMode,
            )
            when (state.viewMode) {
                // M3:日视图(日期条 + 单列时间轴)
                ViewMode.DAY -> DayView(
                    state = state,
                    today = remember { LocalDate.now() },
                    onSelectDate = viewModel::selectDate,
                    occurrences = state.dayOccurrences,
                    onEventClick = onEventClick,
                    onEmptySlotClick = onEmptySlotClick,
                    modifier = Modifier.weight(1f),
                )
                // M1: 进程内固定 today,跨零点需刷新(已记录为 minor)
                ViewMode.WEEK -> WeekView(
                    state = state,
                    today = remember { LocalDate.now() },
                    onSelectDate = viewModel::selectDate,
                    occurrences = state.occurrences,
                    onEventClick = onEventClick,
                    onEmptySlotClick = onEmptySlotClick,
                    modifier = Modifier.weight(1f),
                )
                // M3:月视图(上 6×7 月历 + 下当日列表联动)
                ViewMode.MONTH -> MonthView(
                    state = state,
                    today = remember { LocalDate.now() },
                    onSelectDate = viewModel::selectDate,
                    occurrences = state.monthOccurrences,
                    onEventClick = onEventClick,
                    // spec §3.5:月视图空态「＋ 新建日程」= 该日 09:00(不做下半点预填)
                    onCreateAt = { date -> editorTarget = EditorTarget(start = date.atTime(9, 0)) },
                    modifier = Modifier.weight(1f),
                )
            }
            }
        }
        // 新建事件入口(spec §3.1:右下角 ＋)
        FloatingActionButton(
            onClick = onFabClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("fab_create"),
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建事件")
        }

        // 时间重叠提示(spec §3.5:提示不阻止保存)
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )

        // 全屏编辑页覆盖在日历之上(M2 还没有导航图,M3 换 NavHost)
        editorTarget?.let { target ->
            EventEditScreen(
                start = target.start,
                eventId = target.eventId,
                occurrence = target.occurrence,
                scope = target.scope,
                onClose = { editorTarget = null },
                onSaved = { overlaps -> overlapNotice = overlaps.firstOrNull() },
            )
        }

        // 事件详情卡(spec §3.6):编辑/删除先选作用范围(重复事件三选一),再落库
        detailTarget?.let { occ ->
            // 必须 remember:observeTagsOf 每次调用都返回新 Flow 实例,
            // 直接 collectAsState 会让外层状态每变一次就取消并重启一次 Room 订阅
            val tagsFlow = remember(occ.event.id) { viewModel.observeTagsOf(occ.event.id) }
            val tags by tagsFlow.collectAsState(initial = emptyList())
            LnxDetailSheet(
                visible = true,
                onDismiss = { detailTarget = null },
                title = occ.event.title,
            ) {
                EventDetailContent(
                    event = occ.event,
                    tags = tags,
                    onEdit = { scope ->
                        detailTarget = null
                        // "全部"(含单次事件)= 直接改母事件;另两档走该次发生的编辑模式
                        editorTarget = if (scope == EditScope.ALL) {
                            EditorTarget(eventId = occ.event.id)
                        } else {
                            EditorTarget(occurrence = occ, scope = scope)
                        }
                    },
                    onDelete = { scope ->
                        detailTarget = null
                        viewModel.deleteOccurrence(occ, scope)
                    },
                )
            }
        }
    }
}
