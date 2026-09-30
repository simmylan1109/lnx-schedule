package com.lnx.app.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.R
import com.lnx.app.core.common.LocalLnxLocale
import com.lnx.app.core.common.formatTitle
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.recurrence.EditScope
import com.lnx.app.core.designsystem.SereneHeaderBackground
import com.lnx.app.feature.calendar.components.CalendarTopBar
import com.lnx.app.feature.calendar.components.ViewModeTabs
import com.lnx.app.feature.calendar.day.DayView
import com.lnx.app.feature.calendar.drawer.CalendarDrawer
import com.lnx.app.feature.calendar.month.MonthView
import com.lnx.app.feature.calendar.week.WeekView
import com.lnx.app.feature.search.SearchScreen
import kotlinx.coroutines.launch
import com.lnx.app.feature.event.EventDefaults
import com.lnx.app.feature.event.EventDetailContent
import com.lnx.app.feature.event.EventEditScreen
import com.lnx.app.feature.event.LnxDetailSheet
import com.lnx.app.feature.settings.SettingsScreen
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

/**
 * 点提醒通知带进来的"打开某次发生"请求(spec §3.8)。
 * 刻意不写 data class:按引用比较,连点同一条通知也能再次打开详情卡
 * (data class 的值相等会让 LaunchedEffect 以为没变化,第二次点击就失灵)。
 */
class OpenEventRequest(
    val eventId: String,
    val occurrenceStart: LocalDateTime,
)

@Composable
fun CalendarScreen(
    openRequest: OpenEventRequest? = null,
    onOpenRequestConsumed: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val hiddenTagIds by viewModel.hiddenTagIds.collectAsStateWithLifecycle()
    val hideUntagged by viewModel.hideUntagged.collectAsStateWithLifecycle()
    val openTarget by viewModel.openTarget.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
    val weekStartMonday by viewModel.weekStartMonday.collectAsStateWithLifecycle()
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchTagColors by viewModel.searchTagColors.collectAsStateWithLifecycle()
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    val locale = LocalLnxLocale.current

    // 抽屉(spec §3.10):汉堡菜单打开,勾选即隐藏对应事件
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // M2: 编辑页/详情卡的临时态;M3 引入导航图后改为 NavHost 路由
    var detailTarget by remember { mutableStateOf<Occurrence?>(null) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    // 打开详情卡即撤掉搜索跳转的高亮(spec §3.9 的高亮是一次性定位提示,
    // 留着会在事件块上一直挂个圈,用户还以为那里有新东西)
    val onEventClick: (Occurrence) -> Unit = remember {
        {
            viewModel.clearHighlight()
            detailTarget = it
        }
    }
    val onEmptySlotClick: (LocalDateTime) -> Unit = remember { { editorTarget = EditorTarget(start = it) } }
    val onFabClick: () -> Unit = remember {
        {
            val selected = state.selectedDate
            editorTarget = EditorTarget(start = EventDefaults.startFor(selected, LocalTime.now()))
        }
    }
    // 重叠提示落在日历页:编辑页保存后即关闭,提示得由这里弹(spec §3.5:不阻止保存)
    val snackbar = remember { SnackbarHostState() }
    var overlapTitle by remember { mutableStateOf<String?>(null) }
    val overlapTemplate = stringResource(R.string.calendar_overlap)
    LaunchedEffect(overlapTitle) {
        overlapTitle?.let {
            snackbar.showSnackbar(overlapTemplate.format(it))
            overlapTitle = null
        }
    }
    // 点提醒通知进来(spec §3.8)。两个 effect 必须分开:openEvent 是异步查库,
    // openTarget 要等它出结果才变;合成一个的话第二次读到的还是初始 null,详情卡永远不弹。
    // 消费后立刻回调清掉 Activity 侧的 openRequest:换主题会重建整棵子树、effect 重跑,
    // 不清的话旧请求会再触发一次,详情卡在换完主题后自己弹出来。
    LaunchedEffect(openRequest) {
        openRequest?.let {
            viewModel.openEvent(it.eventId, it.occurrenceStart)
            onOpenRequestConsumed()
        }
    }
    LaunchedEffect(openTarget) {
        openTarget?.let {
            detailTarget = it
            viewModel.consumeOpenTarget()
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
                    onOpenSettings = {
                        viewModel.openSettings()
                        scope.launch { drawerState.close() }
                    },
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 头部区(spec §5.2 主题 4:顶栏 + Tab 走天空蓝→紫渐变;其余主题原样)
                // 注意 SereneHeaderBackground 是 Box 作用域,两个子项会叠在一起,
                // 必须自己包一层 Column 才是"顶栏在上、Tab 在下"。
                SereneHeaderBackground {
                    Column {
                        CalendarTopBar(
                            title = formatTitle(state.selectedDate, locale),
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onTodayClick = viewModel::backToToday,
                            onSearchClick = viewModel::openSearch,
                        )
                        ViewModeTabs(
                            current = state.viewMode,
                            onSelect = viewModel::selectViewMode,
                        )
                    }
                }
            when (state.viewMode) {
                // M3:日视图(日期条 + 单列时间轴)
                ViewMode.DAY -> DayView(
                    state = state,
                    today = remember { LocalDate.now() },
                    onSelectDate = viewModel::selectDate,
                    occurrences = state.dayOccurrences,
                    onEventClick = onEventClick,
                    onEmptySlotClick = onEmptySlotClick,
                    highlight = highlight,
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
                    weekStartMonday = weekStartMonday,
                    highlight = highlight,
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
                    weekStartMonday = weekStartMonday,
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
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.calendar_new_event))
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
                onSaved = { overlaps -> overlapTitle = overlaps.firstOrNull() },
            )
        }

        // 设置页(spec §3.11):盖在最上层,返回关掉回日历
        if (showSettings) {
            SettingsScreen(onClose = viewModel::closeSettings)
        }

        // 搜索页(spec §3.9):盖在设置页之下、日历之上;点结果跳日视图并高亮
        if (searchOpen) {
            SearchScreen(
                query = searchQuery,
                results = searchResults,
                tagColors = searchTagColors,
                onQueryChange = viewModel::setSearchQuery,
                onResultClick = viewModel::jumpToResult,
                onClose = viewModel::closeSearch,
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
                        // 例外只对重复发生存在:没有 originalDate 就退回"改母事件",
                        // 否则会给单次事件开一条永远匹配不上的例外
                        val effective = if (occ.originalDate == null) EditScope.ALL else scope
                        editorTarget = if (effective == EditScope.ALL) {
                            EditorTarget(eventId = occ.event.id)
                        } else {
                            EditorTarget(occurrence = occ, scope = effective)
                        }
                    },
                    onDelete = { scope ->
                        detailTarget = null
                        val effective = if (occ.originalDate == null) EditScope.ALL else scope
                        viewModel.deleteOccurrence(occ, effective)
                    },
                )
            }
        }
    }
}
