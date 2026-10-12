package com.lnx.app.core.sync

import com.lnx.app.core.database.dao.EventDao
import com.lnx.app.core.database.dao.EventExceptionDao
import com.lnx.app.core.database.dao.TagDao
import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.EventExceptionEntity
import com.lnx.app.core.database.entity.TagEntity
import com.lnx.app.core.network.SyncRowDto
import javax.inject.Inject
import javax.inject.Singleton

/** 一次合并的结果:实际落库的行(引擎据此更新镜像)与被跳过的行(留证据用) */
data class AppliedResult(
    val applied: List<SyncRowDto>,
    val skipped: List<String>,
)

/**
 * [LocalSyncStore] 的 Room 实现(v0.3 A4)。
 *
 * 合并规则(PLAN-v0.3 §三,服务端 S3 定下的同一套):
 * - 同 id 比 `updatedAt`,大者赢,**相等时服务端赢**(拉取侧:`row.updatedAt >= local.updatedAt` 即应用);
 * - 拉到的墓碑按删除应用;本地不存在且非墓碑 → 插入;本地更新 → 跳过,等推送去比;
 * - 事件行赢的时候,payload 里的 tagIds 覆盖本地关联(**只保留本地真实存在的标签**:
 *   远端引用的标签还没拉到时先丢着,那些行到了自然补齐);
 * - 拉到事件的墓碑 → 级联把本地的例外与关联清掉(母事件没了,它们没有存在的意义);
 * - 例外行与事件同一套(v0.3 例外墓碑补做):行可能是墓碑(payload.isDeleted),
 *   应用一律整行 upsert —— 墓碑也留行,删除才能被再同步出去。
 *
 * payload 解码失败**只跳过那一行**:服务端不解释 payload,坏行拦不住,
 * 能做的是别让一条坏行拖死整批(PLAN-v0.3 S3 的约定)。
 */
