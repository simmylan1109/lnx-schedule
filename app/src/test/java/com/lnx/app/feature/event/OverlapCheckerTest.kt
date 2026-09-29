package com.lnx.app.feature.event

import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 重叠判定(spec §3.5:半开区间,贴边不算;编辑时排除自己;同日才查)。
 * 计划里本测试在 core/domain,但实现与 EventDraft 耦合放在 feature/event —— 台账已记。
 */
class OverlapCheckerTest {

    private class FakeRepository(
        private val events: List<Event>,
        var lastWindow: Pair<LocalDateTime, LocalDateTime>? = null,
    ) : EventRepository {
        override fun observeOccurrences(start: LocalDateTime, end: LocalDateTime): Flow<List<Occurrence>> =
            flowOf(emptyList())

        override fun observeEvents(start: LocalDateTime, end: LocalDateTime): Flow<List<Event>> {
            lastWindow = start to end
            // 模拟真实查询:窗口半开 [start, end)
            return flowOf(events.filter { it.start < end && it.end > start })
        }

        override suspend fun getEvent(id: String): Event? = events.firstOrNull { it.id == id }

        override suspend fun save(event: Event) = Unit

        override suspend fun delete(id: String) = Unit

        // M4 起的例外操作与本测试无关,占位实现
        override suspend fun upsertException(exception: EventException) = Unit

        override suspend fun cancelOccurrence(masterId: String, originalDate: LocalDate) = Unit

        override suspend fun deleteExceptionsFor(masterId: String) = Unit

        override suspend fun deleteExceptionsFrom(masterId: String, from: LocalDate) = Unit
    }

    private fun event(id: String, start: LocalDateTime, end: LocalDateTime) = Event(
        id = id,
        title = "事件$id",
        allDay = false,
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

    private val day = LocalDate.of(2026, 9, 26)
    private fun at(hour: Int, minute: Int) = day.atTime(hour, minute)

    @Test
    fun `相邻贴边不算重叠`() = runTest {
        val draft = EventDefaults.draft(at(10, 0)).copy(title = "目标", end = at(11, 0))
        // 他在我之前结束(他的结束 = 我的开始)
        val before = FakeRepository(listOf(event("a", at(9, 0), at(10, 0))))
        assertTrue(OverlapChecker.find(before, draft).isEmpty())
        // 他在我之后开始(我的结束 = 他的开始)
        val after = FakeRepository(listOf(event("b", at(11, 0), at(12, 0))))
        assertTrue(OverlapChecker.find(after, draft).isEmpty())
    }

    @Test
    fun `部分与完全包含都算重叠`() = runTest {
        val draft = EventDefaults.draft(at(10, 0)).copy(title = "目标", end = at(11, 0))
        val repo = FakeRepository(
            listOf(
                event("tail", at(10, 30), at(12, 0)), // 尾部相交
                event("head", at(9, 30), at(10, 10)), // 头部相交
                event("wrap", at(9, 0), at(12, 0)), // 包住我
                event("inside", at(10, 15), at(10, 45)), // 被我包住
            ),
        )
        assertEquals(
            listOf("事件tail", "事件head", "事件wrap", "事件inside"),
            OverlapChecker.find(repo, draft),
        )
    }

    @Test
    fun `全天跨天事件与日内事件重叠`() = runTest {
        // 全天 26 日:26 日 00:00 → 27 日 00:00(排他)
        val allDay = event("allday", day.atStartOfDay(), day.plusDays(1).atStartOfDay())
            .copy(allDay = true)
        val draft = EventDefaults.draft(at(23, 0)).copy(title = "深夜") // 23:00–24:00
        val repo = FakeRepository(listOf(allDay))
        assertEquals(listOf("事件allday"), OverlapChecker.find(repo, draft))
    }

    @Test
    fun `编辑时排除自己`() = runTest {
        val draft = EventDefaults.draft(at(10, 0)).copy(id = "me", title = "自己", end = at(11, 0))
        val repo = FakeRepository(listOf(event("me", at(10, 0), at(11, 0))))
        assertTrue(OverlapChecker.find(repo, draft).isEmpty())
    }

    @Test
    fun `同日不同时段之外的事件不参与_查询窗口只覆盖目标当日`() = runTest {
        val draft = EventDefaults.draft(at(10, 0)).copy(title = "目标", end = at(11, 0))
        val repo = FakeRepository(listOf(event("nextday", day.plusDays(1).atTime(10, 0), day.plusDays(1).atTime(11, 0))))
        assertTrue(OverlapChecker.find(repo, draft).isEmpty())
        // 窗口必须是目标日的整天(半开)
        assertEquals(day.atStartOfDay() to day.plusDays(1).atStartOfDay(), repo.lastWindow)
    }

    @Test
    fun `跨零点的草稿也能发现次日事件的重叠`() = runTest {
        val draft = EventDefaults.draft(at(23, 30))
            .copy(title = "夜班", end = day.plusDays(1).atTime(0, 30))
        val repo = FakeRepository(
            listOf(event("next", day.plusDays(1).atTime(0, 0), day.plusDays(1).atTime(1, 0))),
        )
        assertEquals(listOf("事件next"), OverlapChecker.find(repo, draft))
    }
}
