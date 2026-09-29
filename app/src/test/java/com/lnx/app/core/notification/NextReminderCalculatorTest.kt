package com.lnx.app.core.notification

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleType
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提醒时刻计算(spec §3.8):提前 N 分钟、过期不补发、重复事件逐次 */
class NextReminderCalculatorTest {

    private fun event(
        id: String,
        start: String,
        end: String,
        lead: Int?,
        rule: EventRule = EventRule(),
    ) = Event(
        id = id,
        title = "会议",
        allDay = false,
        start = LocalDateTime.parse(start),
        end = LocalDateTime.parse(end),
        location = null,
        notes = null,
        colorSlot = 0,
        priority = Priority.P2,
        reminderLeadMinutes = lead,
        rule = rule,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun occ(e: Event, start: String, end: String, originalDate: java.time.LocalDate? = null) =
        Occurrence(e, LocalDateTime.parse(start), LocalDateTime.parse(end), originalDate)

    @Test
    fun `普通事件_提醒时刻等于开始前 lead 分钟`() {
        val e = event("e1", "2026-09-29T14:00", "2026-09-29T15:00", 15)
        val result = NextReminderCalculator.calculate(
            listOf(occ(e, "2026-09-29T14:00", "2026-09-29T15:00")),
            now = LocalDateTime.parse("2026-09-29T09:00"),
        )
        assertEquals(1, result.size)
        assertEquals("e1", result.single().eventId)
        assertEquals(LocalDateTime.parse("2026-09-29T13:45"), result.single().remindAt)
        assertEquals(LocalDateTime.parse("2026-09-29T14:00"), result.single().occurrenceStart)
    }

    @Test
    fun `不提醒的事件不进清单`() {
        val e = event("e1", "2026-09-29T14:00", "2026-09-29T15:00", null)
        assertTrue(
            NextReminderCalculator.calculate(
                listOf(occ(e, "2026-09-29T14:00", "2026-09-29T15:00")),
                now = LocalDateTime.parse("2026-09-29T09:00"),
            ).isEmpty(),
        )
    }

    @Test
    fun `提醒时刻已过的这次不补发`() {
        val e = event("e1", "2026-09-29T14:00", "2026-09-29T15:00", 15)
        val result = NextReminderCalculator.calculate(
            listOf(occ(e, "2026-09-29T14:00", "2026-09-29T15:00")),
            now = LocalDateTime.parse("2026-09-29T13:50"),
        )
        assertTrue("13:50 已过 13:45 的提醒点,不得补发", result.isEmpty())
    }

    @Test
    fun `提醒时刻恰好等于 now 时不排_避免到点立刻弹一条迟到的通知`() {
        val e = event("e1", "2026-09-29T14:00", "2026-09-29T15:00", 15)
        val result = NextReminderCalculator.calculate(
            listOf(occ(e, "2026-09-29T14:00", "2026-09-29T15:00")),
            now = LocalDateTime.parse("2026-09-29T13:45"),
        )
        assertTrue("已到点的这一条不再排(否则会立刻弹一条迟到的通知)", result.isEmpty())
    }

    @Test
    fun `重复事件的多次发生各排一条`() {
        // 展开器已经把"每周二"摊成多次发生,计算器不该再关心规则
        val master = event(
            "m1", "2026-09-29T09:00", "2026-09-29T09:30", 15,
            rule = EventRule(type = RuleType.WEEKLY),
        )
        val result = NextReminderCalculator.calculate(
            listOf(
                occ(master, "2026-09-29T09:00", "2026-09-29T09:30", java.time.LocalDate.parse("2026-09-29")),
                occ(master, "2026-10-06T09:00", "2026-10-06T09:30", java.time.LocalDate.parse("2026-10-06")),
                occ(master, "2026-10-13T09:00", "2026-10-13T09:30", java.time.LocalDate.parse("2026-10-13")),
            ),
            now = LocalDateTime.parse("2026-09-29T09:00"),
        )
        assertEquals(2, result.size) // 第一次的提醒点 08:45 已过,不补发
        assertEquals(
            listOf("2026-10-06T08:45", "2026-10-13T08:45"),
            result.map { it.remindAt.toString().substring(0, 16) },
        )
    }

    @Test
    fun `清单按提醒时刻升序`() {
        val a = event("a", "2026-09-29T18:00", "2026-09-29T19:00", 60)
        val b = event("b", "2026-09-29T15:00", "2026-09-29T16:00", 15)
        val result = NextReminderCalculator.calculate(
            listOf(
                occ(a, "2026-09-29T18:00", "2026-09-29T19:00"),
                occ(b, "2026-09-29T15:00", "2026-09-29T16:00"),
            ),
            now = LocalDateTime.parse("2026-09-29T09:00"),
        )
        assertEquals(listOf("b", "a"), result.map { it.eventId })
    }

    @Test
    fun `单次重排上限 50 条_超出按时间先后截断`() {
        val e = event("e1", "2026-09-29T10:00", "2026-09-29T10:30", 15)
        val many = (0 until 60).map { i ->
            val start = LocalDateTime.parse("2026-09-29T10:00").plusMinutes(i.toLong())
            occ(e.copy(id = "e$i"), start.toString().substring(0, 16), start.plusMinutes(30).toString().substring(0, 16))
        }
        val result = NextReminderCalculator.calculate(many, now = LocalDateTime.parse("2026-09-29T09:00"))
        assertEquals(50, result.size)
        assertEquals("e0", result.first().eventId)
        assertEquals("e49", result.last().eventId)
    }

    @Test
    fun `全天事件按开始日零点算提醒时刻`() {
        // 全天事件的 start 是当天 00:00,提前 15 分钟 = 前一天 23:45
        val e = event("e1", "2026-09-30T00:00", "2026-10-01T00:00", 15, rule = EventRule())
        val allDay = e.copy(allDay = true)
        val result = NextReminderCalculator.calculate(
            listOf(occ(allDay, "2026-09-30T00:00", "2026-10-01T00:00")),
            now = LocalDateTime.parse("2026-09-29T09:00"),
        )
        assertEquals(LocalDateTime.parse("2026-09-29T23:45"), result.single().remindAt)
    }
}
