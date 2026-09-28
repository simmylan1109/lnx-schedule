package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class OccurrenceExpanderTest {
    private val expander = BasicOccurrenceExpander()

    private fun timed(
        id: String,
        s: String,
        e: String,
        rule: EventRule = EventRule(),
    ) = Event(
        id = id,
        title = id,
        allDay = false,
        start = LocalDateTime.parse(s),
        end = LocalDateTime.parse(e),
        location = null,
        notes = null,
        colorSlot = 0,
        priority = Priority.P2,
        reminderLeadMinutes = null,
        rule = rule,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private val rangeS = LocalDateTime.parse("2026-09-28T00:00")
    private val rangeE = LocalDateTime.parse("2026-10-05T00:00")

    @Test
    fun `完全落在区间内`() {
        val list = expander.expand(
            listOf(timed("a", "2026-09-30T09:00", "2026-09-30T10:00")),
            rangeS,
            rangeE,
        )
        assertEquals(1, list.size)
    }

    @Test
    fun `半开区间_结束贴起点不重叠`() {
        val list = expander.expand(
            listOf(timed("a", "2026-09-27T22:00", "2026-09-28T00:00")),
            rangeS,
            rangeE,
        )
        assertEquals(0, list.size)
    }

    @Test
    fun `半开区间_开始贴终点不重叠`() {
        val list = expander.expand(
            listOf(timed("a", "2026-10-05T00:00", "2026-10-05T01:00")),
            rangeS,
            rangeE,
        )
        assertEquals(0, list.size)
    }

    @Test
    fun `跨区间的事件也命中`() {
        // 跨了整个查询区间
        val list = expander.expand(
            listOf(timed("a", "2026-09-20T09:00", "2026-10-20T10:00")),
            rangeS,
            rangeE,
        )
        assertEquals(1, list.size)
    }

    @Test
    fun `重复事件本里程碑只返回首例`() {
        val daily = timed("a", "2026-09-28T09:00", "2026-09-28T10:00")
            .copy(rule = EventRule(type = RuleType.DAILY, end = RuleEnd.Never))
        val list = expander.expand(listOf(daily), rangeS, rangeE)
        // M4 换成完整引擎后这里应为 7;现在明确钉住"只返回首例"这一中间态
        assertEquals(1, list.size)
    }

    @Test
    fun `结果按开始时间全序`() {
        val list = expander.expand(
            listOf(
                timed("b", "2026-09-30T10:00", "2026-09-30T11:00"),
                timed("a", "2026-09-30T09:00", "2026-09-30T12:00"),
            ),
            rangeS,
            rangeE,
        )
        assertEquals(listOf("a", "b"), list.map { it.event.id })
    }

    @Test
    fun `全天事件按整块返回`() {
        val allDay = timed("a", "2026-09-28T00:00", "2026-09-30T00:00")
            .copy(allDay = true)
        val list = expander.expand(listOf(allDay), rangeS, rangeE)
        assertEquals(1, list.size)
        assertEquals(allDay.start, list.first().start)
    }
}
