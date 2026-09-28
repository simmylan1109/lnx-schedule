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

    @Test
    fun `每月按第N天往返`() {
        val rule = EventRule(
            type = RuleType.MONTHLY,
            monthlyMode = MonthlyMode.BY_MONTHDAY,
            monthlyDay = 31,
            end = RuleEnd.Never,
        )
        assertEquals(rule, event(rule = rule).toEntity().toEvent().rule)
    }

    @Test
    fun `脏数据不炸且有兜底`() {
        val dirty = event().toEntity().copy(
            priority = "P9",
            ruleInterval = 0,
            ruleWeekdays = "1,9,5", // 9 非法
            ruleType = RuleType.WEEKLY.name,
        ).toEvent()
        assertEquals(Priority.P2, dirty.priority) // spec §4.2 默认 P2
        assertEquals(1, dirty.rule.interval) // 0 被抬到 1
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), dirty.rule.weekdays) // 9 丢弃
    }

    @Test
    fun `未知规则类型回落为不重复`() {
        val dirty = event().toEntity().copy(ruleType = "HOURLY", ruleWeekdays = "1,5")
            .toEvent()
        assertEquals(RuleType.NONE, dirty.rule.type)
        assertEquals(EventRule(), dirty.rule) // 整体回落,不残留半截规则
    }

    @Test
    fun `非重复事件的interval归一化不丢`() {
        val weird = event().copy(rule = EventRule(type = RuleType.NONE, interval = 5))
        assertEquals(EventRule(), weird.toEntity().toEvent().rule)
    }
}
