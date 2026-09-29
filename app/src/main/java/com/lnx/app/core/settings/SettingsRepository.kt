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
}
