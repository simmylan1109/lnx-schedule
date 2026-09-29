package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.DayOfWeek
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 展开器契约(M4 完整版):单次事件直通,重复母事件按引擎展开。
 * 取代 M2"只返回首例"的中间态(那个钉子随引擎接入拆除,RecurrenceEngineTest 接管引擎语义)。
 */
class OccurrenceExpanderTest {
    private val expander = DefaultOccurrenceExpander()
    private val rangeS = LocalDateTime.parse("2026-10-01T00:00")
    private val rangeE = LocalDateTime.parse("2026-11-01T00:00")

    private fun timed(
        id: String,
        start: String,
        end: String,
        rule: EventRule = EventRule(),
    ) = Event(
        id = id,
        title = id,
        allDay = false,
        start = LocalDateTime.parse(start),
        end = LocalDateTime.parse(end),
        location = null,
        notes = null,
        colorSlot = 0,
        priority = Priority.P2,
        reminderLeadMinutes = null,
        rule = rule,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `单次事件_区间内返回一条且携带原本发生日`() {
        val list = expander.expand(
            listOf(timed("a", "2026-10-05T09:00", "2026-10-05T10:00")),
            emptyList(),
            rangeS,
            rangeE,
        )
        assertEquals(1, list.size)
        assertEquals(null, list[0].originalDate)
    }

    @Test
    fun `半开区间_结束贴起点不算`() {
        val list = expander.expand(
            listOf(timed("a", "2026-09-30T09:00", "2026-10-01T00:00")),
            emptyList(),
            rangeS,
            rangeE,
        )
        assertEquals(0, list.size)
    }

    @Test
    fun `每周三_区间内4次且各带原本发生日`() {
        val weekly = timed(
            "w1",
            "2026-09-30T09:00",
            "2026-09-30T10:00",
            EventRule(RuleType.WEEKLY, weekdays = setOf(DayOfWeek.WEDNESDAY)),
        )
        val list = expander.expand(listOf(weekly), emptyList(), rangeS, rangeE)
        // 起点早于区间也照常展开:10-07、14、21、28
        assertEquals(listOf(7, 14, 21, 28), list.map { it.start.dayOfMonth })
        assertEquals(listOf(7, 14, 21, 28), list.map { it.originalDate!!.dayOfMonth })
        // 发生携带母事件字段(标题/规则),时间换成各次
        assertEquals("w1", list[0].event.id)
        assertEquals(RuleType.WEEKLY, list[0].event.rule.type)
    }

    @Test
    fun `结果按开始时间全序`() {
        val list = expander.expand(
            listOf(
                timed("b", "2026-10-05T09:00", "2026-10-05T10:00"),
                timed("a", "2026-10-05T08:00", "2026-10-05T09:00"),
            ),
            emptyList(),
            rangeS,
            rangeE,
        )
        assertEquals(listOf("a", "b"), list.map { it.event.id })
    }

    @Test
    fun `全天事件按整块返回`() {
        // 全天跨 2 天(10-05 → 10-07 排他),整块落在区间里
        val allDay = timed("a", "2026-10-05T00:00", "2026-10-07T00:00").copy(allDay = true)
        val list = expander.expand(listOf(allDay), emptyList(), rangeS, rangeE)
        assertEquals(1, list.size)
        assertEquals(allDay.start, list.first().start)
        assertEquals(allDay.end, list.first().end)
    }
}
