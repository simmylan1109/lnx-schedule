package com.lnx.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lnx.app.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 到点弹系统通知(spec §3.8)。
 * 标题 = 事件标题;正文 = `HH:mm 开始`(有地点追加 ` · 地点`)。
 * 响铃/震动跟随系统通知设置,App 不另行配置(spec §3.8),免打扰时段只加 `setSilent`。
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 通知栏 id 用事件 id 的稳定哈希,同一事件的多次提醒互相覆盖而不是堆叠 */
    fun show(reminder: ScheduledReminder, silent: Boolean) {
        ensureChannel()
        val body = buildString {
            append(reminder.occurrenceStart.format(timeFormatter))
            append(" 开始")
            reminder.location?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(reminder.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(reminder))
            .apply { if (silent) setSilent(true) }
            .build()

        // 没给通知权限时 notify 会抛 SecurityException;提醒静默失效但 App 不能崩(spec §3.8)
        if (!canPostNotifications()) return
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(reminder), notification) }
    }

    private fun contentIntent(reminder: ScheduledReminder): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId(reminder),
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_EVENT_ID, reminder.eventId)
            .putExtra(EXTRA_OCCURRENCE_START, reminder.occurrenceStart.toEpochMillis()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "日程提醒", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "事件开始前的提醒" },
        )
    }

    fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    companion object {
        const val CHANNEL_ID = "lnx_reminders"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_OCCURRENCE_START = "occurrence_start"

        fun notificationId(reminder: ScheduledReminder): Int = reminder.eventId.hashCode()
    }
}
