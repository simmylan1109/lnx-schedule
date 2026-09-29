package com.lnx.app.core.domain.recurrence

import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.RuleEnd
import com.lnx.app.core.domain.model.RuleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三选一语义落库(spec §4.4):3 作用范围 × 改/删 = 6 条主断言,
 * 外加 Count 剪断的计数、单次例外不改规则。
 */
class RecurrenceEditHandlerTest {

    /** 录操作的假仓库:断言"谁被调用了、参数对不对",不碰 Room */
    private class FakeRepo : EventRepository {
        val saved = mutableListOf<Event>()
        val deleted = mutableListOf<String>()
        val exceptions = mutableListOf<EventException>()
        val cancelled = mutableListOf<Pair<String, LocalDate>>()
        val clearedExceptionsFor = mutableListOf<String>()
        val clearedExceptionsFrom = mutableListOf<Pair<String, LocalDate>>()

        override fun observeOccurrences(start: LocalDateTime, end: LocalDateTime): Flow<List<Occurrence>> = flowOf(emptyList())
        override fun observeEvents(start: LocalDateTime, end: LocalDateTime): Flow<List<Event>> = flowOf(emptyList())
        override suspend fun getEvent(id: String): Event? = null
        override suspend fun save(event: Event) { saved += event }
        override suspend fun delete(id: String) { deleted += id }
        override suspend fun upsertException(exception: EventException) { exceptions += exception }
        override suspend fun cancelOccurrence(masterId: String, originalDate: LocalDate) { cancelled += masterId to originalDate }
        override suspend fun deleteExceptionsFor(masterId: String) { clearedExceptionsFor += masterId }
        override suspend fun deleteExceptionsFrom(masterId: String, from: LocalDate) { clearedExceptionsFrom += masterId to from }
    }

    private val repo = FakeRepo()
    private val handler = RecurrenceEditHandler(repo, newId = { "new-master" })

