package com.lnx.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.core.common.LocaleContext
import com.lnx.app.core.common.LnxLocale
import com.lnx.app.core.common.LocalLnxLocale
import com.lnx.app.core.designsystem.LnxMotion
import com.lnx.app.core.designsystem.LnxTheme
import com.lnx.app.core.notification.ReminderNotifier
import com.lnx.app.core.notification.fromEpochMillis
import com.lnx.app.core.settings.LnxSettings
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.SettingsRepository
import com.lnx.app.feature.calendar.CalendarScreen
import com.lnx.app.feature.calendar.OpenEventRequest
import com.lnx.app.feature.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** 点提醒通知带过来的打开请求(spec §3.8);Activity 是 singleTask,已在前台时走 onNewIntent */
    private var openRequest by mutableStateOf<OpenEventRequest?>(null)

    /**
     * 语言在这里换(spec §10):`attachBaseContext` 是同步方法,读不了 DataStore,
     * 所以语言值放在 [LocaleContext] 的进程缓存里,这里直接取。
     * 用户在设置里改语言 → 写缓存 → `recreate()` → 重新走这里,新语言生效。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleContext.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 首次进入进程:从存储同步读一次语言(之后都走缓存,不碰磁盘)
        LocaleContext.refreshFromDisk { settingsRepository.current().language }
        // 通知权限不在这里要:用户点开 App 的那一刻还不知道提醒是干什么的。
        // 改成在编辑器里"真的设了提醒"那一刻才问(spec §3.8),引导页第 3 页会问一次。
        handleOpenRequest(intent)
        setContent {
            // null = 设置还没吐出真值(首帧那一刻)。
            // 这一帧**不能**判引导:出厂值 onboardingDone=false,直接判会让每次冷启动
            // 都先闪一下引导页。设置到了再决定进日历还是进引导。
            val loaded by settingsRepository.settings
                .map { it: LnxSettings? -> it }
                .collectAsStateWithLifecycle(initialValue = null)
            val settings = loaded ?: SettingsDefaults.snapshot()
            Crossfade(
                targetState = settings.themeSlot,
                animationSpec = tween(LnxMotion.THEME_CROSSFADE_MILLIS),
                label = "theme-slot",
            ) { slot ->
                LnxTheme(slot = slot, darkMode = settings.darkMode) {
                    // 程序化文案(日期/星期/规则描述)从这里取语言;界面文案由 Context 的
                    // Configuration 决定,两者同源,不会出现"中文标题 + 英文日期"
                    CompositionLocalProvider(
                        LocalLnxLocale provides LnxLocale.resolve(settings.language),
                    ) {
                        Surface(modifier = Modifier.fillMaxSize().testTag("app_root")) {
                            if (loaded != null && !settings.onboardingDone) {
                                OnboardingScreen()
                            } else {
                                CalendarScreen(
                                    openRequest = openRequest,
                                    onOpenRequestConsumed = { openRequest = null },
                                )
                            }
                        }
                    }
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
