package com.lnx.app.core.data

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.MonthlyMode
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class EventMapperTest {
    private fun event(
        rule: EventRule = EventRule(),
        allDay: Boolean = false,
        start: LocalDateTime = LocalDateTime.parse("2026-09-30T09:00"),
        end: LocalDateTime = LocalDateTime.parse("2026-09-30T10:00"),
    ) = Event(
        id = "e1",
        title = "团队周会",
        allDay = allDay,
        start = start,
        end = end,
        location = "3楼",
        notes = "链接",
        colorSlot = 4,
        priority = Priority.P1,
        reminderLeadMinutes = 15,
        rule = rule,
        createdAt = 111L,
        updatedAt = 222L,
    )

    @Test
    fun `普通事件往返不丢字段`() {
        val src = event()
        val back = src.toEntity().toEvent()
        assertEquals(src, back)
    }

    @Test
    fun `全天事件往返_次日零点排他`() {
        val src = event(
            allDay = true,
            start = LocalDateTime.parse("2026-09-28T00:00"),
            end = LocalDateTime.parse("2026-09-30T00:00"),
        )
        val back = src.toEntity().toEvent()
        assertEquals(LocalDateTime.parse("2026-09-30T00:00"), back.end)
    }

    @Test
    fun `每周重复带多星期与次数结束`() {
        val rule = EventRule(
            type = RuleType.WEEKLY,
            interval = 2,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            end = RuleEnd.Count(5),
        )
        val back = event(rule = rule).toEntity().toEvent()
        assertEquals(rule, back.rule)
    }

    @Test
    fun `每月第N个星期与日期结束`() {
        val rule = EventRule(
            type = RuleType.MONTHLY,
            monthlyMode = MonthlyMode.BY_NTH_WEEKDAY,
            monthlyNth = 3,
            monthlyWeekday = DayOfWeek.FRIDAY,
            end = RuleEnd.Until(LocalDate.parse("2027-01-01")),
        )
        val back = event(rule = rule).toEntity().toEvent()
        assertEquals(rule, back.rule)
    }

    @Test
    fun `不重复事件不写结束条件`() {
        val entity = event().toEntity()
        assertEquals(null, entity.ruleEndType)
        assertEquals(RuleEnd.Never, entity.toEvent().rule.end)
    }
}
