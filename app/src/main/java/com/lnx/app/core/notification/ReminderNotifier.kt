package com.lnx.app.core.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lnx.app.MainActivity
import com.lnx.app.R
import com.lnx.app.core.common.LocaleContext
import com.lnx.app.core.common.LnxLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 到点弹系统通知(spec §3.8)。
 * 标题 = 事件标题;正文 = `14:30 开始`(有地点追加 ` · 地点`)。
 * 响铃/震动跟随系统通知设置,App 不另行配置(spec §3.8),免打扰时段只加 `setSilent`。
 *
 * 通知在**进程可能已死**的广播里发出,拿不到 Compose 环境,所以语言取 [LocaleContext]
 * 里的进程缓存,文案走 `strings.xml`(通知渠道名/说明创建后系统会缓存,改语言不会改名,
 * 这是系统行为,不是 bug)。
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 通知栏 id 用事件 id 的稳定哈希,同一事件的多次提醒互相覆盖而不是堆叠 */
    fun show(reminder: ScheduledReminder, silent: Boolean) {
        ensureChannel()
        val locale = LnxLocale.resolve(LocaleContext.language)
        val body = buildString {
            append(
                context.getString(
                    R.string.notif_starts_at,
                    LnxLocale.time(reminder.occurrenceStart.toLocalTime(), locale),
                ),
            )
            reminder.location?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // 自己的日历图标,不再借系统的 ic_dialog_info —— 后者会带着系统主题色,
            // 跟 lnx 摆在一起一眼就看出不是本 App(M5 欠到 M7 才还)。
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(reminder))
            .apply { if (silent) setSilent(true) }
            .build()

        // 没给通知权限时 notify 会抛 SecurityException;提醒静默失效但 App 不能崩(spec §3.8)
        if (!canPostNotifications()) {
            Log.i(TAG, "notifications disabled, skip: ${reminder.eventId}")
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(reminder), notification) }
            .onFailure { Log.w(TAG, "notify failed: ${reminder.eventId}", it) }
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

    /**
     * 建通知渠道。**开机时就建,不等第一次提醒**(M9)。
     *
     * 之前是懒建的:只有 [show] 里才调。后果是用户刚在引导页授了通知权限、去系统
     * "应用 → 通知"里想调一下提醒的响铃/震动,却找不到"日程提醒"这个渠道 —— 得先等到
     * 第一次提醒真的响了,它才会出现在列表里。渠道建好后名字会被系统缓存,所以更得
     * 在用户可能去看之前就建好。
     *
     * 重复调用安全(系统侧幂等),所以 [show] 里那次保留也无妨。
     *
     * 闸门在 `ReminderNotifierTest`(仪器测试):断言建完之后渠道确实在。
     * **但"Application 启动时会调它"这一句没有测试覆盖** —— 仪器测试跑的是
     * `HiltTestApplication`,不会执行 [com.lnx.app.LnxApplication]。和 M8 那次
     * "迁移注册被 TestDatabaseModule 顶掉"是同一类缺口,记在这里免得以后忘了。
     */
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        // NotificationChannelCompat 只在 API 26+ 真正有意义(渠道机制本身就是 26 引入的),
        // 名字/说明照旧走资源,渠道建好后系统会缓存名字 —— 之后换语言不会改渠道名(系统行为)
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notif_channel_name))
                .setDescription(context.getString(R.string.notif_channel_desc))
                .setImportance(NotificationManager.IMPORTANCE_DEFAULT)
                .build(),
        )
    }

    fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    companion object {
        private const val TAG = "lnx-notify"
        const val CHANNEL_ID = "lnx_reminders"

        /**
         * 点通知跳详情用的 extra key。**只有这一处定义**
         * —— 之前 ReminderNotifier 和 MainActivity 各写一份字面量,改一处忘另一处就是静默 bug。
         */
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_OCCURRENCE_START = "occurrence_start"

        fun notificationId(reminder: ScheduledReminder): Int = reminder.eventId.hashCode()
    }
}
