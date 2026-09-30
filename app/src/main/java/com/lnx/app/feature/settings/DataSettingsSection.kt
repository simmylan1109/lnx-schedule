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

/**
 * 数据组(spec §3.11 ⑤):导出 / 导入(spec §3.13)+ 版本号。
 * M6 时这两行是禁用占位,M7 起可点。
 */
@Composable
internal fun DataSettingsSection(
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionTitle(stringResource(R.string.settings_section_data), "settings_section_data", modifier)

    SettingRow(
        title = stringResource(R.string.settings_export),
        subtitle = stringResource(R.string.settings_export_subtitle),
        onClick = onExport,
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
