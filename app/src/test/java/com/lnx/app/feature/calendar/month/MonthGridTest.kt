package com.lnx.app.feature.calendar.month

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 月历格子(spec §3.4:周一起始;M3 裁决:固定 6 行 × 7 列 = 42 格,不足补前后月,
 * 保证月与月之间网格高度稳定不跳)。
 */
class MonthGridTest {

    @Test
    fun `2026年9月_首格是8月31日且共42格`() {
        val cells = monthCells(YearMonth.of(2026, 9))
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 8, 31), cells.first())
        assertEquals(LocalDate.of(2026, 10, 11), cells.last()) // 8-31 + 41
    }

    @Test
    fun `2026年10月_只有5周的月份也补足42格`() {
        val cells = monthCells(YearMonth.of(2026, 10)) // 10-01 周四,首格 9-28
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 9, 28), cells.first())
        assertEquals(LocalDate.of(2026, 11, 8), cells.last())
    }

    @Test
    fun `格子连续且每周一对齐`() {
        val cells = monthCells(YearMonth.of(2026, 9))
        cells.zipWithNext().forEach { (a, b) ->
            assertEquals(a.plusDays(1), b) // 逐日连续
        }
        cells.filterIndexed { i, _ -> i % 7 == 0 }.forEach {
            assertEquals(java.time.DayOfWeek.MONDAY, it.dayOfWeek) // 每行从周一开始
        }
    }

    @Test
    fun `本月日子落在正确位置`() {
        val cells = monthCells(YearMonth.of(2026, 9))
        // 9-01 周二 → 索引 1;9-30 周三 → 索引 30
        assertEquals(LocalDate.of(2026, 9, 1), cells[1])
        assertEquals(LocalDate.of(2026, 9, 30), cells[30])
    }
}
