package com.lnx.app.feature.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 编辑页 UI 状态 */
data class EventEditUiState(
    val draft: EventDraft? = null,
    val isEditing: Boolean = false,
    val errors: List<ValidationError> = emptyList(),
    /** 时间重叠提示(spec §3.5:不阻止保存) */
    val overlapTitles: List<String> = emptyList(),
    val saved: Boolean = false,
)

@HiltViewModel
class EventEditViewModel @Inject constructor(
    private val repository: EventRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val editingId: String? = savedStateHandle.get<String>(KEY_EVENT_ID)

    private val _uiState = MutableStateFlow(EventEditUiState())
    val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    init {
        // 导航参数优先(M3 起用 NavHost 传参);没有参数时等调用方 initialize()
        val navStart = savedStateHandle.get<String>(KEY_START)?.let { LocalDateTime.parse(it) }
        if (editingId != null) {
            viewModelScope.launch {
                val event = repository.getEvent(editingId)
                _uiState.update {
                    it.copy(
                        draft = event?.toDraft() ?: EventDefaults.draft(navStart ?: LocalDateTime.now()),
                        isEditing = true,
                    )
                }
            }
        } else if (navStart != null) {
            _uiState.update { it.copy(draft = EventDefaults.draft(navStart)) }
        }
    }

    /**
     * 新建入口:由调用方给定预填时间(spec §3.5 的三入口规则)。
     * 无导航图时 ViewModel 挂在 Activity 上、跨多次打开存活,
     * 所以这里必须**无条件**重置整个状态——否则第二次打开会残留上一次的草稿。
     */
    fun initialize(start: LocalDateTime) {
        _uiState.value = EventEditUiState(draft = EventDefaults.draft(start))
    }

    /** 编辑入口:按 id 载入既有事件;事件已被删除时退化为新建 */
    fun initializeEvent(eventId: String) {
        val blank = EventEditUiState()
        _uiState.value = blank
        viewModelScope.launch {
            val event = repository.getEvent(eventId)
            // 载入是异步的:若期间调用方又 initialize(新建),丢弃这次过期结果
            _uiState.compareAndSet(
                blank,
                blank.copy(
                    draft = event?.toDraft() ?: EventDefaults.draft(LocalDateTime.now()),
                    isEditing = event != null,
                ),
            )
        }
    }

    fun update(transform: (EventDraft) -> EventDraft) {
        _uiState.update { it.copy(draft = it.draft?.let(transform), errors = emptyList()) }
    }

    fun setTitle(value: String) = update { it.copy(title = value) }
    fun setLocation(value: String) = update { it.copy(location = value) }
    fun setNotes(value: String) = update { it.copy(notes = value) }
    fun setAllDay(value: Boolean) = update { it.copy(allDay = value) }
    fun setStart(value: LocalDateTime) = update { it.copy(start = value) }
    fun setEnd(value: LocalDateTime) = update { it.copy(end = value) }
    fun setColorSlot(slot: Int) = update { it.copy(colorSlot = slot) }
    fun setPriority(priority: Priority) = update { it.copy(priority = priority) }
    fun setReminderLead(minutes: Int?) = update { it.copy(reminderLeadMinutes = minutes) }

    /** 全天开关:开 = 规范形(起始日 00:00 → 结束日次日 00:00);关 = 恢复 09:00–10:00 */
    fun toggleAllDay(enabled: Boolean) = update { draft ->
        when {
            enabled && !draft.allDay -> EventDefaults.toAllDay(draft)
            !enabled && draft.allDay -> EventDefaults.fromAllDay(draft)
            else -> draft.copy(allDay = enabled)
        }
    }

    fun save() {
        val draft = _uiState.value.draft ?: return
        val errors = validate(normalizeAllDay(draft))
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            val normalized = normalizeAllDay(draft)
            val overlaps = OverlapChecker.find(repository, normalized)
            repository.save(
                Event(
                    id = draft.id ?: UUID.randomUUID().toString(),
                    title = normalized.title.trim(),
                    allDay = normalized.allDay,
                    start = normalized.start,
                    end = normalized.end,
                    location = normalized.location.ifBlank { null },
                    notes = normalized.notes.ifBlank { null },
                    colorSlot = normalized.colorSlot,
                    priority = normalized.priority,
                    reminderLeadMinutes = normalized.reminderLeadMinutes,
                    rule = normalized.rule,
                    // 0 = 新建(仓库补当前时间);编辑时透传原值,不得重写创建时间
                    createdAt = draft.createdAt,
                    updatedAt = 0L,
                ),
            )
            _uiState.update { it.copy(saved = true, overlapTitles = overlaps) }
        }
    }

    fun dismissOverlapNotice() = _uiState.update { it.copy(overlapTitles = emptyList()) }

    private fun Event.toDraft() = EventDraft(
        id = id,
        title = title,
        allDay = allDay,
        start = start,
        end = end,
        location = location.orEmpty(),
        notes = notes.orEmpty(),
        colorSlot = colorSlot,
        priority = priority,
        reminderLeadMinutes = reminderLeadMinutes,
        rule = rule,
        createdAt = createdAt,
    )

    companion object {
        const val KEY_START = "start"
        const val KEY_EVENT_ID = "eventId"

        /** 供预览/测试构造一个"不重复"规则 */
        val NoRule: EventRule = EventRule(RuleType.NONE, end = RuleEnd.Never)
    }
}
