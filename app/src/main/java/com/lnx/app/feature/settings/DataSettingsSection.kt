package com.lnx.app.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lnx.app.BuildConfig
import com.lnx.app.R
import com.lnx.app.core.common.LocalLnxLocale
import com.lnx.app.core.common.formatBackupTime

/**
 * 数据组(spec §3.11 ⑤):导出 / 导入(spec §3.13)+ 版本号。
 *
 * v0.2 补欠账 ②:导出曾经只是列表里两行朴素文字之一,和"导入"长得一模一样 ——
 * 但备份是**唯一**的换机逃生通道,它应该长成"该点它"的样子。所以导出行铺了主题色底,
 * 副标题显示**上次备份时间**:从来没备份过的显示"从未备份过",让"该备个份了"这件事
 * 自己站出来说话,而不是靠用户记得。
 */
@Composable
internal fun DataSettingsSection(
    lastExportAt: Long,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLnxLocale.current
    val backupAge = if (lastExportAt > 0L) {
        stringResource(R.string.settings_backup_last_at, formatBackupTime(lastExportAt, locale))
    } else {
        stringResource(R.string.settings_backup_never)
    }

    SectionTitle(stringResource(R.string.settings_section_data), "settings_section_data", modifier)

    SettingRow(
        title = stringResource(R.string.settings_export),
        subtitle = backupAge,
        onClick = onExport,
        highlight = true,
        modifier = Modifier.testTag("data_export"),
    )
    SettingRow(
        title = stringResource(R.string.settings_import),
        subtitle = stringResource(R.string.settings_import_subtitle),
        onClick = onImport,
        modifier = Modifier.testTag("data_import"),
    )
    Text(
        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("data_version"),
    )
}
