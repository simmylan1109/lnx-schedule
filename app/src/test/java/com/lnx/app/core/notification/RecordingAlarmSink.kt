package com.lnx.app.core.notification

import java.time.LocalDateTime

/**
 * 记录型假闹钟落点:纯 JVM 环境没有 AlarmManager,重排这类断言靠它。
 * 同时把续排闹钟排没排上也记下来 —— 那是"提醒链条会不会断"的唯一可见信号。
 */
class RecordingAlarmSink : ReminderAlarmSink {
    var scheduleCount = 0
    var last: List<ScheduledReminder> = emptyList()
    var continueAt: LocalDateTime? = null
    val allScheduled = mutableListOf<List<ScheduledReminder>>()

    val rescheduleCount: Int get() = scheduleCount

    override fun schedule(reminders: List<ScheduledReminder>, continueAt: LocalDateTime?) {
        scheduleCount++
        last = reminders
        allScheduled += reminders
        this.continueAt = continueAt
    }

    override fun cancelAll() = Unit
}
