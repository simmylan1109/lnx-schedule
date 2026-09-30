package com.lnx.app.feature.event

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 详情卡时间行(spec §3.6)。
 *
 * 这里只断言**形状**(哪几天、几点、用哪种句式)—— 句子本身在 `strings.xml` 里、
 * 日期格式在 `LnxLocale` 里,都按语言走,不该在纯单测里拿中文串钉死
 * (早先这里是直接断言"9月26日 周六 09:30 – 11:00"整句的,一改语言就红,
 * 而真正该守的"跨天要带结束那天的日期"反而被淹没在里面)。
 */
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
    fun `定时同日事件只显示一天但保留起止两个时刻`() {
        val parts = detailTimeParts(event(start = day.atTime(9, 30), end = day.atTime(11, 0)))
        assertEquals(DetailTimeParts.Kind.TIMED_SAME_DAY, parts.kind)
        assertEquals(listOf(day), parts.dates)
        assertEquals(listOf(9, 11), parts.times.map { it.hour })
    }

    @Test
    fun `定时跨天事件两端都带日期`() {
        val parts = detailTimeParts(
            event(start = day.atTime(22, 0), end = day.plusDays(1).atTime(6, 30)),
        )
        assertEquals(DetailTimeParts.Kind.TIMED_RANGE, parts.kind)
        assertEquals(listOf(day, day.plusDays(1)), parts.dates)
        assertEquals(listOf(22, 6), parts.times.map { it.hour })
    }

    @Test
    fun `全天单日显示当天`() {
        // 存储:26 日 00:00 → 27 日 00:00(排他)
        val parts = detailTimeParts(
            event(allDay = true, start = day.atStartOfDay(), end = day.plusDays(1).atStartOfDay()),
        )
        assertEquals(DetailTimeParts.Kind.ALL_DAY_SINGLE, parts.kind)
        assertEquals(listOf(day), parts.dates)
        assertTrue("全天事件没有时刻", parts.times.isEmpty())
    }

    @Test
    fun `全天多日区间含最后一天`() {
        // 存储:26 日 00:00 → 28 日 00:00(排他) = 26、27 两天
        val parts = detailTimeParts(
            event(allDay = true, start = day.atStartOfDay(), end = day.plusDays(2).atStartOfDay()),
        )
        assertEquals(DetailTimeParts.Kind.ALL_DAY_RANGE, parts.kind)
        assertEquals(listOf(day, day.plusDays(1)), parts.dates)
    }

    @Test
    fun `不重复规则文案中英各一套`() {
        assertEquals("不重复", detailRuleText(EventRule(RuleType.NONE, end = RuleEnd.Never), Locale.SIMPLIFIED_CHINESE))
        assertEquals("Does not repeat", detailRuleText(EventRule(RuleType.NONE, end = RuleEnd.Never), Locale.ENGLISH))
    }
}
