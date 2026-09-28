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
 * 全天事件的存储规范(spec §4.2):start = 起始日 00:00,end = 结束日**次日** 00:00(排他)。
 * 草稿在切换全天开关时立即落成规范形(toAllDay),结束日期选择器的显示/写入都按排他语义;
 * normalizeAllDay 只做兜底钳制,不再平移 +1(否则会对已排他的结束时间双重平移)。
 */
fun normalizeAllDay(draft: EventDraft): EventDraft {
    if (!draft.allDay) return draft
    val start = draft.start.toLocalDate().atStartOfDay()
    val end = draft.end.toLocalDate().atStartOfDay()
    return draft.copy(
        start = start,
        end = if (end.isAfter(start)) end else start.plusDays(1),
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

    /** 定时 → 全天:单日按当天;跨零点的定时事件按起止两天转为全天 */
    fun toAllDay(draft: EventDraft): EventDraft {
        val firstDay = draft.start.toLocalDate()
        val lastDay = maxOf(draft.end.toLocalDate(), firstDay)
        return draft.copy(
            allDay = true,
            start = firstDay.atStartOfDay(),
            end = lastDay.plusDays(1).atStartOfDay(),
        )
    }

    /** 全天 → 定时:恢复 09:00–10:00,避免留下 00:00 的时间 */
    fun fromAllDay(draft: EventDraft): EventDraft = draft.copy(
        allDay = false,
        start = draft.start.toLocalDate().atTime(9, 0),
        end = draft.start.toLocalDate().atTime(10, 0),
    )
}
