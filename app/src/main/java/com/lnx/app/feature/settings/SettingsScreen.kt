package com.lnx.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.R
import com.lnx.app.core.designsystem.DarkMode

/**
 * 设置页(spec §3.11):四组 —— 外观(4 主题卡 + 深浅色)、提醒(默认提前/免打扰/权限)、
 * 通用(周起始/语言)、数据(导出导入占位 + 版本号)。
 *
 * 与编辑页同构的**全屏 overlay**(无导航图,见 M6 T4 裁定):点一下即生效、自动记住,
 * 没有保存按钮。返回键/左上角箭头关掉回到日历。
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("settings_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 外观:4 张主题卡(点一下即换)+ 深浅色三选
            ThemePickerSection(
                current = settings.themeSlot,
                darkMode = settings.darkMode,
                onPick = viewModel::setThemeSlot,
            )
            Text(
                text = stringResource(R.string.settings_appearance_mode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .testTag("settings_mode_label"),
            )
            ChipChoice(
                options = listOf(
                    stringResource(R.string.settings_mode_follow),
                    stringResource(R.string.settings_mode_light),
                    stringResource(R.string.settings_mode_dark),
                ),
                selectedIndex = when (settings.darkMode) {
                    DarkMode.FOLLOW_SYSTEM -> 0
                    DarkMode.LIGHT -> 1
                    DarkMode.DARK -> 2
                },
                onSelect = { index ->
                    viewModel.setDarkMode(
                        when (index) {
                            0 -> DarkMode.FOLLOW_SYSTEM
                            1 -> DarkMode.LIGHT
                            else -> DarkMode.DARK
                        },
                    )
                },
                tagPrefix = "mode_",
            )

            ReminderSettingsSection(
                defaultLead = settings.reminderLeadMinutes,
                dndEnabled = settings.dndEnabled,
                dndStartMinute = settings.dndStartMinute,
                dndEndMinute = settings.dndEndMinute,
                onSetLead = viewModel::setReminderLead,
                onSetDnd = viewModel::setDnd,
            )

            GeneralSettingsSection(
                weekStartMonday = settings.weekStartMonday,
                language = settings.language,
                onSetWeekStart = viewModel::setWeekStartMonday,
                onSetLanguage = viewModel::setLanguage,
            )

            DataSettingsSection()
        }
    }
}