@Singleton
class RoomSyncStore @Inject constructor(
    private val eventDao: EventDao,
    private val tagDao: TagDao,
    private val exceptionDao: EventExceptionDao,
) : LocalSyncStore {

    override suspend fun currentRows(): List<SyncRowDto> {
        val events = eventDao.allOnce()
        val tags = tagDao.allOnce()
        val exceptions = exceptionDao.allOnce()
        val refsByEvent = tagDao.allEventTags().groupBy { it.eventId }

        return events.map { event ->
            SyncRowDto(
                entity = SyncEntity.EVENT,
                id = event.id,
                payload = SyncPayloads.event(event, refsByEvent[event.id].orEmpty().map { it.tagId }),
                updatedAt = event.updatedAt,
                deleted = event.isDeleted,
            )
        } + tags.map { tag ->
            SyncRowDto(
                entity = SyncEntity.TAG,
                id = tag.id,
                payload = SyncPayloads.tag(tag),
                updatedAt = tag.updatedAt,
                deleted = tag.isDeleted,
            )
        } + exceptions.map { exception ->
            SyncRowDto(
                entity = SyncEntity.EXCEPTION,
                id = exception.id,
                payload = SyncPayloads.exception(exception),
                updatedAt = exception.updatedAt,
                deleted = exception.isDeleted,
            )
        }
    }

    override suspend fun applyPulled(rows: List<SyncRowDto>): AppliedResult {
        val applied = mutableListOf<SyncRowDto>()
        val skipped = mutableListOf<String>()
        // 标签先落(事件 payload 里的 tagIds 引用它们),事件次之,例外最后
        rows.sortedBy { SyncPayloads.order(it.entity) }.forEach { row ->
            try {
                if (applyOne(row)) applied += row
                // applyOne 返回 false = 本地更新,没动 —— 这行不能进"已应用",
                // 否则镜像会被写成服务端的旧内容,白推一轮
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // 协程退出不是"这行坏了"
            } catch (e: Throwable) {
                skipped += "${row.entity}/${row.id}: ${e.message}"
            }
        }
        return AppliedResult(applied, skipped)
    }

    /** 返回 true = 真的落库了;false = 本地更新,跳过 */
    private suspend fun applyOne(row: SyncRowDto): Boolean {
        when (row.entity) {
            SyncEntity.EVENT -> return applyEvent(row)
            SyncEntity.TAG -> return applyTag(row)
            SyncEntity.EXCEPTION -> return applyException(row)
            else -> error("unknown entity kind from server: ${row.entity}")
        }
    }

    private suspend fun applyEvent(row: SyncRowDto): Boolean {
        val payload = SyncPayloads.decodeEvent(row.payload)
        val local = eventDao.getByIdAny(row.id)
        val localUpdatedAt = local?.updatedAt ?: Long.MIN_VALUE
        if (row.updatedAt < localUpdatedAt) return false // 本地更新,等推送去比

        // 墓碑与否以**行上的 deleted 标志**为准,不信 payload 里的 isDeleted:
        // 引擎的"镜像孤儿"路径推墓碑时复用镜像里的旧载荷(那里面 isDeleted 还是 false),
        // 只认 payload 会把删除读成"复活"(v0.3 例外墓碑补做时审查抓到的回归)
        eventDao.upsert(payload.toEntity(row.id, row.updatedAt).copy(isDeleted = row.deleted))
        if (row.deleted) {
            // 母事件没了:它的例外与关联一并清掉(与本地删除事件的级联行为一致)。
            // 例外打墓碑而不是硬删 —— 与本地 deleteExceptionsFor 同一语义,
            // 时间戳取行的 updatedAt:同一条墓碑反复应用结果不变(幂等)
            exceptionDao.tombstoneForMaster(row.id, row.updatedAt)
            tagDao.clearEventTags(row.id)
        } else {
            tagDao.setEventTags(row.id, payload.tagIds.filter { tagDao.getById(it) != null })
        }
        return true
    }

    private suspend fun applyTag(row: SyncRowDto): Boolean {
        val payload = SyncPayloads.decodeTag(row.payload)
        val local = tagDao.getByIdAny(row.id)
        val localUpdatedAt = local?.updatedAt ?: Long.MIN_VALUE
        if (row.updatedAt < localUpdatedAt) return false

        tagDao.upsert(payload.toEntity(row.id, row.updatedAt).copy(isDeleted = row.deleted))
        if (row.deleted) tagDao.clearTagRefs(row.id)
        return true
    }

    private suspend fun applyException(row: SyncRowDto): Boolean {
        val payload = SyncPayloads.decodeException(row.payload)
        val local = exceptionDao.getFor(payload.masterEventId, payload.originalDate)
        val localUpdatedAt = local?.updatedAt ?: Long.MIN_VALUE
        if (row.updatedAt < localUpdatedAt) return false

        // 与事件同一套:整行 upsert,墓碑也是行;墓碑与否以行上的 deleted 标志为准
        // (镜像孤儿墓碑复用旧载荷,只信 payload 会把删除读成"复活")。
        // 例外 id 是确定性的(母事件+原本发生日),两台设备对同一例外算出同一个 id。
        // 理论兜底:万一 (master,date) 命中的本地行 id 与远端不同(只有手改备份能造出来),
        // 按本地 id 写 —— 否则 @Upsert 撞唯一索引后静默不落库,却计入 applied 写进镜像
        val entity = payload.toEntity(row.id, row.updatedAt).copy(
            isDeleted = row.deleted,
            id = local?.id ?: row.id,
        )
        exceptionDao.upsert(entity)
        return true
    }
}

private fun EventPayload.toEntity(id: String, updatedAt: Long) = EventEntity(
    id = id,
    title = title,
    allDay = allDay,
    startAt = startAt,
    endAt = endAt,
    location = location,
    notes = notes,
    colorSlot = colorSlot,
    priority = priority,
    reminderLeadMinutes = reminderLeadMinutes,
    ruleType = ruleType,
    ruleInterval = ruleInterval,
    ruleWeekdays = ruleWeekdays,
    ruleMonthlyMode = ruleMonthlyMode,
    ruleMonthlyDay = ruleMonthlyDay,
    ruleMonthlyNth = ruleMonthlyNth,
    ruleMonthlyWeekday = ruleMonthlyWeekday,
    ruleEndType = ruleEndType,
    ruleEndDate = ruleEndDate,
    ruleCount = ruleCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)

private fun TagPayload.toEntity(id: String, updatedAt: Long) = TagEntity(
    id = id,
    name = name,
    colorSlot = colorSlot,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)

private fun ExceptionPayload.toEntity(id: String, updatedAt: Long) = EventExceptionEntity(
    id = id,
    masterEventId = masterEventId,
    originalDate = originalDate,
    isCancelled = isCancelled,
    overrideTitle = overrideTitle,
    overrideAllDay = overrideAllDay,
    overrideStartAt = overrideStartAt,
    overrideEndAt = overrideEndAt,
    overrideLocation = overrideLocation,
    overrideNotes = overrideNotes,
    overrideColorSlot = overrideColorSlot,
    overridePriority = overridePriority,
    overrideReminderLeadMinutes = overrideReminderLeadMinutes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)