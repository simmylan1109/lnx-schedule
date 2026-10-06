package com.lnx.app.core.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot
import kotlinx.coroutines.flow.Flow

/** 一次读全:设置页、主题、提醒、引导共用同一份快照 */
data class LnxSettings(
    val themeSlot: ThemeSlot,
    val darkMode: DarkMode,
    /** 默认提前提醒分钟数;null = 默认不提醒 */
    val reminderLeadMinutes: Int?,
    val dndEnabled: Boolean,
    val dndStartMinute: Int,
    val dndEndMinute: Int,
    val weekStartMonday: Boolean,
    /** "system" | "zh" | "en" */
    val language: String,
    val onboardingDone: Boolean,
    /**
     * 首启隐私披露页是否已读过(v0.3 A1)。出厂 false = 第一次启动还没给用户看过,
     * 看过之后才不再弹。**和 [onboardingDone] 分开记**:不同意联网的用户同样要
     * 走完三页引导,所以这两个标记不是一回事,不能合成一个。
     */
    val privacyDisclosureDone: Boolean,
    /**
     * 用户自己的同步服务器地址(v0.3 A3),如 `https://1.2.3.4:8443`。
     * **空串 = 还没配置过**。它只在用户自己的机器/服务器之间有意义,不入任何
     * 备份 JSON 之外的东西;**公网地址绝不进仓库**(2026-10-02 事故红线)。
     */
    val serverBaseUrl: String,
    /**
     * 自动同步(v0.3 A5):进日历时顺手拉推一轮。默认开;关掉后只在设置里
     * 手动点「立即同步」——**关掉的只是自动,不撤已同步的数据**(协议本来就只进不出)。
     */
    val autoSync: Boolean,
    /**
     * 上次成功导出备份的时刻(epoch millis);**0 = 从未导出**(v0.2 补欠账 ②)。
     * 放在设置里而不是库表里:它描述的是"这台机器的备份习惯",不是日程数据 ——
     * 进备份 JSON 反而会把"换机前导出过"的假历史带过去。
     */
    val lastExportAt: Long,
)

/**
 * 键名**一律存字符串**(spec 全局约束:蛇形命名)。
 *
 * 为什么不用 DataStore 的强类型 key(int/boolean key):强类型能防住编译期写错类型,
 * 但挡不住"文件里存的是旧版本写的另一种类型"——那会在读的时候抛 ClassCastException,
 * 整个设置流挂掉、App 启动就崩。字符串 + 解析可以优雅回落出厂值(见 [SettingsRepositoryImpl])。
 */
object SettingKeys {
    val THEME_SLOT = stringPreferencesKey("theme_slot")
    val DARK_MODE = stringPreferencesKey("dark_mode")
    val REMINDER_LEAD = stringPreferencesKey("reminder_lead_minutes")
    val DND_ENABLED = stringPreferencesKey("dnd_enabled")
    val DND_START = stringPreferencesKey("dnd_start_minute")
    val DND_END = stringPreferencesKey("dnd_end_minute")
    val WEEK_START_MONDAY = stringPreferencesKey("week_start_monday")
    val LANGUAGE = stringPreferencesKey("language")
    val ONBOARDING_DONE = stringPreferencesKey("onboarding_done")
    val PRIVACY_DISCLOSURE_DONE = stringPreferencesKey("privacy_disclosure_done")
    val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
    val AUTO_SYNC = stringPreferencesKey("auto_sync")
    val LAST_EXPORT_AT = stringPreferencesKey("last_export_at")
}

/** 设置的唯一读写入口(spec §3.11) */
interface SettingsRepository {
    val settings: Flow<LnxSettings>

    /** 一次性取当前值:接收器/非界面代码在协程里取设置用这个 */
    suspend fun current(): LnxSettings

    suspend fun setThemeSlot(slot: ThemeSlot)
    suspend fun setDarkMode(mode: DarkMode)
    suspend fun setReminderLead(minutes: Int?)
    suspend fun setDnd(enabled: Boolean, startMinute: Int, endMinute: Int)
    suspend fun setWeekStartMonday(monday: Boolean)
    suspend fun setLanguage(language: String)
    suspend fun setOnboardingDone()

    /** 记下"隐私披露页已读过"(v0.3 A1)。两个按钮都调它:读过就够了,不记"同不同意" */
    suspend fun setPrivacyDisclosureDone()

    /**
     * 存同步服务器地址(v0.3 A3)。**存之前归一化**:trim、去尾斜杠;
     * 非空值必须以 https:// 开头(固定证书只对 TLS 有效),不合法的传空串当"未配置"。
     */
    suspend fun setServerBaseUrl(url: String)

    /** 自动同步开关(v0.3 A5) */
    suspend fun setAutoSync(enabled: Boolean)

    /** 记下"上次成功导出备份"的时刻;只在导出**成功落盘**后调,失败不算备份过 */
    suspend fun setLastExportAt(millis: Long)
}
