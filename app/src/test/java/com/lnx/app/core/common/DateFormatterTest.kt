package com.lnx.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateFormatterTest {
    @Test
    fun `标题格式为 M月 DD日 周X`() {
        assertEquals("9月 · 30日 周三", formatTitle(LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun `周一到周日的中文映射`() {
        assertEquals("周一", formatTitle(LocalDate.of(2026, 9, 28)).takeLast(2))
        assertEquals("周日", formatTitle(LocalDate.of(2026, 10, 4)).takeLast(2))
    }

    @Test
    fun `年份变化不影响格式`() {
        assertEquals("1月 · 1日 周四", formatTitle(LocalDate.of(2026, 1, 1)))
    }
}
