package com.lnx.app.feature.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.core.common.formatTitle
import com.lnx.app.feature.calendar.components.CalendarTopBar
import com.lnx.app.feature.calendar.components.ViewModeTabs
import com.lnx.app.feature.calendar.week.WeekView
import java.time.LocalDate

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                modifier = Modifier.weight(1f),
            )
            ViewMode.MONTH -> PlaceholderScreen("月视图将在后续里程碑提供")
        }
    }
}