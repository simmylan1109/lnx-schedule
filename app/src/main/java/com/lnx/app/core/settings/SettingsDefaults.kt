package com.lnx.app.core.settings

import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot

/**
 * 设置的**出厂值**(spec §3.11/§3.12)。
 *
 * 全局约束"默认值不得双源":以前提醒默认 15 分钟住在 `EventDefaults`、免打扰窗口住在
 * `DndSettings`,现在统一收口到这里,那两个类只保留"读不到设置时"的兜底。
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
}
