package com.lnx.app.feature.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.TagRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.model.Tag
import com.lnx.app.core.domain.model.TagNameError
import com.lnx.app.core.domain.model.TagNameException
import com.lnx.app.core.domain.recurrence.EditScope
import com.lnx.app.core.domain.recurrence.RecurrenceEditHandler
import com.lnx.app.core.notification.ReminderPlanner
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

    /** 新建标签对话框里的错误;`name` 用于重名提示里回显用户输入的名字。`kind` 为 null = 非预期的失败 */
    data class TagCreateError(val kind: TagNameError?, val name: String? = null)

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
    val tagCreateError: TagCreateError? = null,
    /** 用户刚设了提醒 → 该问一次通知权限了(spec §3.8),编辑器消费后置回 false */
    val askNotificationPermission: Boolean = false,
    /**
     * 非 null = 正在改重复事件的某一次(spec §4.4),保存时按作用范围落库:
     * THIS_ONLY 写例外、THIS_AND_FUTURE 剪断+新建;"全部"不带 context(直接改母事件)。
     */
    val editContext: EditContext? = null,
)

/** 编辑上下文:正在改 [masterId] 系列的 [originalDate] 那一次 */
data class EditContext(
    val masterId: String,
    val originalDate: LocalDate,
    val scope: EditScope,
)

