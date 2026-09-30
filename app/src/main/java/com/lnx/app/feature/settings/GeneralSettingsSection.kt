package com.lnx.app.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.lnx.app.R

/**
 * 通用组(spec §3.11 ④):周起始日、语言。
 * 语言切到"跟随系统/中文/English"三选;低于 Android 13 的系统不支持 per-app 语言,
 * 那里要重启才生效,在页面上写明,别让用户以为没生效。
 */
@Composable
internal fun GeneralSettingsSection(
    weekStartMonday: Boolean,
    language: String,
    onSetWeekStart: (Boolean) -> Unit,
    onSetLanguage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionTitle(stringResource(R.string.settings_section_general), "settings_section_general", modifier)

    Text(
        text = stringResource(R.string.settings_week_start),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("settings_week_label"),
    )
    ChipChoice(
        options = listOf(
            stringResource(R.string.settings_week_monday),
            stringResource(R.string.settings_week_sunday),
        ),
        selectedIndex = if (weekStartMonday) 0 else 1,
        onSelect = { onSetWeekStart(it == 0) },
        tagPrefix = "week_",
    )

    Text(
        text = stringResource(R.string.settings_language),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("settings_lang_label"),
    )
    ChipChoice(
        options = listOf(
            // 中文/英文这两个名字**故意不翻译**:用户是在选这两种语言本身
            stringResource(R.string.settings_lang_zh),
            stringResource(R.string.settings_lang_en),
            stringResource(R.string.settings_lang_system),
        ),
        selectedIndex = when (language) {
            "zh" -> 0
            "en" -> 1
            else -> 2
        },
        // 界面重建由 SettingsScreen 统一做:要等"新语言已写进进程缓存"之后再重建,
        // 否则重建出来的还是旧语言
        onSelect = { onSetLanguage(listOf("zh", "en", "system")[it]) },
        tagPrefix = "lang_",
    )
}
