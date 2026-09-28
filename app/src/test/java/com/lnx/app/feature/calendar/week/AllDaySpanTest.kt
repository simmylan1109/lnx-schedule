package com.lnx.app.feature.calendar.week

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全天/跨天事件在周视图 AllDayStrip 的条带布局(spec §3.3/§3.4):
 * 跨天连续延伸、裁剪到本周、重叠分行;列区间为 [startCol, endColExclusive)。
 */
class AllDaySpanTest {

    private val weekStart = LocalDate.of(2026, 9, 28) // 周一

    private fun occurrence(
        id: String,
        allDay: Boolean,
        start: LocalDateTime,
        end: LocalDateTime,
    ) = Occurrence(
        event = Event(
            id = id,
            title = "E$id",
            allDay = allDay,
            start = start,
            end = end,
            location = null,
            notes = null,
            colorSlot = 0,
            priority = Priority.P2,
            reminderLeadMinutes = null,
            rule = EventRule(RuleType.NONE, end = RuleEnd.Never),
            createdAt = 0L,
            updatedAt = 0L,
        ),
        start = start,
        end = end,
    )

    private fun allDay(first: LocalDate, lastInclusive: LocalDate) = occurrence(
        id = "$first-$lastInclusive",
        allDay = true,
        start = first.atStartOfDay(),
        end = lastInclusive.plusDays(1).atStartOfDay(), // 存储:结束日次日零点(排他)
    )

    private fun col(bar: AllDaySpan.AllDayBar) = bar.startCol to bar.endColExclusive

    @Test
    fun `单日全天事件占一格`() {
        val bars = AllDaySpan.layout(listOf(allDay(weekStart.plusDays(2), weekStart.plusDays(2))), weekStart)
        assertEquals(1, bars.size)
        assertEquals(2 to 3, col(bars.first()))
        assertEquals(0, bars.first().row)
    }

    @Test
    fun `跨天全天事件横向连续延伸`() {
        // 9-28 至 10-01(存储 10-02 零点排他) → 列 0..3
        val bars = AllDaySpan.layout(listOf(allDay(weekStart, LocalDate.of(2026, 10, 1))), weekStart)
        assertEquals(0 to 4, col(bars.first()))
    }

    @Test
    fun `定时跨零点事件也进全天条`() {
        val bars = AllDaySpan.layout(
            listOf(occurrence("x", false, weekStart.atTime(23, 0), weekStart.plusDays(1).atTime(1, 0))),
            weekStart,
        )
        assertEquals(0 to 2, col(bars.first()))
    }

    @Test
    fun `整周外的事件不进条带`() {
        val bars = AllDaySpan.layout(
            listOf(allDay(weekStart.minusDays(3), weekStart.minusDays(1))),
            weekStart,
        )
        assertTrue(bars.isEmpty())
    }

    @Test
    fun `跨入本周的事件裁剪到周内`() {
        // 9-25 至 9-30 → 本周只见 9-28/29/30 → 列 0..2
        val bars = AllDaySpan.layout(
            listOf(allDay(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 30))),
            weekStart,
        )
        assertEquals(0 to 3, col(bars.first()))
    }

    @Test
    fun `重叠的条带分两行_不相邻的共行`() {
        val a = allDay(weekStart, weekStart.plusDays(2)) // 0..3
        val b = allDay(weekStart.plusDays(1), weekStart.plusDays(3)) // 1..4,与 a 重叠 → row 1
        val c = allDay(weekStart.plusDays(4), weekStart.plusDays(4)) // 4..5,与 b 重叠?b 到 4(排他)→ c 从 4 起,不重叠 → row 0
        val bars = AllDaySpan.layout(listOf(a, b, c), weekStart)
        assertEquals(0, bars.first { it.occurrence.event.id == a.event.id }.row)
        assertEquals(1, bars.first { it.occurrence.event.id == b.event.id }.row)
        assertEquals(0, bars.first { it.occurrence.event.id == c.event.id }.row)
    }
}
