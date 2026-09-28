package com.lnx.app.feature.calendar

import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 假仓库:区间查询直接回放预置的发生 */
private class FakeEventRepository(
    private val occurrences: List<Occurrence> = emptyList(),
) : EventRepository {
    val observedRanges = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()

    private fun occurrence(id: String, start: String, end: String) = Occurrence(
        event = Event(
            id = id,
            title = id,
            allDay = false,
            start = LocalDateTime.parse(start),
            end = LocalDateTime.parse(end),
            location = null,
            notes = null,
            colorSlot = 0,
            priority = Priority.P2,
            reminderLeadMinutes = null,
            rule = EventRule(),
            createdAt = 0L,
            updatedAt = 0L,
        ),
        start = LocalDateTime.parse(start),
        end = LocalDateTime.parse(end),
    )

    override fun observeOccurrences(
        start: LocalDateTime,
        end: LocalDateTime,
    ): Flow<List<Occurrence>> = observeEvents(start, end).map { events ->
        events.map { Occurrence(it, it.start, it.end) }
    }

    override fun observeEvents(
        start: LocalDateTime,
        end: LocalDateTime,
    ): Flow<List<Event>> {
        observedRanges += start to end
        return MutableStateFlow(
            occurrences
                .filter { it.start < end && it.end > start }
                .map { it.event },
        )
    }

    override suspend fun getEvent(id: String): Event? =
        occurrences.firstOrNull { it.event.id == id }?.event

    override suspend fun save(event: Event) = Unit

    override suspend fun delete(id: String) = Unit
}

class CalendarViewModelTest {
    // viewModelScope 依赖 Dispatchers.Main,JVM 单测没有主线程,必须替换
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeEventRepository = FakeEventRepository()) = CalendarViewModel(repo)

    @Test
    fun `初始状态为今天与周视图`() {
        val vm = vm()
        assertEquals(LocalDate.now(), vm.uiState.value.selectedDate)
        assertEquals(ViewMode.WEEK, vm.uiState.value.viewMode)
    }

    @Test
    fun `selectViewMode 只改变视图模式`() {
        val vm = vm()
        val date = vm.uiState.value.selectedDate
        vm.selectViewMode(ViewMode.MONTH)
        assertEquals(ViewMode.MONTH, vm.uiState.value.viewMode)
        assertEquals(date, vm.uiState.value.selectedDate)
    }

    @Test
    fun `selectDate 只改变选中日期`() {
        val vm = vm()
        val target = LocalDate.of(2026, 1, 1)
        vm.selectDate(target)
        assertEquals(target, vm.uiState.value.selectedDate)
        assertEquals(ViewMode.WEEK, vm.uiState.value.viewMode)
    }

    @Test
    fun `backToToday 回到今天并保留视图模式`() {
        val vm = vm()
        vm.selectDate(LocalDate.of(2020, 5, 5))
        vm.selectViewMode(ViewMode.MONTH)
        vm.backToToday()
        assertEquals(LocalDate.now(), vm.uiState.value.selectedDate)
        assertEquals(ViewMode.MONTH, vm.uiState.value.viewMode)
    }

    @Test
    fun `按选中周查询并把发生放进状态`() {
        val today = LocalDate.now()
        val repo = FakeEventRepository()
        val vm = CalendarViewModel(repo)
        // 选中日所在周的周一 00:00 到下周一 00:00
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val start = LocalDateTime.of(monday, java.time.LocalTime.MIDNIGHT)
        vm.selectDate(monday)
        val range = repo.observedRanges.last()
        assertEquals(start, range.first)
        assertEquals(start.plusWeeks(1), range.second)
        assertEquals(emptyList<Occurrence>(), vm.uiState.value.occurrences)
    }
}
