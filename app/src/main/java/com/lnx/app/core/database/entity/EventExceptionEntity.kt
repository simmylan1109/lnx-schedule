package com.lnx.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lnx.app.core.data.toEpochMillis
import com.lnx.app.core.data.toLocalDateTime
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventException
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.Priority
import java.time.LocalDate

/**
 * 重复事件的例外表(spec §4.3):对某一次发生的取消或覆盖。
 *
 * override 用**可空列**而非 JSON(kotlinx.serialization 排在 M7 备份才引入;
 * 可空列与 spec §4.3"override 字段组,未改字段沿用母事件"措辞一致,也可直接被 SQL 查询)。
 * 单次编辑("仅本次")总是写全字段;取消那次时 override 列全为 null。
 */
@Entity(
    tableName = "event_exceptions",
    indices = [
        Index("masterEventId"),
        // 同一母事件的同一"原本发生日"只有一条例外;主键用确定性 id,重复写同一例外的语义 = 覆盖
        Index(value = ["masterEventId", "originalDate"], unique = true),
    ],
)
data class EventExceptionEntity(
    @PrimaryKey val id: String,
    val masterEventId: String,
    /** 被例外的"原本发生日"(epochDay) */
    val originalDate: Long,
    /** true = 那次取消(不显示);false = 那次被改(看 override 字段组) */
    val isCancelled: Boolean,
    // —— override 字段组:null = 沿用母事件 ——
    val overrideTitle: String?,
    val overrideAllDay: Boolean?,
    val overrideStartAt: Long?,
    val overrideEndAt: Long?,
    val overrideLocation: String?,
    val overrideNotes: String?,
    val overrideColorSlot: Int?,
    val overridePriority: String?,
    val overrideReminderLeadMinutes: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 例外 id 确定性生成:同一母事件 + 同一原本发生日 = 同一行,重复写即覆盖(幂等) */
fun eventExceptionId(masterId: String, originalDate: LocalDate): String =
    "$masterId:${originalDate.toEpochDay()}"

fun EventExceptionEntity.toDomain(): EventException = EventException(
    masterId = masterEventId,
    originalDate = LocalDate.ofEpochDay(originalDate),
    cancelled = isCancelled,
    override = overrideEvent(),
)

/**
 * 还原 override 事件。脏数据防御:核心字段(标题/起止/全天)缺任何一个就视为
 * "没有有效 override",发生按母事件槽位显示,而不是让整条展开流崩掉。
 */
private fun EventExceptionEntity.overrideEvent(): Event? {
    val title = overrideTitle ?: return null
    val startAt = overrideStartAt ?: return null
    val endAt = overrideEndAt ?: return null
    val allDay = overrideAllDay ?: return null
    return Event(
        id = masterEventId,
        title = title,
        allDay = allDay,
        start = startAt.toLocalDateTime(),
        end = endAt.toLocalDateTime(),
        location = overrideLocation,
        notes = overrideNotes,
        colorSlot = overrideColorSlot ?: 0,
        priority = runCatching { Priority.valueOf(overridePriority ?: Priority.P2.name) }.getOrDefault(Priority.P2),
        reminderLeadMinutes = overrideReminderLeadMinutes,
        // 例外不改规则:占位规则在展开时一定被换成母事件的规则
        rule = EventRule(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun EventException.toEntity(nowMillis: Long): EventExceptionEntity {
    val override = override
    return EventExceptionEntity(
        id = eventExceptionId(masterId, originalDate),
        masterEventId = masterId,
        originalDate = originalDate.toEpochDay(),
        isCancelled = cancelled,
        overrideTitle = override?.title,
        overrideAllDay = override?.allDay,
        overrideStartAt = override?.start?.toEpochMillis(),
        overrideEndAt = override?.end?.toEpochMillis(),
        overrideLocation = override?.location,
        overrideNotes = override?.notes,
        overrideColorSlot = override?.colorSlot,
        overridePriority = override?.priority?.name,
        overrideReminderLeadMinutes = override?.reminderLeadMinutes,
        createdAt = nowMillis,
        updatedAt = nowMillis,
    )
}
