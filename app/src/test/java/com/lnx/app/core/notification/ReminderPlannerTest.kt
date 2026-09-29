package com.lnx.app.core.notification

import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.Occurrence
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 假仓库:窗口内**没有**任何发生(典型:30 天内没事件,但 30 天外可能有) */
private class EmptyWindowRepository : EventRepository {
    override fun observeOccurrences(start: LocalDateTime, end: LocalDateTime): Flow<List<Occurrence>> =
        MutableStateFlow(emptyList())

    override fun observeEvents(start: LocalDateTime, end: LocalDateTime): Flow<List<Event>> =
        MutableStateFlow(emptyList())

    override suspend fun getEvent(id: String): Event? = null
    override suspend fun save(event: Event) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun upsertException(exception: EventException) = Unit
    override suspend fun cancelOccurrence(masterId: String, originalDate: LocalDate) = Unit
    override suspend fun deleteExceptionsFor(masterId: String) = Unit
    override suspend fun deleteExceptionsFrom(masterId: String, from: LocalDate) = Unit
}

class ReminderPlannerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var sink: RecordingAlarmSink

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        sink = RecordingAlarmSink()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun 窗口内没有提醒也必须排续排闹钟() = runTest {
        // 窗口为空 → 清单为空。此时如果"因为没有要排的就不排续排",链条就断了:
        // 30 天外那条提醒永远不会被排上,而 App 又被用户随手划掉。
        val planner = ReminderPlanner(EmptyWindowRepository(), sink)

        planner.reschedule()

        assertNotNull(
            "空窗口也必须排续排闹钟,否则窗口之外的提醒永远排不上",
            sink.continueAt,
        )
    }

    @Test
    fun 续排闹钟落在窗口末端() = runTest {
        val now = LocalDateTime.parse("2026-09-29T10:00")
        val planner = ReminderPlanner(EmptyWindowRepository(), sink)

        planner.reschedule(now)

        assertNotNull(sink.continueAt)
        assertTrue(
            "续排应落在窗口末端(现在 + 30 天)",
            sink.continueAt!! == now.plusDays(ReminderPlanner.LOOKAHEAD_DAYS),
        )
    }

    @Test
    fun 重排是幂等的_连续多次每次都把清单重排() = runTest {
        val planner = ReminderPlanner(EmptyWindowRepository(), sink)

        planner.reschedule()
        planner.reschedule()
        planner.reschedule()

        assertTrue(sink.rescheduleCount == 3)
    }
}
