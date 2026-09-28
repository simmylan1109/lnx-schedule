package com.lnx.app.core.data

import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.MonthlyMode
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val zone: ZoneId get() = ZoneId.systemDefault()

fun Event.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title,
    allDay = allDay,
    startAt = start.toEpochMillis(),
    endAt = end.toEpochMillis(),
    location = location,
    notes = notes,
    colorSlot = colorSlot,
    priority = priority.name,
    reminderLeadMinutes = reminderLeadMinutes,
    ruleType = rule.type.name,
    // 归一化:非重复事件的 interval 无意义,统一写 1,避免"写 5 读回 1"的有损往返
    ruleInterval = if (rule.type == RuleType.NONE) 1 else rule.interval.coerceAtLeast(1),
    ruleWeekdays = rule.weekdays.takeIf { it.isNotEmpty() }?.joinToString(",") { it.value.toString() },
    ruleMonthlyMode = rule.monthlyMode?.name,
    ruleMonthlyDay = rule.monthlyDay,
    ruleMonthlyNth = rule.monthlyNth,
    ruleMonthlyWeekday = rule.monthlyWeekday?.value,
    ruleEndType = rule.end.typeName(),
    ruleEndDate = (rule.end as? RuleEnd.Until)?.date?.toEpochDay(),
    ruleCount = (rule.end as? RuleEnd.Count)?.times,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = false,
)

fun EventEntity.toEvent(): Event = Event(
    id = id,
    title = title,
    allDay = allDay,
    start = startAt.toLocalDateTime(),
    end = endAt.toLocalDateTime(),
    location = location,
    notes = notes,
    colorSlot = colorSlot,
    priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.P2),
    reminderLeadMinutes = reminderLeadMinutes,
    rule = toRule(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun EventEntity.toRule(): EventRule {
    val type = runCatching { RuleType.valueOf(ruleType) }.getOrDefault(RuleType.NONE)
    if (type == RuleType.NONE) return EventRule()
    // 脏数据(导入/手改)不能让整个流崩掉:非法星期号丢弃而非抛异常,
    // 与下面 monthlyWeekday 的处理保持一致
    val weekdays = ruleWeekdays
        ?.split(',')
        ?.mapNotNull { it.trim().toIntOrNull() }
        ?.mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }
        ?.toSet()
        ?: emptySet()
    val end: RuleEnd = when (ruleEndType) {
        "UNTIL" -> ruleEndDate?.let { RuleEnd.Until(LocalDate.ofEpochDay(it)) } ?: RuleEnd.Never
        "COUNT" -> ruleCount?.let { RuleEnd.Count(it) } ?: RuleEnd.Never
        else -> RuleEnd.Never
    }
    return EventRule(
        type = type,
        interval = ruleInterval.coerceAtLeast(1),
        weekdays = weekdays,
        monthlyMode = ruleMonthlyMode?.let { runCatching { MonthlyMode.valueOf(it) }.getOrNull() },
        monthlyDay = ruleMonthlyDay,
        monthlyNth = ruleMonthlyNth,
        monthlyWeekday = ruleMonthlyWeekday?.let { runCatching { DayOfWeek.of(it) }.getOrNull() },
        end = end,
    )
}

/** `RuleEnd.Never` 恒写 null("永不结束"只有一种表示),读回时 null 与未知值都落到 Never */
private fun RuleEnd.typeName(): String? = when (this) {
    is RuleEnd.Never -> null
    is RuleEnd.Until -> "UNTIL"
    is RuleEnd.Count -> "COUNT"
}
