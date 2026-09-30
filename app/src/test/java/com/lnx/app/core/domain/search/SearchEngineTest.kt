package com.lnx.app.core.domain.search

import com.lnx.app.core.domain.DefaultOccurrenceExpander
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.MonthlyMode
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索结果拼装(spec §3.9)。查询本身(SQL LIKE)在 SearchRepositoryTest 里用真 Room 测,
 * 这里只管"命中之后怎么变成一条能看的行":下次发生、排序、限量、通配符转义。
 */
class SearchEngineTest {

    private val engine = SearchEngine(DefaultOccurrenceExpander())
    private val now: LocalDateTime = LocalDateTime.parse("2026-09-29T10:00")

    private fun event(
        id: String,
        start: String,
        end: String,
        rule: EventRule = EventRule(),
    ) = Event(
        id = id,
        title = "标题-$id",
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
    fun `单次事件用事件本身的时间且不写下次发生`() {
        val result = engine.search(
            events = listOf(event("a", "2026-09-30T14:00", "2026-09-30T15:00")),
            exceptions = emptyList(),
            now = now,
        ).single()
        assertFalse(result.recurring)
        assertFalse(result.hasUpcoming)
        assertEquals(LocalDateTime.parse("2026-09-30T14:00"), result.start)
    }

    @Test
    fun `重复事件展示的是下次发生而不是系列起点`() {
        val weekly = EventRule(type = RuleType.DAILY, interval = 1)
        val result = engine.search(
            events = listOf(event("a", "2020-01-01T09:00", "2020-01-01T10:00", weekly)),
            exceptions = emptyList(),
            now = now,
        ).single()
        assertTrue(result.recurring)
        assertTrue(result.hasUpcoming)
        // 今天上午 10 点,今天的 9 点那场已经过去了 → 下次是明天同一时刻
        assertEquals(LocalDateTime.parse("2026-09-30T09:00"), result.start)
    }

    @Test
    fun `下次那次被取消时跳到再下一次`() {
        val daily = EventRule(type = RuleType.DAILY, interval = 1)
        val result = engine.search(
            events = listOf(event("a", "2026-09-01T09:00", "2026-09-01T10:00", daily)),
            exceptions = listOf(
                EventException(masterId = "a", originalDate = LocalDate.parse("2026-09-30"), cancelled = true),
            ),
            now = now,
        ).single()
        assertEquals(LocalDateTime.parse("2026-10-01T09:00"), result.start)
    }

    @Test
    fun `已经结束的系列没有下次发生_退回系列起点`() {
        val ended = EventRule(type = RuleType.DAILY, end = RuleEnd.Count(2))
        val result = engine.search(
            events = listOf(event("a", "2020-01-01T09:00", "2020-01-01T10:00", ended)),
            exceptions = emptyList(),
            now = now,
        ).single()
        assertTrue(result.recurring)
        assertFalse(result.hasUpcoming)
        assertEquals(LocalDateTime.parse("2020-01-01T09:00"), result.start)
    }

    @Test
    fun `每月一次的稀疏规则也能找到下次发生`() {
        val monthly = EventRule(
            type = RuleType.MONTHLY,
            monthlyMode = MonthlyMode.BY_MONTHDAY,
            monthlyDay = 15,
        )
        val result = engine.search(
            events = listOf(event("a", "2020-01-15T09:00", "2020-01-15T10:00", monthly)),
            exceptions = emptyList(),
            now = now,
        ).single()
        assertEquals(LocalDateTime.parse("2026-10-15T09:00"), result.start)
    }

    @Test
    fun `排序_没结束的排前面_过去的那组最近的先`() {
        val list = engine.search(
            events = listOf(
                event("past-far", "2020-01-01T09:00", "2020-01-01T10:00"),
                event("past-near", "2026-09-28T09:00", "2026-09-28T10:00"),
                event("future-far", "2026-12-01T09:00", "2026-12-01T10:00"),
                event("future-near", "2026-09-30T09:00", "2026-09-30T10:00"),
            ),
            exceptions = emptyList(),
            now = now,
        )
        assertEquals(
            listOf("future-near", "future-far", "past-near", "past-far"),
            list.map { it.event.id },
        )
    }

    @Test
    fun `超量截断_留下的是靠前的那批`() {
        val many = (1..(SearchEngine.DEFAULT_LIMIT + 5)).map {
            event("e$it", "2026-09-30T${(it % 24).toString().padStart(2, '0')}:00", "2026-09-30T23:00")
        }
        val list = engine.search(events = many, exceptions = emptyList(), now = now, limit = 10)
        assertEquals(10, list.size)
    }

    @Test
    fun `通配符按普通字符转义_搜百分号不会变成前缀匹配`() {
        assertEquals("%100\\%%", SearchEngine.likePattern("100%"))
        assertEquals("%a\\_b%", SearchEngine.likePattern("a_b"))
        assertEquals("%\\\\x%", SearchEngine.likePattern("\\x"))
    }

    @Test
    fun `每周重复锚在系列起点星期`() {
        val weekly = EventRule(
            type = RuleType.WEEKLY,
            interval = 1,
            weekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
        )
        val result = engine.search(
            events = listOf(event("a", "2026-01-06T09:00", "2026-01-06T10:00", weekly)),
            exceptions = emptyList(),
            now = now,
        ).single()
        // 2026-09-29 是周二,当天 9 点已过 → 下次为周四
        assertEquals(DayOfWeek.THURSDAY, result.start.dayOfWeek)
        assertEquals(LocalDate.parse("2026-10-01"), result.start.toLocalDate())
    }
}
