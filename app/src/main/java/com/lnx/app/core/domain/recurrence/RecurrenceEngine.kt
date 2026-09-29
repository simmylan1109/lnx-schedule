package com.lnx.app.core.domain.recurrence

import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.MonthlyMode
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** 一次发生的起止。引擎只做时间数学,不携带事件;展开器负责把槽位包成 Occurrence。 */
data class RecurrenceSlot(val start: LocalDateTime, val end: LocalDateTime)

/**
 * 重复展开引擎(spec §3.7/§4.4)。纯函数:不读时钟、不碰数据库,M5 排提醒直接复用。
 *
 * 语义钉子:
 * - 半开区间:发生与 [rangeStart, rangeEnd) 有交集(occurrence.end > rangeStart && occurrence.start < rangeEnd);
 * - "N 次"从系列**第一次发生**起计数,区间外的照样计(spec §4.4);
 * - UNTIL 含当天;
 * - 每周按 ISO 周(周一起始,spec §3.4)锚定系列起点所在周;多选星期几时,起点周内**晚于系列起点**的星期照常发生;
 * - 日期不存在就跳过:BY_MONTHDAY 31 在短月不发生;每年 2/29 在平年不发生(与"月长不足跳过"同一条规则,RFC 5545 同款);
 * - 每周未选星期几(脏数据/编辑中间态)回退为系列起点的星期几。
 *
 * 性能:候选日期单调递增,起点可用"区间起点 - 时长"快进(不计数的规则才快进,
 * Count 必须从系列第一次数起);整体迭代上限 [MAX_ITERATIONS],防御畸形规则。
 */
object RecurrenceEngine {

    /** 单次 expand 的迭代上限:触发即截断,绝不挂死 */
    const val MAX_ITERATIONS = 100_000

    fun expand(
        rule: EventRule,
        seriesStart: LocalDateTime,
        occurrenceDuration: Duration,
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime,
    ): List<RecurrenceSlot> {
        if (rule.type == RuleType.NONE || rangeEnd <= rangeStart) return emptyList()
        // 脏数据防线:Count(0/负)按"已经数满"处理 → 一次都不该发生;
        // 老代码 takeIf{>0} 会把它当"没有限制"变成永不结束,方向正好反了
        val countLimit = (rule.end as? RuleEnd.Count)?.times
        if (countLimit != null && countLimit <= 0) return emptyList()
        val untilDate = (rule.end as? RuleEnd.Until)?.date
        // Count 必须从系列第一次数起,不能快进
        val canFastForward = countLimit == null

        val slots = mutableListOf<RecurrenceSlot>()
        var count = 0

        // 候选日期只增不减:开始时间越过区间终点(或越过 UNTIL)即无后续重叠,停止枚举
        fun emit(date: LocalDate): Boolean {
            val start = date.atTime(seriesStart.toLocalTime())
            if (start < seriesStart) return true // 系列还没开始:跳过且不计数
            if (untilDate != null && date > untilDate) return false
            if (start >= rangeEnd) return false
            val end = start.plus(occurrenceDuration)
            if (end > rangeStart) slots += RecurrenceSlot(start, end)
            if (countLimit != null && ++count >= countLimit) return false
            return true
        }

        when (rule.type) {
            RuleType.DAILY -> {
                val interval = rule.interval.coerceAtLeast(1)
                var epoch = seriesStart.toLocalDate().toEpochDay()
                if (canFastForward) epoch += fastForwardDays(epoch, (rangeStart - occurrenceDuration).toLocalDate().toEpochDay(), interval)
                var guard = 0
                while (guard++ < MAX_ITERATIONS) {
                    if (!emit(LocalDate.ofEpochDay(epoch))) break
                    epoch += interval
                }
            }

            RuleType.WEEKLY -> {
                val interval = rule.interval.coerceAtLeast(1)
                val weekdays = (rule.weekdays.ifEmpty { setOf(seriesStart.dayOfWeek) }).sortedBy { it.value }
                val anchor = seriesStart.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                var weekEpoch = anchor.toEpochDay()
                if (canFastForward) {
                    val minWeek = (rangeStart - occurrenceDuration).toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    if (minWeek > anchor) {
                        val weeks = ChronoUnit.WEEKS.between(anchor, minWeek)
                        weekEpoch += (weeks / interval) * interval * 7L // 向下取整到间隔的整数倍,宁多勿漏
                    }
                }
                var guard = 0
                while (guard++ < MAX_ITERATIONS) {
                    val weekStart = LocalDate.ofEpochDay(weekEpoch)
                    for (weekday in weekdays) {
                        if (!emit(weekStart.plusDays((weekday.value - 1).toLong()))) return slots
                    }
                    weekEpoch += interval * 7L
                }
            }

            RuleType.MONTHLY -> {
                val interval = rule.interval.coerceAtLeast(1)
                var index = seriesStart.year * 12 + seriesStart.monthValue - 1
                if (canFastForward) {
                    val min = rangeStart - occurrenceDuration
                    val minIndex = min.year * 12 + min.monthValue - 1
                    if (minIndex > index) index += (minIndex - index) / interval * interval // 向下取整
                }
                var guard = 0
                while (guard++ < MAX_ITERATIONS) {
                    val first = LocalDate.of(index / 12, index % 12 + 1, 1)
                    val date = monthlyDate(first, rule, seriesStart)
                    if (date != null && !emit(date)) return slots
                    index += interval
                }
            }

            RuleType.YEARLY -> {
                val interval = rule.interval.coerceAtLeast(1)
                var year = seriesStart.year
                if (canFastForward) {
                    val minYear = (rangeStart - occurrenceDuration).year
                    if (minYear > year) year += (minYear - year) / interval * interval // 向下取整
                }
                var guard = 0
                while (guard++ < MAX_ITERATIONS) {
                    val date = yearlyDate(year, seriesStart)
                    if (date != null && !emit(date)) return slots
                    year += interval
                }
            }

            RuleType.NONE -> Unit
        }
        return slots
    }

