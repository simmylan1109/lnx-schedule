package com.lnx.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机后重排(spec §3.8 隐含要求:重启会清空所有闹钟)。
 * 只重排、不补发 —— 开机时已经过点的提醒由计算器的"remindAt > now"挡掉。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val planner = reminderEntryPoint(context)?.reminderPlanner() ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                planner.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
