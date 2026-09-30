package com.lnx.app.core.data

import androidx.room.withTransaction
import com.lnx.app.core.backup.Backup
import com.lnx.app.core.backup.BackupEvent
import com.lnx.app.core.backup.BackupEventTag
import com.lnx.app.core.backup.BackupException
import com.lnx.app.core.backup.BackupRepository
import com.lnx.app.core.backup.BackupTag
import com.lnx.app.core.backup.ImportMode
import com.lnx.app.core.backup.ImportResult
import com.lnx.app.core.backup.ImportSummary
import com.lnx.app.core.database.LnxDatabase
import com.lnx.app.core.database.entity.EventEntity
import com.lnx.app.core.database.entity.EventExceptionEntity
import com.lnx.app.core.database.entity.EventTagCrossRef
import com.lnx.app.core.database.entity.TagEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val db: LnxDatabase,
) : BackupRepository {

    private val eventDao get() = db.eventDao()
    private val exceptionDao get() = db.eventExceptionDao()
    private val tagDao get() = db.tagDao()

    /**
     * 导出快照。**墓碑(软删行)一起带走** —— 覆盖导入要能还原"这件事被删过",
     * 否则用户换手机后删掉的日程会自己长回来。
     */
    override suspend fun snapshot(now: Long): Backup = Backup(
        exportedAt = now,
        events = eventDao.allOnce().map { it.toBackup() },
        exceptions = exceptionDao.allOnce().map { it.toBackup() },
        tags = tagDao.allOnce().map { it.toBackup() },
        eventTags = tagDao.allEventTags().map { BackupEventTag(it.eventId, it.tagId) },
    )

    override suspend fun currentEventCount(): Int = eventDao.allOnce().count { !it.isDeleted }

    override suspend fun summarize(backup: Backup, mode: ImportMode): ImportSummary {
        val current = eventDao.allOnce().associateBy { it.id }
        val skipped = if (mode == ImportMode.MERGE) {
            backup.events.count { incoming ->
                // 与 import() 用同一条判据:existing 比 incoming 新(或一样新)才算跳过
                current[incoming.id]?.let { it.updatedAt >= incoming.updatedAt } == true
            }
        } else {
            0
        }
        return ImportSummary(
            eventCount = backup.events.count { !it.isDeleted },
            tagCount = backup.tags.count { !it.isDeleted },
            skippedCount = skipped,
            currentCount = current.values.count { !it.isDeleted },
        )
    }

    /**
     * 导入。整段包在一个事务里:清到写之间不能有缝,否则中途失败会留下"标签没了、事件还在"的半截库。
     */
    override suspend fun import(backup: Backup, mode: ImportMode): ImportResult = db.withTransaction {
        if (mode == ImportMode.OVERWRITE) {
            // 关联表先清。**这里没有外键约束**——关联表只声明了主键,`tags`/`events` 上的
            // 外键是"逻辑上"的(Room 实体里没写 ForeignKey),所以清空顺序靠约定而不是数据库
            // 兜底:先关引用方(关联表),再关被引用方,否则删完主表会留下查不到的悬空关联。
            tagDao.clearAllRefs()
            exceptionDao.clearAll()
            eventDao.clearAll()
            tagDao.clearAll()
            writeAll(backup)
            return@withTransaction ImportResult(
                // 计数口径与摘要一致:数**未删**的事件,墓碑不算"导入了一条"
                eventsAdded = backup.events.count { !it.isDeleted },
                eventsSkipped = 0,
                tagsAdded = backup.tags.size,
                eventsReplaced = 0,
            )
        }

        // —— 合并(spec §3.13:按 id 去重,已存在的跳过,新的加入)——
        // "跳过"与"同 id 冲突保留 updatedAt 较新的一方"两句合起来的判据:
        // 现有的不比导入的旧 → 保留现有的(跳过);导入的更新 → 覆盖。
        val existingEvents = eventDao.allOnce().associateBy { it.id }.toMutableMap()
        val existingExceptions = exceptionDao.allOnce().associateBy { it.id }.toMutableMap()
        val existingTags = tagDao.allOnce().associateBy { it.id }.toMutableMap()
        val existingRefs: Set<Pair<String, String>> =
            tagDao.allEventTags().mapTo(mutableSetOf()) { it.eventId to it.tagId }

        var added = 0
        var replaced = 0
        var skipped = 0
        for (incoming in backup.events.map { it.toEntity() }) {
            val current = existingEvents[incoming.id]
            when {
                current == null -> {
                    added++
                }
                incoming.updatedAt > current.updatedAt -> {
                    replaced++
                }
                else -> {
                    skipped++
                    continue
                }
            }
            eventDao.upsert(incoming)
            existingEvents[incoming.id] = incoming
        }
        for (incoming in backup.exceptions.map { it.toEntity() }) {
            val current = existingExceptions[incoming.id]
            if (current != null && incoming.updatedAt <= current.updatedAt) continue
            exceptionDao.upsert(incoming)
            existingExceptions[incoming.id] = incoming
        }

        // 标签合并有个 id 之外的坑:tags.name 上有唯一索引,而两台设备各自建的
        // "工作"标签 id 必然不同。直接 upsert 会撞唯一索引 —— @Upsert 吞掉约束异常、
        // 回退按新 id 做 UPDATE 影响 0 行,标签静默消失,但指向它的关联照样插入,
        // 留下一串永远查不到的幽灵关联(终审 P1-2)。
        // 所以先按名字对齐:同名不同 id 视为同一个标签,沿用库里的 id,关联重映射过去。
        val tagIdRemap = HashMap<String, String>()
        var tagsAdded = 0
        for (incoming in backup.tags.map { it.toEntity() }) {
            val current = existingTags[incoming.id]
            if (current != null) {
                if (incoming.updatedAt <= current.updatedAt) {
                    tagIdRemap[incoming.id] = current.id
                    continue
                }
                // 同 id 且更新:名字若已被别的行占用,更新同样会撞唯一索引,只跳过
                val nameOwner = tagDao.findByName(incoming.name)
                if (nameOwner != null && nameOwner.id != current.id) {
                    tagIdRemap[incoming.id] = nameOwner.id
                    continue
                }
                tagDao.upsert(incoming)
                existingTags[incoming.id] = incoming
                tagIdRemap[incoming.id] = incoming.id
                continue
            }
            val sameName = tagDao.findByName(incoming.name)
            if (sameName != null) {
                // 名字已在(哪怕已软删):沿用那一行,绝不造第二个同名标签
                tagIdRemap[incoming.id] = sameName.id
                existingTags[sameName.id] = sameName
                continue
            }
            tagDao.upsert(incoming)
            existingTags[incoming.id] = incoming
            tagIdRemap[incoming.id] = incoming.id
            tagsAdded++
        }

        // 关联:按 (eventId, tagId) 去重,只补缺的;标签 id 先过一遍上面的重映射。
        // 指向不存在的事件/标签的关联直接丢,否则插进去就是一行永远查不到的外键垃圾。
        val eventIds = existingEvents.keys
        val tagIds = existingTags.keys
        val newRefs = backup.eventTags
            .map { it.eventId to (tagIdRemap[it.tagId] ?: it.tagId) }
            .filter { it.first in eventIds && it.second in tagIds }
            .filterNot { it in existingRefs }
            .distinct()
            .map { EventTagCrossRef(it.first, it.second) }
        if (newRefs.isNotEmpty()) tagDao.insertCrossRefs(newRefs)

        ImportResult(
            eventsAdded = added,
            eventsSkipped = skipped,
            tagsAdded = tagsAdded,
            eventsReplaced = replaced,
        )
    }

    private suspend fun writeAll(backup: Backup) {
        if (backup.events.isNotEmpty()) eventDao.upsertAll(backup.events.map { it.toEntity() })
        if (backup.tags.isNotEmpty()) tagDao.upsertAll(backup.tags.map { it.toEntity() })
        if (backup.exceptions.isNotEmpty()) {
            exceptionDao.upsertAll(backup.exceptions.map { it.toEntity() })
        }
        if (backup.eventTags.isNotEmpty()) {
            // 覆盖导入同样要去重:event_tag_cross_ref 主键是 (eventId, tagId),
            // 文件里出现重复对会撞主键,整个事务回滚,用户看到的却只是"文件读写失败"
            tagDao.insertCrossRefs(
                backup.eventTags
                    .map { EventTagCrossRef(it.eventId, it.tagId) }
                    .distinct(),
            )
        }
    }
}

// —— 实体 ↔ 备份 DTO 的映射 ——
// 放在文件底部而不是各自实体的伴生文件里:备份是"整库快照"这个视角关心的映射,
// 拆到四个文件里反而看不出它们是同一件事的两面。

private fun EventEntity.toBackup() = BackupEvent(
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

private fun BackupEvent.toEntity() = EventEntity(
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

private fun EventExceptionEntity.toBackup() = BackupException(
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
)

private fun BackupException.toEntity() = EventExceptionEntity(
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
)

private fun TagEntity.toBackup() = BackupTag(
    id = id,
    name = name,
    colorSlot = colorSlot,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)

private fun BackupTag.toEntity() = TagEntity(
    id = id,
    name = name,
    colorSlot = colorSlot,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)
