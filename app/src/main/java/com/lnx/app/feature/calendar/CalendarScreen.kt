package com.lnx.app.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.core.common.formatTitle
import com.lnx.app.feature.calendar.components.CalendarTopBar
import com.lnx.app.feature.calendar.components.ViewModeTabs

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
            ViewMode.WEEK -> Box(modifier = Modifier.testTag("week_grid")) { // Task 5 起由 WeekView 承接此 tag
                PlaceholderScreen("周视图将在本里程碑内实现")
            }
            ViewMode.MONTH -> PlaceholderScreen("月视图将在后续里程碑提供")
        }
    }
}