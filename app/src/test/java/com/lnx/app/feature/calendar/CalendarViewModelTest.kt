package com.lnx.app.feature.calendar

import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.TagFilterState
import com.lnx.app.core.domain.recurrence.RecurrenceEditHandler
import com.lnx.app.core.domain.TagRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.model.Tag
import com.lnx.app.core.domain.recurrence.EditScope
import com.lnx.app.core.domain.recurrence.RecurrenceEngine
import com.lnx.app.core.notification.RecordingAlarmSink
import com.lnx.app.core.notification.ReminderPlanner
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.settings.LnxSettings
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.SettingsRepository
import java.time.Duration
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 假仓库:区间查询直接回放预置的发生 */
private class FakeEventRepository(
    private val occurrences: List<Occurrence> = emptyList(),
    /** 真母事件(重复系列的原始行);getEvent 优先取它——重复发生上带的 start 是那一次,不是系列起点 */
    private val masters: Map<String, Event> = emptyMap(),
) : EventRepository {
    val observedRanges = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
    val saved = mutableListOf<Event>()

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
        masters[id] ?: occurrences.firstOrNull { it.event.id == id }?.event

    override suspend fun save(event: Event) {
        saved += event
    }

    override suspend fun delete(id: String) = Unit

    override suspend fun upsertException(exception: EventException) = Unit

    override suspend fun cancelOccurrence(masterId: String, originalDate: LocalDate) = Unit

    override suspend fun deleteExceptionsFor(masterId: String) = Unit

    override suspend fun deleteExceptionsFrom(masterId: String, from: LocalDate) = Unit
}

/** 假标签仓库:默认空实现;[tagIdsByEvent] 非空时用来验证三个视图窗口都走了同一套筛选 */
private class FakeTagRepository(
    private val tagIdsByEvent: Map<String, List<String>> = emptyMap(),
) : TagRepository {
    override fun observeTags(): Flow<List<Tag>> = MutableStateFlow(emptyList())

    override suspend fun createTag(name: String, colorSlot: Int): Result<Tag> =
        Result.success(Tag("t-$name", name, colorSlot))

    override suspend fun renameTag(id: String, name: String) = Unit

    override suspend fun deleteTag(id: String) = Unit

    override suspend fun setEventTags(eventId: String, tagIds: List<String>) = Unit

    override fun observeTagsOfEvent(eventId: String): Flow<List<Tag>> = MutableStateFlow(emptyList())

    override fun observeEventTagIds(): Flow<Map<String, List<String>>> =
        MutableStateFlow(tagIdsByEvent)
}

class CalendarViewModelTest {
    /** 构造一条落在指定周内的发生,供 ViewModel 测试预置 */
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

