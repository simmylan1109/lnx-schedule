package com.lnx.app.feature.event

import androidx.lifecycle.SavedStateHandle
import com.lnx.app.core.domain.EventRepository
import com.lnx.app.core.domain.TagRepository
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.model.Tag
import com.lnx.app.core.domain.recurrence.EditScope
import com.lnx.app.core.domain.recurrence.RecurrenceEditHandler
import com.lnx.app.core.notification.RecordingAlarmSink
import com.lnx.app.core.notification.ReminderPlanner
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 假仓库:只支撑保存/读取/重叠检查这条最小路径 */
private class FakeEventRepository(
    private val stored: MutableMap<String, Event> = mutableMapOf(),
) : EventRepository {
    val saved = mutableListOf<Event>()
    val exceptionUpserts = mutableListOf<EventException>()

    override fun observeOccurrences(start: LocalDateTime, end: LocalDateTime): Flow<List<Occurrence>> =
        MutableStateFlow(emptyList())

    override fun observeEvents(start: LocalDateTime, end: LocalDateTime): Flow<List<Event>> =
        MutableStateFlow(stored.values.filter { it.start < end && it.end > start })

    override suspend fun getEvent(id: String): Event? = stored[id]

    override suspend fun save(event: Event) {
        saved += event
        stored[event.id] = event
    }

    /** 预置数据不计入 [saved]:断言"保存了几次"时不该把测试数据算进去 */
    fun seedOnly(event: Event) {
        stored[event.id] = event
    }

    override suspend fun delete(id: String) {
        stored.remove(id)
    }

    override suspend fun upsertException(exception: EventException) {
        exceptionUpserts += exception
    }

    override suspend fun cancelOccurrence(masterId: String, originalDate: LocalDate) = Unit

    override suspend fun deleteExceptionsFor(masterId: String) = Unit

    override suspend fun deleteExceptionsFrom(masterId: String, from: LocalDate) = Unit
}

private class FakeTagRepository : TagRepository {
    override fun observeTags(): Flow<List<Tag>> = MutableStateFlow(emptyList())
    override suspend fun createTag(name: String, colorSlot: Int): Result<Tag> =
        Result.success(Tag("t-$name", name, colorSlot))
    override suspend fun renameTag(id: String, name: String) = Unit
    override suspend fun deleteTag(id: String) = Unit
    override suspend fun setEventTags(eventId: String, tagIds: List<String>) = Unit
    override fun observeTagsOfEvent(eventId: String): Flow<List<Tag>> = MutableStateFlow(emptyList())
    override fun observeEventTagIds(): Flow<Map<String, List<String>>> = MutableStateFlow(emptyMap())
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EventEditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeEventRepository
    private lateinit var sink: RecordingAlarmSink

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeEventRepository()
        sink = RecordingAlarmSink()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(editingId: String? = null) = EventEditViewModel(
        repository = repo,
        tagRepository = FakeTagRepository(),
        recurrenceHandler = RecurrenceEditHandler(repo),
        reminderPlanner = ReminderPlanner(repo, sink),
        savedStateHandle = SavedStateHandle(
            if (editingId == null) emptyMap() else mapOf(EventEditViewModel.KEY_EVENT_ID to editingId),
        ),
    )

    private val start = LocalDateTime.parse("2026-09-29T14:00")

    private fun seedMaster(id: String = "m1") = Event(
        id = id, title = "周会", allDay = false,
        start = start, end = start.plusMinutes(30),
        location = null, notes = null, colorSlot = 0, priority = Priority.P2,
        reminderLeadMinutes = 15, rule = EventRule(), createdAt = 1_000L, updatedAt = 0L,
    )

    // —— 保存后必须重排(spec §3.8;M5 终审报"只测了删除没测保存") ——

    @Test
    fun `新建保存后触发提醒重排`() = runTest {
        val subject = vm()
        subject.initialize(start)
        subject.setTitle("会议")
        subject.save()
        assertEquals(1, sink.rescheduleCount)
        assertEquals(1, repo.saved.size)
    }

    @Test
    fun `仅本次保存也触发重排_走的是例外分支`() = runTest {
        val master = seedMaster()
        repo.seedOnly(master)
        val subject = vm()
        subject.initializeOccurrenceEdit(Occurrence(master, start, start.plusMinutes(30), start.toLocalDate()), EditScope.THIS_ONLY)
        subject.save()

        assertEquals(1, sink.rescheduleCount)
        assertEquals(1, repo.exceptionUpserts.size) // 走的确实是例外分支,不是母事件
        assertEquals(0, repo.saved.size)
    }

    @Test
    fun `全部保存也触发重排_母事件本身被改`() = runTest {
        val master = seedMaster()
        repo.seedOnly(master)
        val subject = vm()
        subject.initializeOccurrenceEdit(Occurrence(master, start, start.plusMinutes(30), start.toLocalDate()), EditScope.ALL)
        subject.save()

        assertEquals(1, sink.rescheduleCount)
        assertEquals(1, repo.saved.size)
        assertEquals("m1", repo.saved.single().id)
    }

    // —— P0 回归:草稿默认带 15 分钟提醒,用户不碰提醒行也要申请通知权限 ——

    @Test
    fun `默认提醒直接保存也要申请通知权限`() = runTest {
        val subject = vm()
        subject.initialize(start) // EventDraft 默认 reminderLeadMinutes = 15
        subject.setTitle("会议")
        assertFalse(subject.uiState.value.askNotificationPermission)

        subject.save()

        assertTrue("从没碰过提醒行,但事件带提醒,必须申请权限", subject.uiState.value.askNotificationPermission)
    }

    @Test
    fun `不提醒的事件保存不申请权限`() = runTest {
        val subject = vm()
        subject.initialize(start)
        subject.setTitle("会议")
        subject.setReminderLead(null)

        subject.save()

        assertFalse(subject.uiState.value.askNotificationPermission)
        assertEquals(1, sink.rescheduleCount) // 重排照做(清掉旧提醒)
    }

    @Test
    fun `点过提醒档位后保存不再重复申请`() = runTest {
        val subject = vm()
        subject.initialize(start)
        subject.setTitle("会议")
        subject.setReminderLead(30)
        assertTrue(subject.uiState.value.askNotificationPermission)
        subject.consumeNotificationPermissionRequest()

        subject.save()

        assertFalse("同一次编辑会话内只问一次", subject.uiState.value.askNotificationPermission)
    }

    @Test
    fun `选不提醒不触发权限申请`() = runTest {
        val subject = vm()
        subject.initialize(start)
        subject.setTitle("会议")
        subject.setReminderLead(15)
        subject.consumeNotificationPermissionRequest()
        subject.setReminderLead(null)

        assertFalse(subject.uiState.value.askNotificationPermission)
    }

    @Test
    fun `保存时校验不通过不排闹钟`() = runTest {
        val subject = vm()
        subject.initialize(start)
        subject.setTitle("会议")
        subject.setTitle("")
        subject.save()

        assertFalse(subject.uiState.value.saved)
        assertEquals(0, sink.rescheduleCount)
    }
}
