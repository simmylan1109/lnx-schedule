package com.lnx.app.core.backup

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * 备份文件的顶层结构(spec §3.13:含全部事件、例外、标签、关联)。
 *
 * 字段名用 `camelCase` 直接对应实体列名,不做重命名 —— 换手机导出的文件要在别的
 * 版本上也读得懂,靠的就是这套名字稳定;真要改结构就升 [Backup.FORMAT_VERSION] 并在
 * [BackupCodec] 里加分支,而不是悄悄改字段名。
 *
 * 时间一律存 epoch millis / epochDay,与 Room 列一致,导入时不做时区换算
 * (换算只发生在"显示"那一层)。
 */
@Serializable
data class Backup(
    /**
     * 文件标识。**必须真的写进文件里**(`@EncodeDefault(ALWAYS)`,不是普通默认值):
     * 编码器开了 `encodeDefaults = false` 省体积,默认字段一律不落盘 ——
     * 而 format/version 恰好都等于默认值,不加这一句,导出的文件里就一个标记都没有,
     * 任意形状相同的 json 都能被当备份读进来。实机走查时真的导出过一个只剩
     * `exportedAt` 的文件才发现。
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val format: String = Backup.FORMAT,
    /** 格式版本。同样必须落盘,否则"来自更新版本的备份"这道闸门形同虚设 */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val version: Int = Backup.FORMAT_VERSION,
    val exportedAt: Long = 0L,
    val events: List<BackupEvent> = emptyList(),
    val exceptions: List<BackupException> = emptyList(),
    val tags: List<BackupTag> = emptyList(),
    val eventTags: List<BackupEventTag> = emptyList(),
) {
    companion object {
        const val FORMAT = "lnx-backup"

        /** 格式版本:往后加字段只升这个号,旧文件按老分支读 */
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class BackupEvent(
    val id: String,
    val title: String,
    val allDay: Boolean,
    val startAt: Long,
    val endAt: Long,
    val location: String? = null,
    val notes: String? = null,
    val colorSlot: Int,
    val priority: String,
    val reminderLeadMinutes: Int? = null,
    val ruleType: String = "NONE",
    val ruleInterval: Int = 1,
    val ruleWeekdays: String? = null,
    val ruleMonthlyMode: String? = null,
    val ruleMonthlyDay: Int? = null,
    val ruleMonthlyNth: Int? = null,
    val ruleMonthlyWeekday: Int? = null,
    val ruleEndType: String? = null,
    val ruleEndDate: Long? = null,
    val ruleCount: Int? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /**
     * 软删除墓碑必须一起导出(spec §3.13 的覆盖导入要能还原"已删除"这个事实,
     * 否则换机后删掉的事件会复活)。默认 false,老文件没有这个字段时按未删处理。
     */
    val isDeleted: Boolean = false,
)

@Serializable
data class BackupException(
    val id: String,
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
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class BackupTag(
    val id: String,
    val name: String,
    val colorSlot: Int,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
)

@Serializable
data class BackupEventTag(
    val eventId: String,
    val tagId: String,
)
