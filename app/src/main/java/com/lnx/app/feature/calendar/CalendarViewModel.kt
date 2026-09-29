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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ViewMode(val label: String) { DAY("日"), WEEK("周"), MONTH("月") }

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
) : ViewModel() {
    private val today: LocalDate = LocalDate.now()

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
    private val occurrences: StateFlow<List<Occurrence>> = selection
        // 只在"周"变化时重查:切视图模式不该触发查询
        .map { weekStartOf(it.selectedDate) }
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

    private fun weekStartOf(date: LocalDate): LocalDateTime =
        weekStartOfDate(date).atStartOfDay()

    fun selectDate(date: LocalDate) = selection.update { it.copy(selectedDate = date) }

    fun selectViewMode(mode: ViewMode) = selection.update { it.copy(viewMode = mode) }

    fun backToToday() = selection.update { it.copy(selectedDate = today) }

    /** 删除事件(spec §3.6);软删除,Room 失效通知会让周视图即时消失 */
    fun deleteEvent(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** 详情卡的标签行(spec §3.6);M3 起事件可带标签 */
    fun observeTagsOf(eventId: String): Flow<List<Tag>> = tagRepository.observeTagsOfEvent(eventId)
}
