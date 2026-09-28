package com.lnx.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekLogicTest {
    @Test
    fun `任意日期归到所在周的周一`() {
        // 2026-09-30 是周三
        assertEquals(LocalDate.of(2026, 9, 28), weekStartOf(LocalDate.of(2026, 9, 30)))
        // 周日 2026-10-04 属于 9-28 开始的那一周
        assertEquals(LocalDate.of(2026, 9, 28), weekStartOf(LocalDate.of(2026, 10, 4)))
        // 周一自己
        assertEquals(LocalDate.of(2026, 9, 28), weekStartOf(LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun `页面与日期互转 间隔一周`() {
        val base = LocalDate.of(2026, 9, 28)
        val p0 = dateToPage(base)
        assertEquals(base.plusWeeks(3), pageToDate(p0 + 3))
        assertEquals(base.minusWeeks(2), pageToDate(p0 - 2))
    }
}
