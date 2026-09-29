package com.lnx.app

import android.app.Application
import com.lnx.app.core.notification.ReminderPlanner
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class LnxApplication : Application() {

    @Inject
    lateinit var reminderPlanner: ReminderPlanner

    /**
     * 进程启动即重排(spec §3.8):系统回收进程 / 用户强制停止后,之前排的闹钟不可靠,
     * 开机广播只覆盖"重启设备"这一种情况。每次重排都是幂等的全量覆盖,多跑一次无副作用。
     */
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runCatching { reminderPlanner.reschedule() }
        }
    }
}
