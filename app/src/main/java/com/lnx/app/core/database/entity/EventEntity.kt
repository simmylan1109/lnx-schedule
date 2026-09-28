package com.lnx.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 事件表(spec §4.2)。
 * 重复规则字段在本里程碑就建好表(M4 启用),避免后续 migration。
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val allDay: Boolean,
    /** 普通事件:epoch millis;全天事件:起始日本地 00:00 的 millis */
    val startAt: Long,
    /** 排他:全天事件为结束日次日 00:00 */
    val endAt: Long,
    val location: String?,
    val notes: String?,
    /** 0..7 色位(spec §5.4),存编号而非具体颜色,换主题时自动跟随 */
    val colorSlot: Int,
    /** P0..P3 */
    val priority: String,
    /** null = 不提醒;否则 5/15/30/60 */
    val reminderLeadMinutes: Int?,
    // —— 重复规则(M4 启用)——
    /** NONE / DAILY / WEEKLY / MONTHLY / YEARLY */
    val ruleType: String,
    /** 每 X 天/周/月/年 */
    val ruleInterval: Int,
    /** WEEKLY 用,ISO 星期值(1=周一..7=周日)的 csv */
    val ruleWeekdays: String?,
    /** BY_MONTHDAY / BY_NTH_WEEKDAY */
    val ruleMonthlyMode: String?,
    val ruleMonthlyDay: Int?,
    val ruleMonthlyNth: Int?,
    val ruleMonthlyWeekday: Int?,
    /** NEVER / UNTIL / COUNT */
    val ruleEndType: String?,
    /** UNTIL 时为 epochDay */
    val ruleEndDate: Long?,
    val ruleCount: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
)