    // viewModelScope 依赖 Dispatchers.Main,JVM 单测没有主线程,必须替换
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeEventRepository = FakeEventRepository()) =
        CalendarViewModel(
            repo,
            FakeTagRepository(),
            TagFilterState(),
            RecurrenceEditHandler(repo),
            ReminderPlanner(repo, RecordingAlarmSink()),
            FakeSettingsRepository(),
        )

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
        val thisMonday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
        // 预置一条落在本周的事件,证明 occurrences 真的流进了状态
        val repo = FakeEventRepository(
            listOf(
                occurrence("e1", "${thisMonday}T09:00", "${thisMonday}T10:00"),
            ),
        )
        val vm = CalendarViewModel(repo, FakeTagRepository(), TagFilterState(), RecurrenceEditHandler(repo), ReminderPlanner(repo, RecordingAlarmSink()), FakeSettingsRepository())
        vm.selectDate(thisMonday)
        assertEquals(listOf("e1"), vm.uiState.value.occurrences.map { it.event.id })

        val start = LocalDateTime.of(thisMonday, java.time.LocalTime.MIDNIGHT)
        // 日视图查询(±1 天窗口)也走同一仓库,断言周窗口时按"区间长 7 天"过滤
        val range = repo.observedRanges
            .last { java.time.Duration.between(it.first, it.second).toDays() == 7L }
        assertEquals(start, range.first)
        assertEquals(start.plusWeeks(1), range.second)
    }

    @Test
    fun `切换到另一周会重新查询`() {
        val repo = FakeEventRepository()
        val vm = CalendarViewModel(repo, FakeTagRepository(), TagFilterState(), RecurrenceEditHandler(repo), ReminderPlanner(repo, RecordingAlarmSink()), FakeSettingsRepository())
        val before = repo.observedRanges
            .count { java.time.Duration.between(it.first, it.second).toDays() == 7L }
        // 选一个肯定不同的周(今天所在的周往后三周),确保不是同值合流
        vm.selectDate(LocalDate.now().plusWeeks(3))
        val after = repo.observedRanges
            .count { java.time.Duration.between(it.first, it.second).toDays() == 7L }
        assertEquals(before + 1, after)
    }

    @Test
    fun `勾掉标签后周日月三个窗口都过滤掉该事件`() {
        // 三个视图各查各的窗口(周 / ±1 天 / ±1 月),最容易出的错就是只过滤了其中一路。
        // 三路都断言,漏哪一路都会红。
        val thisMonday = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        val repo = FakeEventRepository(
            listOf(occurrence("e1", "${thisMonday}T09:00", "${thisMonday}T10:00")),
        )
        val filterState = TagFilterState()
        val vm = CalendarViewModel(repo, FakeTagRepository(mapOf("e1" to listOf("t1"))), filterState, RecurrenceEditHandler(repo), ReminderPlanner(repo, RecordingAlarmSink()), FakeSettingsRepository())
        vm.selectDate(thisMonday)
        assertEquals(listOf("e1"), vm.uiState.value.occurrences.map { it.event.id })

        filterState.toggle("t1")

        assertTrue(vm.uiState.value.occurrences.isEmpty())
        assertTrue(vm.uiState.value.dayOccurrences.isEmpty())
        assertTrue(vm.uiState.value.monthOccurrences.isEmpty())
    }

    @Test
    fun `删本次及以后_按真母事件剪断_系列起点和剪断日之前的发生都不丢`() {
        // 回归:删除路径曾把"那一次的发生"当母事件落库,startAt 被改写成剪断日,
        // 叠加 Until(前一天)后整条系列一条都不剩 —— 剪断日之前的历史全被抹掉。
        val seriesStart = LocalDateTime.parse("2026-09-29T09:00")
        val cutDate = LocalDate.parse("2026-12-01")
        val rule = EventRule(type = RuleType.DAILY, interval = 1)
        val master = Event(
            id = "m1", title = "每日站会", allDay = false,
            start = seriesStart, end = seriesStart.plusHours(1),
            location = null, notes = null, colorSlot = 0, priority = Priority.P2,
            reminderLeadMinutes = null, rule = rule, createdAt = 0L, updatedAt = 0L,
        )
        // 详情页拿到的发生:event.start 是"这一次"的日期,不是系列起点
        val occurrence = Occurrence(
            event = master.copy(start = cutDate.atTime(9, 0), end = cutDate.atTime(10, 0)),
            start = cutDate.atTime(9, 0),
            end = cutDate.atTime(10, 0),
            originalDate = cutDate,
        )
        val repo = FakeEventRepository(masters = mapOf("m1" to master))

        vm(repo).deleteOccurrence(occurrence, EditScope.THIS_AND_FUTURE)

        assertEquals(1, repo.saved.size)
        val saved = repo.saved.single()
        assertEquals(seriesStart, saved.start)
        assertEquals(RuleEnd.Until(cutDate.minusDays(1)), saved.rule.end)
        // 剪断日之前的历史发生必须还在(以引擎实际展开为准,不是只看规则字段)
        val kept = RecurrenceEngine.expand(
            saved.rule, saved.start, Duration.between(saved.start, saved.end),
            seriesStart, cutDate.atStartOfDay(),
        )
        assertTrue("剪断日之前应仍有发生", kept.isNotEmpty())
    }

    @Test
    fun `删除本次及以后_剪断真母事件并触发提醒重排`() {
        // 删除会改变"还剩哪些发生带提醒",所以落库后必须重排;漏了这一步,
        // 被删事件的闹钟还会照响(spec §3.8)
        val seriesStart = LocalDateTime.parse("2026-09-08T10:00")
        val cutDate = LocalDate.parse("2026-12-01")
        val master = Event(
            id = "m1", title = "周会", allDay = false,
            start = seriesStart, end = seriesStart.plusHours(1),
            location = null, notes = null, colorSlot = 0, priority = Priority.P2,
            reminderLeadMinutes = 15, rule = EventRule(type = RuleType.DAILY), createdAt = 0L, updatedAt = 0L,
        )
        val sink = RecordingAlarmSink()
        val repo = FakeEventRepository(masters = mapOf("m1" to master))
        val vm = CalendarViewModel(
            repo, FakeTagRepository(), TagFilterState(), RecurrenceEditHandler(repo), ReminderPlanner(repo, sink),
            FakeSettingsRepository(),
        )

        vm.deleteOccurrence(
            Occurrence(
                master.copy(start = cutDate.atTime(10, 0), end = cutDate.atTime(11, 0)),
                cutDate.atTime(10, 0),
                cutDate.atTime(11, 0),
                cutDate,
            ),
            EditScope.THIS_AND_FUTURE,
        )

        assertEquals(seriesStart, repo.saved.single().start)
        assertEquals(1, sink.rescheduleCount)
    }
}

/** 设置假实现:日历只需要"周起始日"这一项(M6 起视图由它决定) */
private class FakeSettingsRepository(
    initial: LnxSettings = SettingsDefaults.snapshot(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<LnxSettings> = state
    override suspend fun current(): LnxSettings = state.value
    override suspend fun setThemeSlot(slot: ThemeSlot) = Unit
    override suspend fun setDarkMode(mode: DarkMode) = Unit
    override suspend fun setReminderLead(minutes: Int?) = Unit
    override suspend fun setDnd(enabled: Boolean, startMinute: Int, endMinute: Int) = Unit
    override suspend fun setWeekStartMonday(monday: Boolean) {
        state.value = state.value.copy(weekStartMonday = monday)
    }

    override suspend fun setLanguage(language: String) = Unit
    override suspend fun setOnboardingDone() = Unit
}
