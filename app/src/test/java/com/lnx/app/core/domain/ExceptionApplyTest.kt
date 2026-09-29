package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 例外应用(spec §4.3/§4.4):取消的隐藏,改过的按 override 显示,
 * 改期搬进窗口的例外也补回;例外不改规则本身。
 */
class ExceptionApplyTest {
    private val expander = DefaultOccurrenceExpander()
    private val rangeS = LocalDateTime.parse("2026-10-01T00:00")
    private val rangeE = LocalDateTime.parse("2026-11-01T00:00")

    private val weekly = Event(
        id = "w1",
        title = "周会",
        allDay = false,
        start = LocalDateTime.parse("2026-09-30T09:00"),
        end = LocalDateTime.parse("2026-09-30T10:00"),
        location = null,
        notes = null,
        colorSlot = 2,
        priority = Priority.P2,
        reminderLeadMinutes = null,
        rule = EventRule(RuleType.WEEKLY, weekdays = setOf(DayOfWeek.WEDNESDAY)),
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun expand(exceptions: List<EventException>): List<Occurrence> =
        expander.expand(listOf(weekly), exceptions, rangeS, rangeE)

    private fun overrideEvent(date: String, hour: Int): Event = weekly.copy(
        start = LocalDateTime.parse("$date" + "T${hour.toString().padStart(2, '0')}:00"),
        end = LocalDateTime.parse("$date" + "T${(hour + 1).toString().padStart(2, '0')}:00"),
    )

    @Test
    fun `取消的那次不出现_其余照常`() {
        val list = expand(listOf(EventException(masterId = "w1", originalDate = LocalDate.parse("2026-10-14"), cancelled = true)))
        assertEquals(listOf(7, 21, 28), list.map { it.start.dayOfMonth })
    }

    @Test
    fun `改过的一次按override显示_其余不变`() {
        val list = expand(
            listOf(
                EventException(
                    masterId = "w1",
                    originalDate = LocalDate.parse("2026-10-14"),
                    override = overrideEvent("2026-10-14", 14),
                ),
            ),
        )
        // 10-14 那次 14:00,其余 09:00
        assertEquals(4, list.size)
        val moved = list.first { it.start.dayOfMonth == 14 }
        assertEquals(14, moved.start.hour)
        assertEquals(14, moved.originalDate!!.dayOfMonth) // 例外键仍是原本发生日
        assertEquals(listOf(7, 14, 21, 28), list.map { it.start.dayOfMonth })
        assertEquals(listOf(9, 14, 9, 9), list.map { it.start.hour })
    }

    @Test
    fun `改期搬进窗口的例外也补回_原那天让位`() {
        // 原本 10-07 那次被改到 10-15:10-15 落在窗口,10-07 那天让位(不是两份)
        val list = expand(
            listOf(
                EventException(
                    masterId = "w1",
                    originalDate = LocalDate.parse("2026-10-07"),
                    override = overrideEvent("2026-10-15", 11),
                ),
            ),
        )
        assertEquals(listOf(14, 15, 21, 28), list.map { it.start.dayOfMonth })
        val moved = list.first { it.start.dayOfMonth == 15 }
        assertEquals(11, moved.start.hour)
        assertEquals(LocalDate.parse("2026-10-07"), moved.originalDate)
    }

    @Test
    fun `例外不改规则_标题等字段可覆盖`() {
        val list = expand(
            listOf(
                EventException(
                    masterId = "w1",
                    originalDate = LocalDate.parse("2026-10-14"),
                    override = overrideEvent("2026-10-14", 9).copy(title = "改名的周会", location = "三楼"),
                ),
            ),
        )
        val changed = list.first { it.start.dayOfMonth == 14 }
        assertEquals("改名的周会", changed.event.title)
        assertEquals("三楼", changed.event.location)
        assertEquals(RuleType.WEEKLY, changed.event.rule.type) // 规则沿用母事件
        val untouched = list.first { it.start.dayOfMonth == 7 }
        assertEquals("周会", untouched.event.title)
    }

    @Test
    fun `取消的改期例外同样不出现`() {
        // 先改期到 10-15,又取消:两次都不出现
        val list = expand(
            listOf(
                EventException(
                    masterId = "w1",
                    originalDate = LocalDate.parse("2026-10-07"),
                    cancelled = true,
                    override = overrideEvent("2026-10-15", 11),
                ),
            ),
        )
        assertEquals(listOf(14, 21, 28), list.map { it.start.dayOfMonth })
    }

    @Test
    fun `非重复事件的例外被忽略`() {
        // 单次事件落在 10-05;给它挂一条 10-05 的取消例外,也不该被吃掉(例外只对重复母事件有意义)
        val single = weekly.copy(
            id = "s1",
            rule = EventRule(),
            start = LocalDateTime.parse("2026-10-05T09:00"),
            end = LocalDateTime.parse("2026-10-05T10:00"),
        )
        val list = expander.expand(
            listOf(single),
            listOf(EventException(masterId = "s1", originalDate = LocalDate.parse("2026-10-05"), cancelled = true)),
            rangeS,
            rangeE,
        )
        assertEquals(1, list.size) // 例外对单次事件无意义
        assertNull(list[0].originalDate)
    }

    @Test
    fun `取消后其余按开始时间排序不变`() {
        val list = expand(
            listOf(EventException(masterId = "w1", originalDate = LocalDate.parse("2026-10-07"), cancelled = true)),
        )
        assertEquals(listOf(14, 21, 28), list.map { it.start.dayOfMonth })
    }
}
