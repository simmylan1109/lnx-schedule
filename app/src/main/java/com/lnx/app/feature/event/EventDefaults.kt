package com.lnx.app.feature.event

import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 编辑页里的可变草稿(尚未落库) */
data class EventDraft(
    val id: String? = null,
    val title: String = "",
    val allDay: Boolean = false,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String = "",
    val notes: String = "",
    val colorSlot: Int = 0,
    val priority: Priority = Priority.P2,
    /** null = 不提醒 */
    val reminderLeadMinutes: Int? = 15,
    val rule: EventRule = EventRule(RuleType.NONE, end = RuleEnd.Never),
)

enum class ValidationError { TITLE_REQUIRED, END_NOT_AFTER_START }

fun validate(draft: EventDraft): List<ValidationError> = buildList {
    if (draft.title.isBlank()) add(ValidationError.TITLE_REQUIRED)
    if (draft.end <= draft.start) add(ValidationError.END_NOT_AFTER_START)
}

/**
 * 全天事件归一化(spec §4.2:全天 = 起始日 00:00 到结束日次日 00:00,排他)。
 * start 取开始日,end 取"用户选中的结束日次日零点";多日全天由 M3 的日期范围选择器提供。
 */
fun normalizeAllDay(draft: EventDraft): EventDraft {
    if (!draft.allDay) return draft
    return draft.copy(
        start = draft.start.toLocalDate().atStartOfDay(),
        end = draft.end.toLocalDate().plusDays(1).atStartOfDay(),
    )
}

/** 新建/编辑的默认值与预填规则(spec §3.5),纯函数便于单测 */
object EventDefaults {
    const val DEFAULT_DURATION_MINUTES = 60L
    private const val DEFAULT_REMINDER_MINUTES = 15

    /**
     * 入口预填(spec §3.5):
     * - FAB:所选日期;若该日是今天,取**下一个半点**(14:23 → 14:30);
     * - 其他日期:该日 09:00。
     */
    fun startFor(selectedDate: LocalDate, now: LocalTime, today: LocalDate = LocalDate.now()): LocalDateTime {
        if (selectedDate != today) return selectedDate.atTime(9, 0)
        val minuteOfDay = now.hour * 60 + now.minute
        // "下一个半点"是严格下一个:14:23 → 14:30,14:30 → 15:00(用 ceil 会在整点半点停在原地)
        val half = (minuteOfDay / 30 + 1) * 30
        // 23:30 之后没有下一个半点,回落到当日最后一个整点前的半点
        val clamped = half.coerceAtMost(24 * 60 - 30)
        return selectedDate.atStartOfDay().plusMinutes(clamped.toLong())
    }

    fun draft(start: LocalDateTime): EventDraft = EventDraft(
        start = start,
        end = start.plusMinutes(DEFAULT_DURATION_MINUTES),
        reminderLeadMinutes = DEFAULT_REMINDER_MINUTES,
    )
}