@HiltViewModel
class EventEditViewModel @Inject constructor(
    private val repository: EventRepository,
    private val tagRepository: TagRepository,
    private val recurrenceHandler: RecurrenceEditHandler,
    private val reminderPlanner: ReminderPlanner,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val editingId: String? = savedStateHandle.get<String>(KEY_EVENT_ID)

    /** 本次编辑会话内是否已弹过通知权限(避免保存时二次弹) */
    private var permissionAskedInSession = false

    private val _uiState = MutableStateFlow(EventEditUiState())
    val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    /**
     * 编辑会话号:每次进入编辑器 +1。异步载入完草稿后**只认当前会话**的结果,
     * 上一轮晚到的结果直接丢弃。
     *
     * 这里必须用会话号,不能用 `compareAndSet(整份旧状态, 新状态)`:
     * 标签采集协程随时在改同一份 state(它比初始化先拿到标签,或用户刚建过标签),
     * 一旦它插在中间写一笔,整份 state 就不再等于当初的"空状态",
     * compareAndSet 便永远失败 —— 实测表现是**编辑器永远画不出来**(标题栏不出现,超时 10s)。
     */
    private var session = 0

    /**
     * 新建草稿(spec §3.5:提醒 = 设置里的默认值,出厂 15)。
     *
     * 档位**运行时从设置读**,不再用编译期常量 —— 常量和设置页各写一份默认值就是"双源":
     * 用户在设置里改成 30,新建事件却还是 15。读失败(文件损坏/IO 异常)才回落出厂值,
     * 读不到设置不该让编辑器开不出来。
     */
    private suspend fun newDraft(start: LocalDateTime): EventDraft = EventDefaults.draft(
        start = start,
        defaultLead = runCatching { settingsRepository.current().reminderLeadMinutes }
            .getOrDefault(SettingsDefaults.REMINDER_LEAD_MINUTES),
    )

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
                        draft = event?.toDraft() ?: newDraft(navStart ?: LocalDateTime.now()),
                        isEditing = true,
                    )
                }
            }
        } else if (navStart != null) {
            val sessionId = ++session
            _uiState.value = EventEditUiState()
            viewModelScope.launch {
                val draft = newDraft(navStart)
                if (sessionId == session) _uiState.update { it.copy(draft = draft) }
            }
        }
    }

    /**
     * 新建入口:由调用方给定预填时间(spec §3.5 的三入口规则)。
     * 无导航图时 ViewModel 挂在 Activity 上、跨多次打开存活,
     * 所以这里必须**无条件**重置本会话的状态——否则第二次打开会残留上一次的草稿。
     *
     * 草稿要等设置读回来才建(默认提醒档位来自设置),期间 `draft` 为 null、
     * 编辑器渲染空壳(见 [EventEditScreen]);先清空再等,免得那几毫秒里留着上一份草稿。
     *
     * **标签清单要留着**:它来自常驻订阅,只在标签表变动时才重新吐值。
     * 整份清零的话,第二次打开编辑器标签区就是空的,得等用户新建/删除一个标签才恢复。
     */
    fun initialize(start: LocalDateTime) {
        permissionAskedInSession = false // 新的一次编辑 → 重新获得一次索权机会
        val sessionId = ++session
        val carried = _uiState.value
        _uiState.value = EventEditUiState(tags = carried.tags, tagCreateError = carried.tagCreateError)
        viewModelScope.launch {
            val draft = newDraft(start)
            // 期间调用方又 initialize 了(用户关掉又开)就丢弃这次,别覆盖新一轮
            if (sessionId == session) _uiState.update { it.copy(draft = draft) }
        }
    }

    /** 编辑入口:按 id 载入既有事件;事件已被删除时退化为新建 */
    fun initializeEvent(eventId: String) {
        permissionAskedInSession = false
        val sessionId = ++session
        // 同 [initialize]:标签清单是常驻订阅的产物,别整份清零(见那里的说明)
        val carried = _uiState.value
        _uiState.value = EventEditUiState(tags = carried.tags, tagCreateError = carried.tagCreateError)
        viewModelScope.launch {
            val event = repository.getEvent(eventId)
            val tagIds = tagRepository.observeTagsOfEvent(eventId).first().map { it.id }.toSet()
            // 载入是异步的:若期间调用方又 initialize(新建),丢弃这次过期结果(见 [session])
            if (sessionId == session) {
                _uiState.update {
                    it.copy(
                        draft = event?.toDraft() ?: newDraft(LocalDateTime.now()),
                        isEditing = event != null,
                        selectedTagIds = tagIds,
                    )
                }
            }
        }
    }

    /**
     * 编辑重复事件的**某一次**("仅本次"/"本次及以后",spec §4.4)。
     * 草稿 = 该次发生的时间(不是系列首例),但带着母事件的规则;
     * 保存时按 [EditContext.scope] 走例外/剪断,不直接写母事件。
     */
    fun initializeOccurrenceEdit(occ: Occurrence, scope: EditScope) {
        permissionAskedInSession = false
        val master = occ.event
        _uiState.value = EventEditUiState(
            draft = EventDraft(
                id = null, // 不直接写母事件:落库路径由 editContext 决定
                title = master.title,
                allDay = master.allDay,
                start = occ.start,
                end = occ.end,
                location = master.location.orEmpty(),
                notes = master.notes.orEmpty(),
                colorSlot = master.colorSlot,
                priority = master.priority,
                reminderLeadMinutes = master.reminderLeadMinutes,
                rule = master.rule,
                createdAt = master.createdAt,
            ),
            isEditing = true,
            editContext = EditContext(master.id, occ.originalDate ?: occ.start.toLocalDate(), scope),
        )
        viewModelScope.launch {
            val tagIds = tagRepository.observeTagsOfEvent(master.id).first().map { it.id }.toSet()
            // 只在仍是同一次编辑时回填(期间用户又改入口则丢弃)
            _uiState.update { if (it.editContext?.masterId == master.id) it.copy(selectedTagIds = tagIds) else it }
        }
    }

    /** 多选标签:已选则取消,未选则加入(spec §3.10 多对多) */
    fun toggleTag(id: String) = _uiState.update { s ->
        val next = s.selectedTagIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        s.copy(selectedTagIds = next)
    }

    /** 新建标签并自动选中。返回是否成功:重名等失败要把原因留在对话框里让用户改,
     * 不能弹一下就没了(静默失败过一次,结果是事件挂上不存在的标签 id,界面里再也藏不掉它)。
     *
     * 失败只报**错误类型**([TagNameError]),中文/英文句子由界面按当前语言渲染 ——
     * ViewModel 里存句子就等于把中文焊死在数据流上(换语言后报错仍是中文)。
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
                val error = when (e) {
                    is TagNameException -> TagCreateError(e.kind, e.name)
                    else -> TagCreateError(null) // 非预期异常:界面显示通用失败文案
                }
                _uiState.update { it.copy(tagCreateError = error) }
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

    fun setReminderLead(minutes: Int?) {
        update { it.copy(reminderLeadMinutes = minutes) }
        // 用户主动设提醒是最自然的索权时机;选"不提醒"不该弹权限框
        if (minutes != null) askNotificationPermissionOnce()
    }

    /**
     * 本次编辑会话内只问一次通知权限。
     *
     * spec §3.5 规定新建事件的草稿**默认就带提醒**(档位取设置,出厂 15),
     * 所以光靠"用户点了提醒档位"索权会漏掉主路径:用户不碰提醒行直接保存,
     * 事件有提醒、闹钟也排了,却从没申请过 `POST_NOTIFICATIONS`,通知永远不会出现。
     * 保存时也要兜一次底(见 [save])。
     */
    private fun askNotificationPermissionOnce() {
        if (permissionAskedInSession) return
        permissionAskedInSession = true
        _uiState.update { it.copy(askNotificationPermission = true) }
    }

    fun consumeNotificationPermissionRequest() =
        _uiState.update { it.copy(askNotificationPermission = false) }

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
            val event = Event(
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
            )
            val context = _uiState.value.editContext
            val selected = _uiState.value.selectedTagIds.toList()
            try {
                if (context == null) {
                    repository.save(event)
                    tagRepository.setEventTags(id, selected)
                } else when (context.scope) {
                    EditScope.ALL -> {
                        // "全部":改母事件本身(id/createdAt 保持)
                        val master = event.copy(id = context.masterId, createdAt = draft.createdAt)
                        repository.save(master)
                        tagRepository.setEventTags(context.masterId, selected)
                    }

                    EditScope.THIS_ONLY -> {
                        // 仅本次:写例外(全字段覆盖,规则强制沿用母事件);标签挂母事件,不动
                        val master = repository.getEvent(context.masterId)
                        repository.upsertException(
                            EventException(
                                masterId = context.masterId,
                                originalDate = context.originalDate,
                                override = event.copy(id = context.masterId, rule = master?.rule ?: draft.rule),
                            ),
                        )
                    }

                    EditScope.THIS_AND_FUTURE -> {
                        // 本次及以后:新母事件自持一个 id,标签跟着挂过去
                        val master = repository.getEvent(context.masterId) ?: return@launch
                        val newMaster = event.copy(id = UUID.randomUUID().toString(), createdAt = 0L)
                        recurrenceHandler.apply(master, context.originalDate, newMaster, EditScope.THIS_AND_FUTURE)
                        tagRepository.setEventTags(newMaster.id, selected)
                    }
                }
            } finally {
                // 提醒重排(spec §3.8):新建/改期/改规则/剪断都会改变"哪些发生还带着提醒",
                // 所以放 finally —— 上面 THIS_AND_FUTURE 遇到母事件已被删会提前 return,
                // 放到分支之后就跟着跳过了
                reminderPlanner.reschedule()
            }
            // 草稿默认就带 15 分钟提醒(spec §3.5),用户完全可能不碰提醒行直接保存;
            // 这里兜底索权,否则事件有提醒、闹钟也排了,通知却永远发不出来
            if (event.reminderLeadMinutes != null) askNotificationPermissionOnce()
            _uiState.update { it.copy(saved = true, overlapTitles = overlaps) }
        }
    }

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
