package com.lnx.app.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.settings.LnxSettings
import com.lnx.app.core.settings.SettingKeys
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore 实现的设置仓库。
 *
 * 读侧一律"解析失败就用出厂值":设置是唯一偏好源,但它同时也是**最容易被外部写坏**的地方
 * (旧版本残留、手改文件、导入的备份)。任何一项解析不出来,都只让那一项回落到出厂值,
 * 不允许整个设置流抛异常把 App 启动带崩。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<LnxSettings> = dataStore.data.map(::toSettings)

    override suspend fun current(): LnxSettings = settings.first()

    override suspend fun setThemeSlot(slot: ThemeSlot) {
        dataStore.edit { it[SettingKeys.THEME_SLOT] = slot.name }
    }

    override suspend fun setDarkMode(mode: DarkMode) {
        dataStore.edit { it[SettingKeys.DARK_MODE] = mode.name }
    }

    /** null = 不提醒,存 -1(不能用 0:0 会被算成"提前 0 分钟",到点立即触发) */
    override suspend fun setReminderLead(minutes: Int?) {
        dataStore.edit { it[SettingKeys.REMINDER_LEAD] = (minutes ?: NO_REMINDER).toString() }
    }

    override suspend fun setDnd(enabled: Boolean, startMinute: Int, endMinute: Int) {
        dataStore.edit {
            it[SettingKeys.DND_ENABLED] = enabled.toString()
            it[SettingKeys.DND_START] = startMinute.toString()
            it[SettingKeys.DND_END] = endMinute.toString()
        }
    }

    override suspend fun setWeekStartMonday(monday: Boolean) {
        dataStore.edit { it[SettingKeys.WEEK_START_MONDAY] = monday.toString() }
    }

    override suspend fun setLanguage(language: String) {
        dataStore.edit { it[SettingKeys.LANGUAGE] = language }
    }

    override suspend fun setOnboardingDone() {
        dataStore.edit { it[SettingKeys.ONBOARDING_DONE] = true.toString() }
    }

    override suspend fun setLastExportAt(millis: Long) {
        dataStore.edit { it[SettingKeys.LAST_EXPORT_AT] = millis.toString() }
    }

    private fun toSettings(prefs: Preferences): LnxSettings = LnxSettings(
        themeSlot = prefs[SettingKeys.THEME_SLOT]
            ?.let { name -> ThemeSlot.entries.firstOrNull { it.name == name } }
            ?: SettingsDefaults.THEME_SLOT,
        darkMode = prefs[SettingKeys.DARK_MODE]
            ?.let { name -> DarkMode.entries.firstOrNull { it.name == name } }
            ?: SettingsDefaults.DARK_MODE,
        // 注意:这里不能写成 `?.let { ... } ?: 默认值` —— 哨兵值解析出的 null("不提醒")
        // 会被 elvis 当成"没值"吃掉,于是"不提醒"又变回默认 15
        reminderLeadMinutes = when (val raw = prefs[SettingKeys.REMINDER_LEAD]?.toIntOrNull()) {
            null -> SettingsDefaults.REMINDER_LEAD_MINUTES // 没写过 / 脏数据
            NO_REMINDER -> null // 显式选了"不提醒"
            in VALID_LEADS -> raw
            else -> SettingsDefaults.REMINDER_LEAD_MINUTES // 越界值(如负数)
        },
        dndEnabled = prefs[SettingKeys.DND_ENABLED]?.toBooleanStrictOrNull() ?: SettingsDefaults.DND_ENABLED,
        dndStartMinute = prefs[SettingKeys.DND_START]?.toIntOrNull()?.takeIf { it in 0..MINUTES_PER_DAY }
            ?: SettingsDefaults.DND_START_MINUTE,
        dndEndMinute = prefs[SettingKeys.DND_END]?.toIntOrNull()?.takeIf { it in 0..MINUTES_PER_DAY }
            ?: SettingsDefaults.DND_END_MINUTE,
        weekStartMonday = prefs[SettingKeys.WEEK_START_MONDAY]?.toBooleanStrictOrNull()
            ?: SettingsDefaults.WEEK_START_MONDAY,
        language = prefs[SettingKeys.LANGUAGE]?.takeIf { it in VALID_LANGUAGES } ?: SettingsDefaults.LANGUAGE,
        onboardingDone = prefs[SettingKeys.ONBOARDING_DONE]?.toBooleanStrictOrNull()
            ?: SettingsDefaults.ONBOARDING_DONE,
        // 非法值(负数/非数字)一律当"从未备份",别让一条脏数据卡住整个设置流
        lastExportAt = prefs[SettingKeys.LAST_EXPORT_AT]?.toLongOrNull()
            ?.takeIf { it > 0 } ?: SettingsDefaults.LAST_EXPORT_AT,
    )

    private companion object {
        /** "不提醒"的哨兵值 */
        const val NO_REMINDER = -1
        const val MINUTES_PER_DAY = 24 * 60
        val VALID_LEADS = setOf(5, 15, 30, 60)
        val VALID_LANGUAGES = setOf("system", "zh", "en")
    }
}
