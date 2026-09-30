package com.lnx.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.common.weekStartOf as weekStartOfDate
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.TagFilterState
import com.lnx.app.core.domain.TagRepository
import com.lnx.app.core.domain.applyTagFilter
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Tag
import com.lnx.app.core.domain.recurrence.EditScope
import com.lnx.app.core.domain.recurrence.RecurrenceEditHandler
import com.lnx.app.core.domain.search.HighlightTarget
import com.lnx.app.core.domain.search.SearchRepository
import com.lnx.app.core.domain.search.SearchResult
import com.lnx.app.core.notification.ReminderPlanner
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 视图模式。**标签是界面文案,归 `ViewModeTabs` 用 `stringResource` 取**,枚举里不带中文 */
enum class ViewMode { DAY, WEEK, MONTH }

data class CalendarUiState(
    val selectedDate: LocalDate,
    val viewMode: ViewMode,
    /** 当前所选周区间内的事件(M2 只做周视图;日/月视图在 M3 消费同一份数据) */
    val occurrences: List<Occurrence> = emptyList(),
    /** 所选日 ±1 天的事件(日视图翻页用:当前页 + 相邻页),M3 日视图消费 */
    val dayOccurrences: List<Occurrence> = emptyList(),
    /** 所选月 ±1 月的事件(月视图翻页用),M3 月视图消费 */
    val monthOccurrences: List<Occurrence> = emptyList(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: EventRepository,
    private val tagRepository: TagRepository,
    private val tagFilterState: TagFilterState,
    private val recurrenceHandler: RecurrenceEditHandler,
    private val reminderPlanner: ReminderPlanner,
    private val searchRepository: SearchRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val today: LocalDate = LocalDate.now()

    /**
     * 周起始日(spec §3.11 ①):true = 周一(出厂),false = 周日。
     * 周视图的翻页锚点、周查询窗口、月视图表头与格子的首位都由它决定 ——
     * 只存不用的话用户在设置里改了"周起始日",日历纹丝不动(验收走查踩到过)。
     */
    val weekStartMonday: StateFlow<Boolean> = settingsRepository.settings
        .map { it.weekStartMonday }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsDefaults.WEEK_START_MONDAY)

    private val selection = MutableStateFlow(
        CalendarUiState(selectedDate = today, viewMode = ViewMode.WEEK)
    )

    /** 抽屉清单用的全部标签(spec §3.10) */
    val tags: StateFlow<List<Tag>> = tagRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenTagIds: StateFlow<Set<String>> = tagFilterState.hiddenTagIds
    val hideUntagged: StateFlow<Boolean> = tagFilterState.hideUntagged

    fun toggleTag(tagId: String) = tagFilterState.toggle(tagId)

    fun toggleUntagged() = tagFilterState.toggleUntagged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val occurrences: StateFlow<List<Occurrence>> = combine(selection, weekStartMonday) { sel, monday ->
        weekStartOf(sel.selectedDate, monday)
    }
        // 只在"周"变化时重查:切视图模式不该触发查询
        .distinctUntilChanged()
        .flatMapLatest { weekStart ->
            repository.observeOccurrences(weekStart, weekStart.plusWeeks(1))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 日视图数据:所选日 ±1 天(当前页 + 相邻页可即时渲染),天变化才重查 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dayOccurrences: StateFlow<List<Occurrence>> = selection
        .map { it.selectedDate }
        .distinctUntilChanged()
        .flatMapLatest { day ->
            repository.observeOccurrences(
                day.minusDays(1).atStartOfDay(),
                day.plusDays(2).atStartOfDay(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 月视图数据:所选月 ±1 月(当前页 + 相邻页),月变化才重查 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthOccurrences: StateFlow<List<Occurrence>> = selection
        .map { YearMonth.from(it.selectedDate) }
        .distinctUntilChanged()
        .flatMapLatest { month ->
            repository.observeOccurrences(
                month.minusMonths(1).atDay(1).atStartOfDay(),
                month.plusMonths(2).atDay(1).atStartOfDay(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 筛选输入(关联表 + 抽屉勾选状态)合流成单一快照,供下方 5 流 combine 使用 */
    private data class FilterSpec(
        val tags: Map<String, List<String>>,
        val hidden: Set<String>,
        val hideUntagged: Boolean,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filterSpec: StateFlow<FilterSpec> =
        combine(
            tagRepository.observeEventTagIds(),
            tagFilterState.hiddenTagIds,
            tagFilterState.hideUntagged,
        ) { tags, hidden, hideUntagged ->
            FilterSpec(tags, hidden, hideUntagged)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            FilterSpec(emptyMap(), emptySet(), false),
        )

    /**
     * 选择与数据分开再合并:切 Tab / 翻周必须**立刻**反映到界面,
     * 不能等数据库查询回来(否则点下去有可感知的延迟,自动化测试也会抢跑)。
     * 三个视图的数据都过同一份标签筛选(spec §3.10:抽屉勾选全局生效)。
     */
    val uiState: StateFlow<CalendarUiState> =
        combine(selection, occurrences, dayOccurrences, monthOccurrences, filterSpec) {
                sel,
                occ,
                dayOcc,
                monthOcc,
                filter,
            ->
            sel.copy(
                occurrences = applyTagFilter(occ, filter.tags, filter.hidden, filter.hideUntagged),
                dayOccurrences = applyTagFilter(dayOcc, filter.tags, filter.hidden, filter.hideUntagged),
                monthOccurrences = applyTagFilter(monthOcc, filter.tags, filter.hidden, filter.hideUntagged),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, selection.value)

    /**
     * 点提醒通知进来的那次发生(spec §3.8:点击通知 → 打开该事件详情卡片)。
     * 独立于 uiState:它是一次性请求,不是"当前选中什么"的状态,混进 combine
     * 会让打开详情卡顺带触发三视图重查。找不到对应发生(事件已被删)时留 null,什么都不弹。
     */
    private val _openTarget = MutableStateFlow<Occurrence?>(null)
    val openTarget: StateFlow<Occurrence?> = _openTarget.asStateFlow()

    /**
     * 设置页开合(spec §3.11)。
     * **必须放在 ViewModel 而不是界面里的 remember**:MainActivity 用
     * `Crossfade(settings.themeSlot)` 包住整棵日历子树,换主题瞬间子树重建,
     * remember 状态全部归零 —— 放在界面里的话,点一下主题卡设置页就会自己关掉
     * (实机踩过)。ViewModel 挂在 Activity 作用域,重建时同一实例,状态活下来。
     */
    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    fun openSettings() {
        _showSettings.value = true
    }

    fun closeSettings() {
        _showSettings.value = false
    }

    fun openEvent(eventId: String, occurrenceStart: LocalDateTime) {
        viewModelScope.launch {
            selectDate(occurrenceStart.toLocalDate())
            val day = occurrenceStart.toLocalDate()
            val found = repository
                .observeOccurrences(day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                .first()
                .firstOrNull { it.event.id == eventId && it.start == occurrenceStart }
            _openTarget.value = found
        }
    }

    fun consumeOpenTarget() {
        _openTarget.value = null
    }

    // —— 搜索(spec §3.9)——
    // 开合与查询词同样放 ViewModel,理由与 [showSettings] 一样:换主题会重建整棵子树。

    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<SearchResult>> = _searchQuery
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else searchRepository.search(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 结果行上的标签色点(spec §3.9)。关联表给的是标签 id,要转成色位号得再合一次标签表;
     * 顺手在这里换算好,界面只管画圆点,不用认得标签实体。
     */
    val searchTagColors: StateFlow<Map<String, List<Int>>> =
        combine(tagRepository.observeTags(), tagRepository.observeEventTagIds()) { tags, ids ->
            val slotById = tags.associate { it.id to it.colorSlot }
            ids.mapValues { (_, list) -> list.mapNotNull { slotById[it] } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * 搜索结果跳转后的高亮目标(spec §3.9「定位 / 高亮」)。
     * 换日期或打开详情卡时清掉 —— 高亮是一次性提示,留着会一直有个圈挂在别的事件上。
     */
    private val _highlight = MutableStateFlow<HighlightTarget?>(null)
    val highlight: StateFlow<HighlightTarget?> = _highlight.asStateFlow()

    fun openSearch() {
        _searchQuery.value = ""
        _searchOpen.value = true
    }

    fun closeSearch() {
        _searchOpen.value = false
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * 点搜索结果(spec §3.9):跳日视图、定位到那天(重复事件用「下次发生」那天)、
     * 高亮该次发生。选中的日期就是结果自己的开始日期,不做任何推算。
     */
    fun jumpToResult(result: SearchResult) {
        selection.update {
            it.copy(selectedDate = result.start.toLocalDate(), viewMode = ViewMode.DAY)
        }
        _highlight.value = HighlightTarget(result.event.id, result.start)
        _searchOpen.value = false
        _searchQuery.value = ""
    }

    fun clearHighlight() {
        _highlight.value = null
    }

    /** 所选日期所在周的周起始零点(按设置的周起始日) */
    private fun weekStartOf(date: LocalDate, mondayFirst: Boolean): LocalDateTime =
        weekStartOfDate(date, mondayFirst).atStartOfDay()

    fun selectDate(date: LocalDate) {
        // 日期没变就不撤高亮:DayView 翻页回写会带着"同一个日期"再进来一次
        // (程序化翻页也会触发回写),那时高亮是刚由搜索跳转设上的,清掉它高亮就没了。
        if (selection.value.selectedDate != date) _highlight.value = null
        selection.update { it.copy(selectedDate = date) }
    }

    fun selectViewMode(mode: ViewMode) = selection.update { it.copy(viewMode = mode) }

    fun backToToday() = selection.update { it.copy(selectedDate = today) }

    /** 删除事件(spec §3.6);软删除,Room 失效通知会让周视图即时消失 */
    fun deleteEvent(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            reminderPlanner.reschedule()
        }
    }

    /** 详情卡删除:按作用范围落库(单次事件也是 ALL,走同一入口) */
    fun deleteOccurrence(occ: Occurrence, scope: EditScope) {
        viewModelScope.launch {
            // 必须按 id 回读真母事件:occ.event 带的 start 是"那一次"的日期,
            // 直接落库会把系列起点改写成被删那次,剪断日之前的历史会整条消失。
            val master = repository.getEvent(occ.event.id) ?: occ.event
            recurrenceHandler.apply(
                master = master,
                originalDate = occ.originalDate ?: master.start.toLocalDate(),
                edited = null,
                scope = scope,
            )
            reminderPlanner.reschedule()
        }
    }

    /** 详情卡的标签行(spec §3.6);M3 起事件可带标签 */
    fun observeTagsOf(eventId: String): Flow<List<Tag>> = tagRepository.observeTagsOfEvent(eventId)
}
