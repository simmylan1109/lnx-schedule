package com.lnx.app.feature.calendar.month

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历格子(spec §3.4:周一起始;M3 裁决:固定 6 行 × 7 列 = 42 格,
 * 不足的月份用前后月补位,保证月与月之间网格高度稳定不跳)。
 */
fun monthCells(
    month: YearMonth,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
): List<LocalDate> {
    val firstOfMonth = month.atDay(1)
    // 首格 = 本月 1 日所在周的周起始日(可能落在上月)
    val firstCell = firstOfMonth.minusDays(((firstOfMonth.dayOfWeek.value + 7 - weekStart.value) % 7).toLong())
    return (0 until 42).map { firstCell.plusDays(it.toLong()) }
}