    /** 把候选起点快进到不早于 [minEpochDay] 的间隔整数倍位置(向下取整,重叠过滤兜底) */
    private fun fastForwardDays(fromEpochDay: Long, minEpochDay: Long, interval: Int): Long {
        if (minEpochDay <= fromEpochDay) return 0L
        val diff = minEpochDay - fromEpochDay
        return diff / interval * interval
    }

    /** 月内发生日:BY_NTH_WEEKDAY 第 N 个星期X(不存在返回 null);BY_MONTHDAY 指定日(短月返回 null);缺省沿用系列起点日 */
    private fun monthlyDate(monthFirst: LocalDate, rule: EventRule, seriesStart: LocalDateTime): LocalDate? = when (rule.monthlyMode) {
        MonthlyMode.BY_NTH_WEEKDAY -> {
            val weekday = rule.monthlyWeekday ?: seriesStart.dayOfWeek
            val nth = rule.monthlyNth?.takeIf { it in 1..5 } ?: 1
            val firstHit = monthFirst.with(TemporalAdjusters.nextOrSame(weekday))
            val nthHit = firstHit.plusDays((nth - 1) * 7L)
            nthHit.takeIf { it.month == monthFirst.month }
        }

        else -> {
            val day = rule.monthlyDay ?: seriesStart.dayOfMonth
            day.takeIf { it in 1..monthFirst.lengthOfMonth() }?.let { monthFirst.withDayOfMonth(it) }
        }
    }

    /** 年内发生日:月/日沿用系列起点;2/29 在平年不存在 → 返回 null 跳过 */
    private fun yearlyDate(year: Int, seriesStart: LocalDateTime): LocalDate? {
        val month = seriesStart.monthValue
        val day = seriesStart.dayOfMonth
        if (day > 28) {
            val length = LocalDate.of(year, month, 1).lengthOfMonth()
            if (day > length) return null
        }
        return LocalDate.of(year, month, day)
    }
}
