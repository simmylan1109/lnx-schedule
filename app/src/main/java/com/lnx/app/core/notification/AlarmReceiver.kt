package com.lnx.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 到点接收器:弹通知 + 立刻补排下一次(spec §3.8"重复事件逐次排期")。
 *
 * 静默与否在**到点这一刻**才算 —— 排闹钟时把静音标志写死是错的,排期和响铃时刻可能差几天。
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notifier: ReminderNotifier

    @Inject
    lateinit var planner: ReminderPlanner

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
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
        val firedAt = fromEpochMillis(System.currentTimeMillis())

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                notifier.show(
                    reminder = reminder,
                    silent = DndPolicy.isSilent(
                        at = firedAt,
                        enabled = DndSettings.enabled(),
                        startMinute = DndSettings.startMinute(),
                        endMinute = DndSettings.endMinute(),
                    ),
                )
                // 这次已经响过,重排时它自然会被"提醒时刻 > now"过滤掉,链条自动往下走
                planner.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
