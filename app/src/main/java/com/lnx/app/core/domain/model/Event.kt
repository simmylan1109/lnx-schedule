package com.lnx.app.core.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** 事件优先级(spec §3.5),P0 最高 */
enum class Priority(val label: String) { P0("P0"), P1("P1"), P2("P2"), P3("P3") }

enum class RuleType { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

enum class MonthlyMode { BY_MONTHDAY, BY_NTH_WEEKDAY }

/** 重复结束条件(spec §3.7 三选一) */
sealed interface RuleEnd {
    /** 永不结束;非重复事件也用这个值,与数据库中 ruleEndType = null 对应 */
    data object Never : RuleEnd

    /** 到指定日期结束(含当天) */
    data class Until(val date: LocalDate) : RuleEnd

    /** 重复 N 次后结束 */
    data class Count(val times: Int) : RuleEnd
}

/** 重复规则(spec §3.7);type = NONE 时其余字段全部忽略 */
data class EventRule(
    val type: RuleType = RuleType.NONE,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val monthlyMode: MonthlyMode? = null,
    val monthlyDay: Int? = null,
    val monthlyNth: Int? = null,
    val monthlyWeekday: DayOfWeek? = null,
    val end: RuleEnd = RuleEnd.Never,
)

/**
 * 领域事件(spec §4.2)。
 * 全天事件:start 为起始日 00:00,end 为结束日次日 00:00(排他)。
 */
data class Event(
    val id: String,
    val title: String,
    val allDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String?,
    val notes: String?,
    val colorSlot: Int,
    val priority: Priority,
    val reminderLeadMinutes: Int?,
    val rule: EventRule,
    val createdAt: Long,
    val updatedAt: Long,
)
