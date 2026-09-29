package com.lnx.app.core.domain

import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 标签筛选(spec §3.10):勾掉标签即隐藏"含该标签"的事件(任一命中即隐藏);
 * 未分类行控制无标签事件;筛选状态重启恢复全选(由 TagFilterState 不持久化保证)。
 */
class TagFilterLogicTest {

    private val day = LocalDate.of(2026, 9, 28)

    private fun occ(id: String) = Occurrence(
        event = Event(
            id = id,
            title = id,
            allDay = false,
            start = day.atTime(10, 0),
            end = day.atTime(11, 0),
            location = null,
            notes = null,
            colorSlot = 0,
            priority = Priority.P2,
            reminderLeadMinutes = null,
            rule = EventRule(RuleType.NONE, end = RuleEnd.Never),
            createdAt = 0L,
            updatedAt = 0L,
        ),
        start = day.atTime(10, 0),
        end = day.atTime(11, 0),
    )

    private val eventTags = mapOf(
        "tagged-a" to listOf("a"),
        "tagged-ab" to listOf("a", "b"),
        "tagged-b" to listOf("b"),
    )

    @Test
    fun `全不选时全部可见`() {
        val input = listOf(occ("tagged-a"), occ("tagged-b"), occ("untagged"))
        assertEquals(
            listOf("tagged-a", "tagged-b", "untagged"),
            applyTagFilter(input, eventTags, hidden = emptySet(), hideUntagged = false)
                .map { it.event.id },
        )
    }

    @Test
    fun `隐藏a后含a的事件都隐藏_含b的保留`() {
        val input = listOf(occ("tagged-a"), occ("tagged-ab"), occ("tagged-b"), occ("untagged"))
        assertEquals(
            listOf("tagged-b", "untagged"),
            applyTagFilter(input, eventTags, hidden = setOf("a"), hideUntagged = false)
                .map { it.event.id },
        )
    }

    @Test
    fun `隐藏未分类只影响无标签事件`() {
        val input = listOf(occ("tagged-a"), occ("untagged"))
        assertEquals(
            listOf("tagged-a"),
            applyTagFilter(input, eventTags, hidden = emptySet(), hideUntagged = true)
                .map { it.event.id },
        )
    }

    @Test
    fun `关联表里没有记录的事件一律按未分类`() {
        val input = listOf(occ("unknown"))
        assertEquals(
            emptyList<String>(),
            applyTagFilter(input, eventTags, hidden = emptySet(), hideUntagged = true)
                .map { it.event.id },
        )
    }

    @Test
    fun `筛选不改变原列表`() {
        val input = listOf(occ("tagged-a"), occ("untagged"))
        applyTagFilter(input, eventTags, hidden = setOf("a"), hideUntagged = true)
        assertEquals(2, input.size)
    }
}
