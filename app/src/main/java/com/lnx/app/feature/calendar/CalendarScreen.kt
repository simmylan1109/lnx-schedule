package com.lnx.app.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.core.common.formatTitle
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.feature.calendar.components.CalendarTopBar
import com.lnx.app.feature.calendar.components.ViewModeTabs
import com.lnx.app.feature.calendar.week.WeekView
import com.lnx.app.feature.event.EventDefaults
import com.lnx.app.feature.event.EventDetailContent
import com.lnx.app.feature.event.EventEditScreen
import com.lnx.app.feature.event.LnxDetailSheet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 编辑页入口:M2 无导航图的临时路由,eventId(编辑)与 start(新建)恰好给一个 */
private data class EditorTarget(val eventId: String? = null, val start: LocalDateTime? = null)

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
        Column(modifier = Modifier.fillMaxSize()) {
            CalendarTopBar(
                title = formatTitle(state.selectedDate),
                onMenuClick = { /* 抽屉在 M3 接入 */ },
                onTodayClick = viewModel::backToToday,
                onSearchClick = { /* 搜索在 M7 接入 */ },
            )
            ViewModeTabs(
                current = state.viewMode,
                onSelect = viewModel::selectViewMode,
            )
            when (state.viewMode) {
                ViewMode.DAY -> PlaceholderScreen("日视图将在后续里程碑提供")
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
                ViewMode.MONTH -> PlaceholderScreen("月视图将在后续里程碑提供")
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
                onClose = { editorTarget = null },
                onSaved = { overlaps -> overlapNotice = overlaps.firstOrNull() },
            )
        }

        // 事件详情卡(spec §3.6):编辑转到编辑页,删除确认后删除(M2 只有普通事件)
        detailTarget?.let { occ ->
            val tags by viewModel.observeTagsOf(occ.event.id).collectAsState(initial = emptyList())
            LnxDetailSheet(
                visible = true,
                onDismiss = { detailTarget = null },
                title = occ.event.title,
            ) {
                EventDetailContent(
                    event = occ.event,
                    tags = tags,
                    onEdit = {
                        detailTarget = null
                        editorTarget = EditorTarget(eventId = occ.event.id)
                    },
                    onDelete = {
                        detailTarget = null
                        viewModel.deleteEvent(occ.event.id)
                    },
                )
            }
        }
    }
}
