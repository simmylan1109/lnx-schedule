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
        assertEquals(
            LocalDateTime.of(LocalDate.of(2026, 9, 28), LocalTime.of(9, 30)),
            EventDefaults.startFor(LocalDate.of(2026, 9, 28), now = LocalTime.of(9, 7)),
        )
        // 整点已过时,进位到下一个半点
        assertEquals(
            LocalDateTime.of(LocalDate.of(2026, 9, 28), LocalTime.of(10, 0)),
            EventDefaults.startFor(LocalDate.of(2026, 9, 28), now = LocalTime.of(9, 30)),
        )
        // 非今天:该日 09:00
        assertEquals(
            LocalDateTime.of(LocalDate.of(2026, 10, 5), LocalTime.of(9, 0)),
            EventDefaults.startFor(LocalDate.of(2026, 10, 5), now = LocalTime.of(9, 7)),
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
}
