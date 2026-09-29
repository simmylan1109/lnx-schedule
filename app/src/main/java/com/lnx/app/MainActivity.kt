package com.lnx.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.LnxTheme
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.notification.fromEpochMillis
import com.lnx.app.core.notification.ReminderNotifier
import com.lnx.app.feature.calendar.CalendarScreen
import com.lnx.app.feature.calendar.OpenEventRequest
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 点提醒通知带过来的打开请求(spec §3.8);Activity 是 singleTask,已在前台时走 onNewIntent */
    private var openRequest by mutableStateOf<OpenEventRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 通知权限不在这里要:用户点开 App 的那一刻还不知道提醒是干什么的。
        // 改成在编辑器里"真的设了提醒"那一刻才问(spec §3.8),引导页第 3 页在 M7 接入。
        handleOpenRequest(intent)
        setContent {
            LnxTheme(slot = ThemeSlot.MATERIAL_YOU, darkMode = DarkMode.FOLLOW_SYSTEM) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CalendarScreen(openRequest = openRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenRequest(intent)
    }

    /**
     * 从 Intent 里取出通知带的打开请求。
     * 由 onCreate 与 onNewIntent 两处调用;**公开**是为了让仪器测试能走同一条真实路径
     * (后台点通知走 onNewIntent,而它是 Activity 的受保护方法)。
     */
    fun handleOpenRequest(intent: Intent?) {
        val eventId = intent?.getStringExtra(ReminderNotifier.EXTRA_EVENT_ID) ?: return
        val start = intent.getLongExtra(ReminderNotifier.EXTRA_OCCURRENCE_START, 0L)
        if (start == 0L) return
        openRequest = OpenEventRequest(eventId, fromEpochMillis(start))
        // 清掉 extra:配置变更导致 Activity 重建时 onCreate 再读一次会把详情卡重复弹出来
        intent.removeExtra(ReminderNotifier.EXTRA_EVENT_ID)
        intent.removeExtra(ReminderNotifier.EXTRA_OCCURRENCE_START)
    }
}
