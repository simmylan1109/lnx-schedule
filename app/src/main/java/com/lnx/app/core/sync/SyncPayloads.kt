package com.lnx.app.core.sync

import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.EventExceptionEntity
import com.lnx.app.core.database.entity.TagEntity
import com.lnx.app.core.network.SyncRowDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * 同步行的 payload 形状(v0.3 A4)。
 *
 * 服务端把 payload 当黑盒存,**形状由客户端自定** —— 这里刻意与备份格式(M7)分开:
 * 备份是"换机的完整快照",同步行是"单行最新状态",两者的版本节奏不该绑死。
 *
 * **关联(事件↔标签)不单独成行**,而是把 tagIds 嵌进 event 行的 payload:
 * 关联表没有 updatedAt 也没有墓碑,单独同步会遭遇两个经典难题 ——
 * 改关联不产生新版本(推不出去)、取消关联无法传播(另一台设备上复活)。
 * 挂在事件行上之后,这两个问题都随事件行的 LWW 一起解决:
 * 改关联必然伴随事件写入(仓库层保证),事件行赢了,payload 里的 tagIds 就是权威。
 *
 * **不携带 id**:行的 id 由 SyncRowDto.id 权威承载,payload 里再放一份是第二个真相源。
 */
@Serializable
data class EventPayload(
    val title: String,
    val allDay: Boolean,
    val startAt: Long,
    val endAt: Long,
    val location: String? = null,
    val notes: String? = null,
    val colorSlot: Int,
    val priority: String,
    val reminderLeadMinutes: Int? = null,
    val ruleType: String,
    val ruleInterval: Int,
    val ruleWeekdays: String? = null,
    val ruleMonthlyMode: String? = null,
    val ruleMonthlyDay: Int? = null,
    val ruleMonthlyNth: Int? = null,
    val ruleMonthlyWeekday: Int? = null,
    val ruleEndType: String? = null,
    val ruleEndDate: Long? = null,
    val ruleCount: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    /** 本事件当前挂的标签;应用到本地时按"本地存在的标签"过滤 */
    val tagIds: List<String> = emptyList(),
)

@Serializable
data class TagPayload(
    val name: String,
    val colorSlot: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Serializable
data class ExceptionPayload(
    val masterEventId: String,
    val originalDate: Long,
    val isCancelled: Boolean,
    val overrideTitle: String? = null,
    val overrideAllDay: Boolean? = null,
    val overrideStartAt: Long? = null,
    val overrideEndAt: Long? = null,
    val overrideLocation: String? = null,
    val overrideNotes: String? = null,
    val overrideColorSlot: Int? = null,
    val overridePriority: String? = null,
    val overrideReminderLeadMinutes: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * 墓碑(v0.3 例外墓碑补做)。默认 false:老客户端推来的 payload 没有这个字段,
     * 解出来就是"活着" —— 它们本来也从不产生删除,语义一致。
     */
    val isDeleted: Boolean = false,
)

/** 实体种类常量(与服务端 SyncEntity 的取值约定对齐) */
object SyncEntity {
    const val EVENT = "event"
    const val EXCEPTION = "exception"
    const val TAG = "tag"
}

/** payload 编解码的统一入口 */
object SyncPayloads {
    private val json = Json { ignoreUnknownKeys = true }

    fun event(entity: EventEntity, tagIds: List<String>): JsonElement =
        json.encodeToJsonElement(
            EventPayload(
                title = entity.title,
                allDay = entity.allDay,
                startAt = entity.startAt,
                endAt = entity.endAt,
                location = entity.location,
                notes = entity.notes,
                colorSlot = entity.colorSlot,
                priority = entity.priority,
                reminderLeadMinutes = entity.reminderLeadMinutes,
                ruleType = entity.ruleType,
                ruleInterval = entity.ruleInterval,
                ruleWeekdays = entity.ruleWeekdays,
                ruleMonthlyMode = entity.ruleMonthlyMode,
                ruleMonthlyDay = entity.ruleMonthlyDay,
                ruleMonthlyNth = entity.ruleMonthlyNth,
                ruleMonthlyWeekday = entity.ruleMonthlyWeekday,
                ruleEndType = entity.ruleEndType,
                ruleEndDate = entity.ruleEndDate,
                ruleCount = entity.ruleCount,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isDeleted = entity.isDeleted,
                tagIds = tagIds,
            ),
        )

    fun tag(entity: TagEntity): JsonElement =
        json.encodeToJsonElement(
            TagPayload(
                name = entity.name,
                colorSlot = entity.colorSlot,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isDeleted = entity.isDeleted,
            ),
        )

    fun exception(entity: EventExceptionEntity): JsonElement =
        json.encodeToJsonElement(
            ExceptionPayload(
                masterEventId = entity.masterEventId,
                originalDate = entity.originalDate,
                isCancelled = entity.isCancelled,
                overrideTitle = entity.overrideTitle,
                overrideAllDay = entity.overrideAllDay,
                overrideStartAt = entity.overrideStartAt,
                overrideEndAt = entity.overrideEndAt,
                overrideLocation = entity.overrideLocation,
                overrideNotes = entity.overrideNotes,
                overrideColorSlot = entity.overrideColorSlot,
                overridePriority = entity.overridePriority,
                overrideReminderLeadMinutes = entity.overrideReminderLeadMinutes,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isDeleted = entity.isDeleted,
            ),
        )

    fun decodeEvent(payload: JsonElement): EventPayload = json.decodeFromJsonElement(EventPayload.serializer(), payload)

    fun decodeTag(payload: JsonElement): TagPayload = json.decodeFromJsonElement(TagPayload.serializer(), payload)

    fun decodeException(payload: JsonElement): ExceptionPayload =
        json.decodeFromJsonElement(ExceptionPayload.serializer(), payload)

    /** 行排序键:标签最先落(事件 payload 里的 tagIds 引用它们),事件次之,例外最后 */
    fun order(entity: String): Int = when (entity) {
        SyncEntity.TAG -> 0
        SyncEntity.EVENT -> 1
        else -> 2
    }
}
