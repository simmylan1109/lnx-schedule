package com.lnx.app.feature.calendar

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class ViewMode(val label: String) { DAY("日"), WEEK("周"), MONTH("月") }

data class CalendarUiState(
    val selectedDate: LocalDate,
    val viewMode: ViewMode,
)

@HiltViewModel
class CalendarViewModel @Inject constructor() : ViewModel() {
    private val today: LocalDate = LocalDate.now()

    private val _uiState = MutableStateFlow(
        CalendarUiState(selectedDate = today, viewMode = ViewMode.WEEK)
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    fun selectDate(date: LocalDate) = _uiState.update { it.copy(selectedDate = date) }

    fun selectViewMode(mode: ViewMode) = _uiState.update { it.copy(viewMode = mode) }

    fun backToToday() = _uiState.update { it.copy(selectedDate = today) }
}