package com.lnx.app.core.backup

/** spec §3.13 的两种导入方式 */
enum class ImportMode {
    /** 按 id 去重,同 id 冲突取 updatedAt 新的一方 */
    MERGE,

    /** 清空现有数据后导入 */
    OVERWRITE,
}

/** 摘要(spec §3.13:导入前先展示 `包含 N 个事件、M 个标签`) */
data class ImportSummary(
    val eventCount: Int,
    val tagCount: Int,
    /** 合并时会跳过多少条已存在的(spec §3.13「已存在的跳过」,让用户预期到结果) */
    val skippedCount: Int,
    /** 覆盖模式会清掉多少条现有数据(用于二次警告的措辞) */
    val currentCount: Int,
)

/** 导入结果计数,用于导入后给一句反馈 */
data class ImportResult(
    val eventsAdded: Int,
    val eventsSkipped: Int,
    val tagsAdded: Int,
    val eventsReplaced: Int,
)

interface BackupRepository {
    /** 导出:把库里的事件 / 例外 / 标签 / 关联拍成一份快照 */
    suspend fun snapshot(now: Long): Backup

    /** 当前库里未删事件条数(覆盖导入的二次警告要用) */
    suspend fun currentEventCount(): Int

    /** 只读地算一遍摘要,不落库 —— 用户点"导入"到点"确认"之间数据可能已经变了 */
    suspend fun summarize(backup: Backup, mode: ImportMode): ImportSummary

    suspend fun import(backup: Backup, mode: ImportMode): ImportResult
}
