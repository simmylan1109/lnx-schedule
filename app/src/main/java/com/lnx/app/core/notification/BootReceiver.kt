package com.lnx.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟失效后的重排。三种情况都会让已排的闹钟作废:
 * - `BOOT_COMPLETED`:重启清空全部闹钟;
 * - `MY_PACKAGE_REPLACED`:App 升级时系统会清掉本包的全部闹钟;
 * - `TIMEZONE_CHANGED`:时区变了,已排闹钟仍按旧绝对时刻触发。
 *
 * 只重排、不补发 —— 此刻已经过点的提醒由计算器的"remindAt > now"挡掉。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val planner = reminderEntryPoint(context)?.reminderPlanner() ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                runCatching { planner.reschedule() }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
