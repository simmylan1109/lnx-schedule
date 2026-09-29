package com.lnx.app.core.notification

import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 免打扰静默策略(spec §3.8):时段内通知照发但不响铃,结束边界排他 */
class DndPolicyTest {

    private fun at(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 9, 29, hour, minute)

    @Test
    fun `关掉时任何时刻都不静默`() {
        assertFalse(DndPolicy.isSilent(at(23, 30), enabled = false, startMinute = 22 * 60, endMinute = 8 * 60))
    }

    @Test
    fun `窗口内静默_晚上十一点半落在二十二点到八点`() {
        assertTrue(DndPolicy.isSilent(at(23, 30), enabled = true, startMinute = 22 * 60, endMinute = 8 * 60))
    }

    @Test
    fun `窗口内静默_凌晨三点`() {
        assertTrue(DndPolicy.isSilent(at(3, 0), enabled = true, startMinute = 22 * 60, endMinute = 8 * 60))
    }

    @Test
    fun `窗口外不静默_中午十二点`() {
        assertFalse(DndPolicy.isSilent(at(12, 0), enabled = true, startMinute = 22 * 60, endMinute = 8 * 60))
    }

    @Test
    fun `开始边界含_二十二点整算静默`() {
        assertTrue(DndPolicy.isSilent(at(22, 0), enabled = true, startMinute = 22 * 60, endMinute = 8 * 60))
    }

    @Test
    fun `结束边界排他_八点整不再静默`() {
        assertFalse(DndPolicy.isSilent(at(8, 0), enabled = true, startMinute = 22 * 60, endMinute = 8 * 60))
    }

    @Test
    fun `结束前一分钟仍静默`() {
        assertTrue(DndPolicy.isSilent(at(7, 59), enabled = true, startMinute = 22 * 60, endMinute = 8 * 60))
    }

    @Test
    fun `不跨午夜的窗口_九点到十八点`() {
        val start = 9 * 60
        val end = 18 * 60
        assertTrue(DndPolicy.isSilent(at(12, 0), enabled = true, startMinute = start, endMinute = end))
        assertFalse(DndPolicy.isSilent(at(8, 59), enabled = true, startMinute = start, endMinute = end))
        assertFalse(DndPolicy.isSilent(at(18, 0), enabled = true, startMinute = start, endMinute = end))
    }

    @Test
    fun `起止相同的窗口视为不静默_防呆`() {
        // 用户把起止设成同一分钟时不该"全天静默",按空窗口处理
        assertFalse(DndPolicy.isSilent(at(3, 0), enabled = true, startMinute = 480, endMinute = 480))
    }
}
