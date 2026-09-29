package com.lnx.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 到点接收器:弹通知 + 立刻补排下一次(spec §3.8"重复事件逐次排期")。
 *
 * 静默与否在**到点这一刻**才算 —— 排闹钟时把静音标志写死是错的,排期和响铃时刻可能差几天。
 *
 * **`goAsync()` 整个 `onReceive` 只能调一次,且返回值必须按可空处理**:
 * AOSP 实现是 `res = mPendingResult; mPendingResult = null; return res` —— 第二次调用恒返回 null,
 * 在 null 上调 `finish()` 会 NPE 并把进程带崩。曾经的写法是"fire() 取一次、内部再调
 * reschedule() 又取一次",结果**每次提醒到点都崩**(实测 logcat:`NullPointerException ...
 * at AlarmReceiver$reschedule$1.invokeSuspend(AlarmReceiver.kt:69)`),因为通知是先发出去的,
 * 走查截图看不出异常。所以:只取一次,把它传给下面所有的协程。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE &&
            intent.action != ReminderScheduler.ACTION_CONTINUE
        ) return
        val deps = reminderEntryPoint(context)
        if (deps == null) {
            Log.w(TAG, "hilt entry point unavailable, drop ${intent.action}")
            return
        }
        // 只取这一次;判空是必要的(平台类型,第二次调用才会返回 null,这里给未来改代码的人留闸门)
        val pending = goAsync() ?: return
        Log.i(TAG, "onReceive ${intent.action}")

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    // 续排闹钟:只把窗口之外的提醒接上,不弹通知
                    ReminderScheduler.ACTION_CONTINUE -> runCatching { deps.reminderPlanner().reschedule() }
                    else -> fire(intent, deps)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(intent: Intent, deps: ReminderEntryPoint) {
        val eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return
        val occurrenceStart = intent.getLongExtra(ReminderScheduler.EXTRA_OCCURRENCE_START, 0L)
        if (occurrenceStart == 0L) return
        val reminder = ScheduledReminder(
            eventId = eventId,
            occurrenceStart = fromEpochMillis(occurrenceStart),
            remindAt = fromEpochMillis(intent.getLongExtra(ReminderScheduler.EXTRA_REMIND_AT, occurrenceStart)),
            title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty(),
            location = intent.getStringExtra(ReminderScheduler.EXTRA_LOCATION),
        )
        // 和下面的补排对称:这是裸 CoroutineScope,未捕获异常会直接杀进程
        runCatching {
            deps.reminderNotifier().show(
                reminder = reminder,
                silent = DndPolicy.isSilent(
                    at = fromEpochMillis(System.currentTimeMillis()),
                    enabled = DndSettings.enabled(),
                    startMinute = DndSettings.startMinute(),
                    endMinute = DndSettings.endMinute(),
                ),
            )
        }.onFailure { Log.w(TAG, "notify for $eventId failed", it) }
        // 这次已经响过,重排时它自然会被"提醒时刻 > now"过滤掉,链条自动往下走
        runCatching { deps.reminderPlanner().reschedule() }
            .onFailure { Log.w(TAG, "reschedule after fire failed", it) }
    }

    private companion object {
        private const val TAG = "lnx-remind"
    }
}
