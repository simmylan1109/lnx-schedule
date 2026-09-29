package com.lnx.app.core.settings

import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot

/**
 * 设置的**出厂值**(spec §3.11/§3.12)。
 *
 * 全局约束"默认值不得双源":提醒的默认提前量以前是 `EventDefaults` 里的编译期常量、
 * 免打扰窗口以前是 `DndSettings`,现在两者都在运行时读设置,这里只剩"读不到时"的兜底
 * (`EventDefaults.draft` 的默认参数、接收器读设置失败时的回落都用它)。
 */
object SettingsDefaults {
    val THEME_SLOT = ThemeSlot.MATERIAL_YOU
    val DARK_MODE = DarkMode.FOLLOW_SYSTEM

    /** 默认提前提醒分钟数;null = 新建事件默认不提醒(spec §3.11 默认 15) */
    val REMINDER_LEAD_MINUTES: Int? = 15

    const val DND_ENABLED = false
    const val DND_START_MINUTE = 22 * 60
    const val DND_END_MINUTE = 8 * 60

    const val WEEK_START_MONDAY = true

    /** "system" | "zh" | "en" */
    const val LANGUAGE = "system"

    const val ONBOARDING_DONE = false

    /** 提醒可选项(spec §3.11):不提醒 / 5 / 15 / 30 / 60 */
    val REMINDER_CHOICES: List<Int?> = listOf(null, 5, 15, 30, 60)

    /** 全部出厂值组成的一份快照:DataStore 首帧还没吐出来时先用它渲染,避免主题闪一下 */
    fun snapshot(): LnxSettings = LnxSettings(
        themeSlot = THEME_SLOT,
        darkMode = DARK_MODE,
        reminderLeadMinutes = REMINDER_LEAD_MINUTES,
        dndEnabled = DND_ENABLED,
        dndStartMinute = DND_START_MINUTE,
        dndEndMinute = DND_END_MINUTE,
        weekStartMonday = WEEK_START_MONDAY,
        language = LANGUAGE,
        onboardingDone = ONBOARDING_DONE,
    )
}
