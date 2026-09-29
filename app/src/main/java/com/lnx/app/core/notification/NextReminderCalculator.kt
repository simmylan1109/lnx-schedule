package com.lnx.app.core.notification

import com.lnx.app.core.domain.model.Occurrence
import java.time.LocalDateTime

/** 一条待排期的提醒:某次发生开始前 [leadMinutes] 分钟响 */
data class ScheduledReminder(
    val eventId: String,
    val occurrenceStart: LocalDateTime,
    val remindAt: LocalDateTime,
    val title: String,
    val location: String?,
) {
    /** 取消闹钟要能按这条反查回同一个 PendingIntent,故 id 必须只由这两项决定 */
    val key: String get() = "$eventId@$occurrenceStart"
}

/**
 * 待提醒清单计算(spec §3.8)。纯函数,不碰 AlarmManager 也不碰数据库。
 *
 * 输入是**已经展开好的发生**(`OccurrenceExpander` 的产物):重复事件的每一次、
 * 被取消掉的、被改期的,都已经算清楚了。提前量取 `Occurrence.event.reminderLeadMinutes`
 * —— 例外 override 带了该字段,所以"仅本次改时间"会连带改掉这次的提前量。
 *
 * 两条硬规则:
 * - 提醒时刻已过(或正好等于 now)的不排 —— 已过期的提醒不补发(spec §3.8);
 * - 单次最多 50 条,按时间先后截断 —— 防止"永不结束的每日事件"一次排出海量闹钟。
 */
object NextReminderCalculator {

    const val MAX_PER_RESCHEDULE = 50

    fun calculate(
        occurrences: List<Occurrence>,
        now: LocalDateTime,
    ): List<ScheduledReminder> = occurrences
        .mapNotNull { occ ->
            val lead = occ.event.reminderLeadMinutes ?: return@mapNotNull null
            if (lead < 0) return@mapNotNull null
            ScheduledReminder(
                eventId = occ.event.id,
                occurrenceStart = occ.start,
                remindAt = occ.start.minusMinutes(lead.toLong()),
                title = occ.event.title,
                location = occ.event.location,
            )
        }
        .filter { it.remindAt > now }
        .sortedWith(compareBy({ it.remindAt }, { it.eventId }))
        .take(MAX_PER_RESCHEDULE)
}
