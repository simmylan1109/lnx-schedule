package com.lnx.app.feature.event

import com.lnx.app.core.domain.model.Priority
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDefaultsTest {
    @Test
    fun `FAB默认起点是所选日的下一个半点`() {
        // spec §3.5:所选日期 + 今天取下一个半点
        // today 显式传入:默认 LocalDate.now() 会让测试在跨天后失效
        val today = LocalDate.of(2026, 9, 28)
        assertEquals(
            LocalDateTime.of(LocalDate.of(2026, 9, 28), LocalTime.of(9, 30)),
            EventDefaults.startFor(LocalDate.of(2026, 9, 28), now = LocalTime.of(9, 7), today = today),
        )
        // 整点已过时,进位到下一个半点
        assertEquals(
            LocalDateTime.of(LocalDate.of(2026, 9, 28), LocalTime.of(10, 0)),
            EventDefaults.startFor(LocalDate.of(2026, 9, 28), now = LocalTime.of(9, 30), today = today),
        )
        // 非今天:该日 09:00
        assertEquals(
            LocalDateTime.of(LocalDate.of(2026, 10, 5), LocalTime.of(9, 0)),
            EventDefaults.startFor(LocalDate.of(2026, 10, 5), now = LocalTime.of(9, 7), today = today),
        )
    }

    @Test
    fun `默认时长一小时`() {
        assertEquals(60L, EventDefaults.DEFAULT_DURATION_MINUTES)
    }

    @Test
    fun `新建默认值符合spec`() {
        val draft = EventDefaults.draft(LocalDateTime.of(2026, 9, 30, 9, 0))
        assertEquals("", draft.title)
        assertFalse(draft.allDay)
        assertEquals(Priority.P2, draft.priority)
        assertEquals(0, draft.colorSlot)
        assertEquals(15, draft.reminderLeadMinutes) // spec 出厂默认 15 分钟
        assertEquals(LocalDateTime.of(2026, 9, 30, 10, 0), draft.end)
    }

    @Test
    fun `校验_标题必填且结束晚于开始`() {
        val ok = EventDefaults.draft(LocalDateTime.of(2026, 9, 30, 9, 0)).copy(title = "周会")
        assertEquals(emptyList<ValidationError>(), validate(ok))
        assertTrue(validate(ok.copy(title = "  ")).contains(ValidationError.TITLE_REQUIRED))
        assertTrue(validate(ok.copy(end = ok.start)).contains(ValidationError.END_NOT_AFTER_START))
        assertTrue(validate(ok.copy(end = ok.start.minusMinutes(1))).contains(ValidationError.END_NOT_AFTER_START))
    }

    @Test
    fun `全天事件按整日保存且结束为次日零点`() {
        val draft = EventDefaults.draft(LocalDateTime.of(2026, 9, 30, 9, 0))
            .copy(title = "团建", allDay = true)
        val normalized = normalizeAllDay(draft)
        assertEquals(LocalDateTime.of(2026, 9, 30, 0, 0), normalized.start)
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), normalized.end) // 排他
    }

    // 回归:结束日期选择器存的是排他的"次日零点",归一化不得再 +1(否则多出一整天)
    @Test
    fun `全天归一化不再平移已排他的结束时间`() {
        val draft = EventDefaults.draft(LocalDateTime.of(2026, 9, 26, 0, 0))
            .copy(title = "两日行程", allDay = true, end = LocalDateTime.of(2026, 9, 28, 0, 0))
        val normalized = normalizeAllDay(draft)
        assertEquals(LocalDateTime.of(2026, 9, 26, 0, 0), normalized.start)
        assertEquals(LocalDateTime.of(2026, 9, 28, 0, 0), normalized.end) // 覆盖 26、27 两天
    }

    @Test
    fun `全天归一化兜底结束不晚于开始时保一天`() {
        val draft = EventDefaults.draft(LocalDateTime.of(2026, 9, 27, 8, 0))
            .copy(title = "异常草稿", allDay = true, end = LocalDateTime.of(2026, 9, 27, 0, 0))
        val normalized = normalizeAllDay(draft)
        assertEquals(LocalDateTime.of(2026, 9, 28, 0, 0), normalized.end)
    }

    @Test
    fun `开启全天_当日定时事件转为单日全天`() {
        val draft = EventDefaults.draft(LocalDateTime.of(2026, 9, 30, 14, 30))
            .copy(title = "团建") // 14:30–15:30
        val allDay = EventDefaults.toAllDay(draft)
        assertTrue(allDay.allDay)
        assertEquals(LocalDateTime.of(2026, 9, 30, 0, 0), allDay.start)
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), allDay.end)
    }

    @Test
    fun `开启全天_跨零点的定时事件转为两日全天`() {
        val draft = EventDefaults.draft(LocalDateTime.of(2026, 9, 30, 23, 0))
            .copy(title = "通宵", end = LocalDateTime.of(2026, 10, 1, 1, 0))
        val allDay = EventDefaults.toAllDay(draft)
        assertEquals(LocalDateTime.of(2026, 9, 30, 0, 0), allDay.start)
        assertEquals(LocalDateTime.of(2026, 10, 2, 0, 0), allDay.end)
    }

    @Test
    fun `关闭全天恢复九点到十点`() {
        val allDay = EventDefaults.draft(LocalDateTime.of(2026, 9, 30, 0, 0))
            .copy(
                title = "度假",
                allDay = true,
                end = LocalDateTime.of(2026, 10, 3, 0, 0),
            )
        val timed = EventDefaults.fromAllDay(allDay)
        assertFalse(timed.allDay)
        assertEquals(LocalDateTime.of(2026, 9, 30, 9, 0), timed.start)
        assertEquals(LocalDateTime.of(2026, 9, 30, 10, 0), timed.end)
    }
}
