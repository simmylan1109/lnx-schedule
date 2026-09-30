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

    // —— spec §3.11 ① 周起始日可设为周日 ——
    // 这条是验收走查抓出来的洞:设置里能改、也落盘,但视图从来没读它(周视图永远从周一开始)

    @Test
    fun `周日起始时归到所在周的周日`() {
        // 2026-09-30 周三 → 周日起始的那一周从 9-27(周日)开始
        assertEquals(LocalDate.of(2026, 9, 27), weekStartOf(LocalDate.of(2026, 9, 30), mondayFirst = false))
        // 周日自己就是周首
        assertEquals(LocalDate.of(2026, 9, 27), weekStartOf(LocalDate.of(2026, 9, 27), mondayFirst = false))
        // 周六 10-03 属于 9-27 开始的那一周
        assertEquals(LocalDate.of(2026, 9, 27), weekStartOf(LocalDate.of(2026, 10, 3), mondayFirst = false))
    }

    @Test
    fun `两种周起始的页码各自独立且都能往返`() {
        val date = LocalDate.of(2026, 9, 30)
        val mondayPage = dateToPage(date, mondayFirst = true)
        val sundayPage = dateToPage(date, mondayFirst = false)

        assertEquals(LocalDate.of(2026, 9, 28), pageToDate(mondayPage, mondayFirst = true))
        assertEquals(LocalDate.of(2026, 9, 27), pageToDate(sundayPage, mondayFirst = false))
        // 同一天在两种周起始下必然落在"同一周"里:页码可差 1,但都不能跨出所在周
        check(sundayPage == mondayPage || sundayPage == mondayPage + 1) {
            "周一起始页=$mondayPage,周日起始页=$sundayPage,应相同或 +1"
        }
    }
}
