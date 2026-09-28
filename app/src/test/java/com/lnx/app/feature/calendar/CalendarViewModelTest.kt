package com.lnx.app.feature.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarViewModelTest {
    @Test
    fun `初始状态为今天与周视图`() {
        val vm = CalendarViewModel()
        assertEquals(LocalDate.now(), vm.uiState.value.selectedDate)
        assertEquals(ViewMode.WEEK, vm.uiState.value.viewMode)
    }

    @Test
    fun `selectViewMode 只改变视图模式`() {
        val vm = CalendarViewModel()
        val date = vm.uiState.value.selectedDate
        vm.selectViewMode(ViewMode.MONTH)
        assertEquals(ViewMode.MONTH, vm.uiState.value.viewMode)
        assertEquals(date, vm.uiState.value.selectedDate)
    }

    @Test
    fun `selectDate 只改变选中日期`() {
        val vm = CalendarViewModel()
        val target = LocalDate.of(2026, 1, 1)
        vm.selectDate(target)
        assertEquals(target, vm.uiState.value.selectedDate)
        assertEquals(ViewMode.WEEK, vm.uiState.value.viewMode)
    }

    @Test
    fun `backToToday 回到今天并保留视图模式`() {
        val vm = CalendarViewModel()
        vm.selectDate(LocalDate.of(2020, 5, 5))
        vm.selectViewMode(ViewMode.MONTH)
        vm.backToToday()
        assertEquals(LocalDate.now(), vm.uiState.value.selectedDate)
        assertEquals(ViewMode.MONTH, vm.uiState.value.viewMode)
    }
}
