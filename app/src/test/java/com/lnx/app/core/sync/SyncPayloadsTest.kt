package com.lnx.app.core.sync

import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.EventExceptionEntity
import com.lnx.app.core.database.entity.TagEntity
import kotlin.reflect.full.primaryConstructor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * payload 与实体的双向映射(v0.3 A4)。**双向都要锁**:漏一个字段,
 * 推上去的是缺字段的数据,拉回来的会把本地字段静默抹成空 —— 这类 bug 单看代码看不出来。
 */
class SyncPayloadsTest {

    private val event = EventEntity(
        id = "e-1",
        title = "周会",
        allDay = false,
        startAt = 1_760_000_000_000,
        endAt = 1_760_000_360_000,
        location = "3F 会议室",
        notes = "带笔记本",
        colorSlot = 2,
        priority = "P1",
        reminderLeadMinutes = 15,
        ruleType = "WEEKLY",
        ruleInterval = 1,
        ruleWeekdays = "1,3",
        ruleMonthlyMode = null,
        ruleMonthlyDay = null,
        ruleMonthlyNth = null,
        ruleMonthlyWeekday = null,
        ruleEndType = "COUNT",
        ruleEndDate = null,
        ruleCount = 10,
        createdAt = 1_759_000_000_000,
        updatedAt = 1_760_000_000_000,
        isDeleted = false,
    )

    private val tag = TagEntity(
        id = "t-1",
        name = "工作",
        colorSlot = 3,
        createdAt = 1_759_000_000_000,
        updatedAt = 1_759_500_000_000,
        isDeleted = false,
    )

    private val exception = EventExceptionEntity(
        id = "e-1:20000",
        masterEventId = "e-1",
        originalDate = 20_000,
        isCancelled = false,
        overrideTitle = "周会(改)",
        overrideAllDay = false,
        overrideStartAt = 1_760_000_100_000,
        overrideEndAt = 1_760_000_400_000,
        overrideLocation = null,
        overrideNotes = null,
        overrideColorSlot = 4,
        overridePriority = "P2",
        overrideReminderLeadMinutes = null,
        createdAt = 1_759_000_000_000,
        updatedAt = 1_759_900_000_000,
    )

    @Test
    fun `事件往返_含关联的标签列表`() {
        val payload = SyncPayloads.decodeEvent(SyncPayloads.event(event, listOf("t-1", "t-2")))
        assertEquals("周会", payload.title)
        assertEquals(listOf("t-1", "t-2"), payload.tagIds)
        assertEquals("P1", payload.priority)
        assertEquals("COUNT", payload.ruleEndType)
        assertEquals(10, payload.ruleCount)
        assertEquals(1_760_000_000_000, payload.updatedAt)
    }

    @Test
    fun `标签往返_含墓碑`() {
        val tombstoned = tag.copy(isDeleted = true)
        val payload = SyncPayloads.decodeTag(SyncPayloads.tag(tombstoned))
        assertEquals("工作", payload.name)
        assertEquals(true, payload.isDeleted)
        assertEquals(1_759_500_000_000, payload.updatedAt)
    }

    @Test
    fun `例外往返_可空字段组一个不丢`() {
        val payload = SyncPayloads.decodeException(SyncPayloads.exception(exception))
        assertEquals("e-1", payload.masterEventId)
        assertEquals(20_000, payload.originalDate)
        assertEquals(false, payload.isCancelled)
        assertEquals("周会(改)", payload.overrideTitle)
        assertEquals(4, payload.overrideColorSlot)
        assertNull(payload.overrideReminderLeadMinutes)
        assertNull(payload.overrideLocation)
    }

    @Test
    fun `实体的每一列都在payload里_加列漏改payload时这条先红`() {
        // 形状对比用构造参数名:id 不进 payload(行 id 权威),其余一列都不能少
        val entityColumns = EventEntity::class.primaryConstructor!!.parameters
            .mapNotNull { it.name }.toSet() - "id"
        val payloadColumns = EventPayload::class.primaryConstructor!!.parameters
            .mapNotNull { it.name }.toSet()
        assertEquals(emptySet<String>(), entityColumns - payloadColumns)

        // 标签与例外同样盯一遍(它们的列少,但漂移一样会静默丢数据)
        val tagEntityColumns = TagEntity::class.primaryConstructor!!.parameters
            .mapNotNull { it.name }.toSet() - "id"
        val tagPayloadColumns = TagPayload::class.primaryConstructor!!.parameters
            .mapNotNull { it.name }.toSet()
        assertEquals(emptySet<String>(), tagEntityColumns - tagPayloadColumns)

        val excEntityColumns = EventExceptionEntity::class.primaryConstructor!!.parameters
            .mapNotNull { it.name }.toSet() - "id"
        val excPayloadColumns = ExceptionPayload::class.primaryConstructor!!.parameters
            .mapNotNull { it.name }.toSet()
        assertEquals(emptySet<String>(), excEntityColumns - excPayloadColumns)
    }
}