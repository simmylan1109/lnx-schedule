package com.lnx.app.feature.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lnx.app.R
import com.lnx.app.core.notification.NotificationPermission
import com.lnx.app.core.settings.SettingsDefaults

/**
 * 提醒组(spec §3.11 ②③):默认提前量、免打扰开关与时段、通知权限状态与「去开启」。
 * 这两项改动会即时重排闹钟(见 [SettingsViewModel]),否则已经排进去的提醒时刻就不再对。
 */
@Composable
internal fun ReminderSettingsSection(
    defaultLead: Int?,
    dndEnabled: Boolean,
    dndStartMinute: Int,
    dndEndMinute: Int,
    onSetLead: (Int?) -> Unit,
    onSetDnd: (Boolean, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.settings_section_reminder), "settings_section_reminder", modifier)

    Text(
        text = stringResource(R.string.settings_default_lead),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).testTag("settings_lead_label"),
    )
    ChipChoice(
        options = listOf(
            stringResource(R.string.settings_lead_none),
            stringResource(R.string.settings_lead_5),
            stringResource(R.string.settings_lead_15),
            stringResource(R.string.settings_lead_30),
            stringResource(R.string.settings_lead_60),
        ),
        selectedIndex = SettingsDefaults.REMINDER_CHOICES.indexOf(defaultLead).coerceAtLeast(0),
        onSelect = { onSetLead(SettingsDefaults.REMINDER_CHOICES[it]) },
        tagPrefix = "lead_",
    )

    SettingRow(
        title = stringResource(R.string.settings_dnd),
        modifier = Modifier.testTag("dnd_row"),
        trailing = {
            Switch(
                checked = dndEnabled,
                onCheckedChange = { onSetDnd(it, dndStartMinute, dndEndMinute) },
                modifier = Modifier.testTag("dnd_switch"),
            )
        },
    )

    // 时段两行:起、止各开一个时间选择器(spec §3.11 ②)
    SettingRow(
        title = stringResource(R.string.settings_dnd_window) + " · 开始",
        modifier = Modifier.testTag("dnd_start_row"),
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onSetDnd(dndEnabled, hour * 60 + minute, dndEndMinute) },
                dndStartMinute / 60, dndStartMinute % 60, true,
            ).show()
        },
        trailing = { Text(formatMinute(dndStartMinute), style = MaterialTheme.typography.bodyLarge) },
    )
    SettingRow(
        title = stringResource(R.string.settings_dnd_window) + " · 结束",
        modifier = Modifier.testTag("dnd_end_row"),
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onSetDnd(dndEnabled, dndStartMinute, hour * 60 + minute) },
                dndEndMinute / 60, dndEndMinute % 60, true,
            ).show()
        },
        trailing = { Text(formatMinute(dndEndMinute), style = MaterialTheme.typography.bodyLarge) },
    )

    // 通知权限状态 + 去开启(spec §3.11 ③):被拒时提醒静默失效,必须给用户一条路
    val granted = NotificationPermission.isGranted(context)
    SettingRow(
        title = stringResource(R.string.settings_notification_permission),
        subtitle = stringResource(
            if (granted) R.string.settings_permission_granted else R.string.settings_permission_denied,
        ),
        modifier = Modifier.testTag("permission_row"),
        trailing = {
            if (!granted) {
                TextButton(
                    onClick = { context.openAppNotificationSettings() },
                    modifier = Modifier.testTag("permission_open"),
                ) { Text(stringResource(R.string.settings_open_system_settings)) }
            }
        },
    )
}

private fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

/** 跳系统应用详情页的通知权限项;拿不到应用详情页就退回应用详情页 */
private fun android.content.Context.openAppNotificationSettings() {
    val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Settings.ACTION_APP_NOTIFICATION_SETTINGS
    } else {
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    }
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(target).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(target, Uri.fromParts("package", packageName, null))
    }
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
}