    /** 每周一 09:00 起,永不结束的母事件 */
    private val master = Event(
        id = "m1",
        title = "周会",
        allDay = false,
        start = LocalDateTime.parse("2026-09-28T09:00"),
        end = LocalDateTime.parse("2026-09-28T10:00"),
        location = null,
        notes = null,
        colorSlot = 2,
        priority = Priority.P2,
        reminderLeadMinutes = null,
        rule = EventRule(RuleType.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY)),
        createdAt = 111L,
        updatedAt = 0L,
    )

    private val targetDate = LocalDate.parse("2026-10-12") // 第 3 个周一
    private fun edited(hour: Int = 14) = master.copy(
        title = "改过的周会",
        start = targetDate.atTime(hour, 0),
        end = targetDate.atTime(hour + 1, 0),
        rule = EventRule(RuleType.DAILY), // 用户在编辑器里改了规则:仅本次必须拦掉
    )

    // —— 仅本次 ——

    @Test
    fun `仅本次_删_写取消例外不动母事件`() = runTest {
        handler.apply(master, targetDate, null, EditScope.THIS_ONLY)
        assertEquals("m1" to targetDate, repo.cancelled.single())
        assertTrue(repo.deleted.isEmpty())
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `仅本次_改_写override例外且不改规则`() = runTest {
        handler.apply(master, targetDate, edited(), EditScope.THIS_ONLY)
        val ex = repo.exceptions.single()
        assertEquals("m1", ex.masterId)
        assertEquals(targetDate, ex.originalDate)
        assertEquals(14, ex.override!!.start.hour)
        assertEquals(RuleType.WEEKLY, ex.override!!.rule.type) // 单次例外不许改规则
        assertTrue(repo.saved.isEmpty()) // 母事件分毫未动
    }

    // —— 全部 ——

    @Test
    fun `全部_改_只存母事件并带原createdAt`() = runTest {
        handler.apply(master, targetDate, edited(), EditScope.ALL)
        val saved = repo.saved.single()
        assertEquals("m1", saved.id)
        assertEquals(111L, saved.createdAt) // 创建时间不得被刷新
        assertEquals(RuleType.DAILY, saved.rule.type) // 全部改:规则可以改
        assertTrue(repo.exceptions.isEmpty())
    }

    @Test
    fun `全部_删_软删母事件并清它的例外`() = runTest {
        handler.apply(master, targetDate, null, EditScope.ALL)
        assertEquals("m1", repo.deleted.single())
        assertEquals("m1", repo.clearedExceptionsFor.single())
        assertTrue(repo.saved.isEmpty())
    }

    // —— 本次及以后 ——

    @Test
    fun `本次及以后_改_剪断旧系列并新建母事件`() = runTest {
        handler.apply(master, targetDate, edited(), EditScope.THIS_AND_FUTURE)
        // 旧母:截止到剪断日前一天(10-11),规则其余不变
        val cut = repo.saved.single { it.id == "m1" }
        assertEquals(RuleEnd.Until(LocalDate.parse("2026-10-11")), cut.rule.end)
        assertEquals(RuleType.WEEKLY, cut.rule.type)
        // 新母:新 UUID,规则/时间取用户所改,系列起点 = 剪断日那次
        val fresh = repo.saved.single { it.id == "new-master" }
        assertEquals(14, fresh.start.hour)
        assertEquals(RuleType.DAILY, fresh.rule.type)
        assertEquals(0L, fresh.createdAt) // 由仓库盖当前时间
        // 剪断日起的旧例外清理
        assertEquals("m1" to targetDate, repo.clearedExceptionsFrom.single())
    }

    @Test
    fun `本次及以后_删_只剪断旧系列不建新母事件`() = runTest {
        handler.apply(master, targetDate, null, EditScope.THIS_AND_FUTURE)
        assertEquals(1, repo.saved.size)
        assertEquals("m1", repo.saved.single().id)
        assertEquals(RuleEnd.Until(LocalDate.parse("2026-10-11")), repo.saved.single().rule.end)
        assertTrue(repo.deleted.isEmpty()) // 旧系列剪断即"消失",不软删(墓碑留给同步语义)
        assertEquals("m1" to targetDate, repo.clearedExceptionsFrom.single())
    }

    // —— 剪断的边界 ——

    @Test
    fun `剪断_原有UNTIL取更近者`() = runTest {
        val untilSoon = master.copy(rule = master.rule.copy(end = RuleEnd.Until(LocalDate.parse("2026-09-30"))))
        handler.apply(untilSoon, targetDate, null, EditScope.THIS_AND_FUTURE)
        assertEquals(RuleEnd.Until(LocalDate.parse("2026-09-30")), repo.saved.single().rule.end)
    }

    @Test
    fun `剪断_COUNT改为剪断日前的实际次数`() = runTest {
        // 9-28 起每周一,数满 10 次;在第 3 个周一(10-12)剪断 → 前面只有 2 次(9-28、10-05)
        val counted = master.copy(rule = master.rule.copy(end = RuleEnd.Count(10)))
        handler.apply(counted, targetDate, null, EditScope.THIS_AND_FUTURE)
        assertEquals(RuleEnd.Count(2), repo.saved.single().rule.end)
    }

    @Test
    fun `剪断_首次发生就剪_不写Count0`() = runTest {
        // 剪在系列第一次当天:前面 0 次,必须退化为 Until 而不是 Count(0)
        handler.apply(master, LocalDate.parse("2026-09-28"), null, EditScope.THIS_AND_FUTURE)
        val end = repo.saved.single().rule.end
        assertTrue("不得写 Count(0):$end", end !is RuleEnd.Count)
        assertEquals(RuleEnd.Until(LocalDate.parse("2026-09-27")), end)
    }

    @Test
    fun `仅本次删只写取消例外_不删母事件不清例外`() = runTest {
        handler.apply(master, targetDate, null, EditScope.THIS_ONLY)
        assertTrue(repo.deleted.isEmpty())
        assertEquals(0, repo.clearedExceptionsFor.size)
        assertEquals(0, repo.saved.size)
    }
}
