package com.lnx.app.core.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 闹钟落点。抽成接口是为了让 [ReminderPlanner] 在 JVM 单测里能塞假实现
 * ——AlarmManager 在纯 JVM 环境里根本不存在。
 */
interface ReminderAlarmSink {
    /**
     * 全量重排。[continueAt] 非 null 时额外排一个"续排"闹钟到该时刻,
     * 它只触发下一次重排、不弹通知,用来保证窗口之外的提醒也能排上(见 [ReminderPlanner])。
     */
    fun schedule(reminders: List<ScheduledReminder>, continueAt: LocalDateTime? = null)
    fun cancelAll()
}

/**
 * 把待提醒清单排进系统闹钟(spec §3.8)。
 *
 * **请求码 = 该条在清单里的序号**,不是事件 id 也不是时刻:清单每次都从同一套纯函数
 * (NextReminderCalculator)按时间排序重算,序号因此是稳定的"槽位"。重排 = 先把
 * [SLOT_COUNT] 个槽位全取消再按新清单排,不需要另存一张"已排了什么"的表,也不会漏取消
 * (删掉的事件留下的旧闹钟一定落在某个槽位上,会被扫掉)。
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderAlarmSink {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    /** 全量重排:清空所有槽位后按 [reminders] 重新排;[continueAt] 见 [ReminderAlarmSink.schedule] */
    override fun schedule(reminders: List<ScheduledReminder>, continueAt: LocalDateTime?) {
        cancelAll()
        reminders.take(SLOT_COUNT).forEachIndexed { index, reminder ->
            setAlarm(index, ACTION_FIRE, reminder.remindAt, fireIntent(reminder, context))
        }
        if (continueAt != null) {
            setAlarm(CONTINUE_SLOT, ACTION_CONTINUE, continueAt, continueIntent(context))
        }
    }

    override fun cancelAll() {
        val am = alarmManager ?: return
        (0 until SLOT_COUNT).forEach { cancelSlot(am, it, ACTION_FIRE) }
        cancelSlot(am, CONTINUE_SLOT, ACTION_CONTINUE)
    }

    private fun cancelSlot(am: AlarmManager, slot: Int, action: String) {
        val pending = existingPendingIntent(slot, action) ?: return
        am.cancel(pending)
        pending.cancel()
    }

    private fun setAlarm(slot: Int, action: String, triggerAt: LocalDateTime, intent: Intent) {
        val am = alarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // 精确闹钟在 Android 12+ 需要用户授权;拿不到就退化成窗口闹钟(可能晚几分钟),
        // spec §3.8 已写明"受系统限制时尽力而为"
        if (canScheduleExact(am)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMillis(), pending)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt.toEpochMillis(), WINDOW_MILLIS, pending)
        }
    }

    /**
     * 取回已排的 PendingIntent。**不填 extras** 是关键:PendingIntent 的相等性只看
     * component/action/data/category,extras 不参与 —— 所以这里能命中带 extras 创建的那些。
     */
    private fun existingPendingIntent(slot: Int, action: String): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            slot,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun canScheduleExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    companion object {
        /** 槽位数 = 单次重排上限,与 [NextReminderCalculator.MAX_PER_RESCHEDULE] 对齐 */
        const val SLOT_COUNT = NextReminderCalculator.MAX_PER_RESCHEDULE

        /** 续排闹钟单独占一个槽位(不占用提醒的 50 个) */
        const val CONTINUE_SLOT = SLOT_COUNT

        /** 无精确权限时的兜底窗口:约 ±5 分钟 */
        const val WINDOW_MILLIS = 5 * 60 * 1000L

        const val ACTION_FIRE = "com.lnx.app.action.REMINDER_FIRE"

        /** 续排:到点只重排,不弹通知 */
        const val ACTION_CONTINUE = "com.lnx.app.action.REMINDER_CONTINUE"

        const val EXTRA_EVENT_ID = "com.lnx.app.extra.EVENT_ID"
        const val EXTRA_OCCURRENCE_START = "com.lnx.app.extra.OCCURRENCE_START"
        const val EXTRA_REMIND_AT = "com.lnx.app.extra.REMIND_AT"
        const val EXTRA_TITLE = "com.lnx.app.extra.TITLE"
        const val EXTRA_LOCATION = "com.lnx.app.extra.LOCATION"

        /** 闹钟 Intent:显式指向 [AlarmReceiver](避免被别的应用截获) */
        fun fireIntent(reminder: ScheduledReminder, context: Context): Intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_EVENT_ID, reminder.eventId)
                .putExtra(EXTRA_OCCURRENCE_START, reminder.occurrenceStart.toEpochMillis())
                .putExtra(EXTRA_REMIND_AT, reminder.remindAt.toEpochMillis())
                .putExtra(EXTRA_TITLE, reminder.title)
                .putExtra(EXTRA_LOCATION, reminder.location)

        fun continueIntent(context: Context): Intent =
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_CONTINUE)
    }
}

/** 事件发生时刻 ↔ 闹钟时间戳;用系统时区,与 Room 落库的 [com.lnx.app.core.data.TimeMapping] 一致 */
fun LocalDateTime.toEpochMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun fromEpochMillis(millis: Long): LocalDateTime =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
