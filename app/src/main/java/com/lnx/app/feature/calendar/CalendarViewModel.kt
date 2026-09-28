package com.lnx.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.common.weekStartOf
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Occurrence
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class ViewMode(val label: String) { DAY("日"), WEEK("周"), MONTH("月") }

data class CalendarUiState(
    val selectedDate: LocalDate,
    val viewMode: ViewMode,
    /** 当前所选周区间内的事件(M2 只做周视图;日/月视图在 M3 消费同一份数据) */
    val occurrences: List<Occurrence> = emptyList(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: EventRepository,
) : ViewModel() {
    private val today: LocalDate = LocalDate.now()

    private val selection = MutableStateFlow(
        CalendarUiState(selectedDate = today, viewMode = ViewMode.WEEK)
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CalendarUiState> = selection
        .flatMapLatest { sel ->
            val weekStart = weekStartOf(sel.selectedDate).atStartOfDay()
            repository.observeOccurrences(weekStart, weekStart.plusWeeks(1))
                .map { occurrences -> sel.copy(occurrences = occurrences) }
        }
        .stateIn(
            scope = viewModelScope,
            // Eagerly:日历是单屏常驻,让状态始终是活的;若 M3 引入多页面/多订阅方,
            // 再评估改回 WhileSubscribed(5_000) 之类的懒启动
            started = SharingStarted.Eagerly,
            initialValue = selection.value,
        )

    fun selectDate(date: LocalDate) = selection.update { it.copy(selectedDate = date) }

    fun selectViewMode(mode: ViewMode) = selection.update { it.copy(viewMode = mode) }

    fun backToToday() = selection.update { it.copy(selectedDate = today) }
}
