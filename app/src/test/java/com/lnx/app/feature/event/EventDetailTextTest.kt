package com.lnx.app.feature.event

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** 详情卡文案(spec §3.6:全天显示日期区间;定时显示起止) */
class EventDetailTextTest {

    private fun event(
        title: String = "测试",
        allDay: Boolean = false,
        start: LocalDateTime,
        end: LocalDateTime,
    ) = Event(
        id = "x",
        title = title,
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
    )

    private val day = LocalDate.of(2026, 9, 26) // 周六

    @Test
    fun `定时同日事件显示日期与起止`() {
        val text = detailTimeText(
            event(start = day.atTime(9, 30), end = day.atTime(11, 0)),
        )
        assertEquals("9月26日 周六 09:30 – 11:00", text)
    }

    @Test
    fun `定时跨天事件显示两端的日期与时间`() {
        val text = detailTimeText(
            event(start = day.atTime(22, 0), end = day.plusDays(1).atTime(6, 30)),
        )
        assertEquals("9月26日 周六 22:00 至 9月27日 周日 06:30", text)
    }

    @Test
    fun `全天单日显示当天全天`() {
        // 存储:26 日 00:00 → 27 日 00:00(排他)
        val text = detailTimeText(
            event(allDay = true, start = day.atStartOfDay(), end = day.plusDays(1).atStartOfDay()),
        )
        assertEquals("9月26日 周六 全天", text)
    }

    @Test
    fun `全天多日显示区间含最后一天`() {
        // 存储:26 日 00:00 → 28 日 00:00(排他) = 26、27 两天
        val text = detailTimeText(
            event(allDay = true, start = day.atStartOfDay(), end = day.plusDays(2).atStartOfDay()),
        )
        assertEquals("9月26日 周六 至 9月27日 周日 全天", text)
    }

    @Test
    fun `不重复规则文案`() {
        assertEquals("不重复", detailRuleText(EventRule(RuleType.NONE, end = RuleEnd.Never)))
    }
}
