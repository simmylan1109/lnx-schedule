package com.lnx.app.feature.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.TagRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.model.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    /** 全部可选标签(M3 spec §3.10) */
    val tags: List<Tag> = emptyList(),
    /** 该事件已选中的标签 */
    val selectedTagIds: Set<String> = emptySet(),
    /** 新建标签失败的原因(重名等),给对话框内联显示 */
    val tagCreateError: String? = null,
)

@HiltViewModel
class EventEditViewModel @Inject constructor(
    private val repository: EventRepository,
    private val tagRepository: TagRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val editingId: String? = savedStateHandle.get<String>(KEY_EVENT_ID)

    private val _uiState = MutableStateFlow(EventEditUiState())
    val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    init {
        // 标签清单常驻刷新(新建标签对话框、其他入口改名/删除都要跟)
        viewModelScope.launch {
            tagRepository.observeTags().collect { tags ->
                _uiState.update { it.copy(tags = tags) }
            }
        }
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
            val tagIds = tagRepository.observeTagsOfEvent(eventId).first().map { it.id }.toSet()
            // 载入是异步的:若期间调用方又 initialize(新建),丢弃这次过期结果
            _uiState.compareAndSet(
                blank,
                blank.copy(
                    draft = event?.toDraft() ?: EventDefaults.draft(LocalDateTime.now()),
                    isEditing = event != null,
                    selectedTagIds = tagIds,
                ),
            )
        }
    }

    /** 多选标签:已选则取消,未选则加入(spec §3.10 多对多) */
    fun toggleTag(id: String) = _uiState.update { s ->
        val next = s.selectedTagIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        s.copy(selectedTagIds = next)
    }

    /**
     * 新建标签并自动选中。返回是否成功:重名等失败要把原因留在对话框里让用户改,
     * 不能弹一下就没了(静默失败过一次,结果是事件挂上不存在的标签 id,界面里再也藏不掉它)。
     */
    suspend fun createTag(name: String, colorSlot: Int): Boolean =
        tagRepository.createTag(name, colorSlot).fold(
            onSuccess = { tag ->
                _uiState.update {
                    it.copy(selectedTagIds = it.selectedTagIds + tag.id, tagCreateError = null)
                }
                true
            },
            onFailure = { e ->
                _uiState.update { it.copy(tagCreateError = e.message ?: "创建标签失败") }
                false
            },
        )

    fun clearTagCreateError() = _uiState.update { it.copy(tagCreateError = null) }

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
    fun setRule(rule: EventRule) = update { it.copy(rule = rule) }

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
            // 先定 id 再落库,标签关联才能指向同一条事件
            val id = draft.id ?: UUID.randomUUID().toString()
            repository.save(
                Event(
                    id = id,
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
            tagRepository.setEventTags(id, _uiState.value.selectedTagIds.toList())
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
