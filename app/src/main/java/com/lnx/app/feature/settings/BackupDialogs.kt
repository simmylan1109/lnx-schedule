package com.lnx.app.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lnx.app.R

/**
 * 导入三连对话(spec §3.13):摘要 → (覆盖时)二次警告 → 出错提示。
 * 三个都是 ConfirmDialog,没有"取消后数据已经动了"这种中间态 —— 真落库只发生在
 * [BackupViewModel.apply] 里,而它只由摘要页的"合并"和警告页的"确定"触发。
 */
@Composable
internal fun BackupSummaryDialog(
    pending: PendingImport,
    onMerge: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("backup_summary_dialog"),
        title = { Text(stringResource(R.string.backup_summary_title)) },
        text = {
            // 事件数与标签数各自成一个词,单复数按各自的数量选(英文里 "1 events" 是错的)
            val events = pluralStringResource(
                R.plurals.backup_count_event,
                pending.summary.eventCount,
                pending.summary.eventCount,
            )
            val tags = pluralStringResource(
                R.plurals.backup_count_tag,
                pending.summary.tagCount,
                pending.summary.tagCount,
            )
            Text(
                text = stringResource(R.string.backup_summary, events, tags),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("backup_summary_text"),
            )
        },
        confirmButton = {
            TextButton(onClick = onMerge, modifier = Modifier.testTag("backup_merge")) {
                Text(stringResource(R.string.backup_merge))
            }
        },
        dismissButton = {
            TextButton(onClick = onOverwrite, modifier = Modifier.testTag("backup_overwrite")) {
                Text(stringResource(R.string.backup_overwrite))
            }
        },
    )
}

@Composable
internal fun OverwriteWarningDialog(
    warning: OverwriteWarning,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("backup_overwrite_dialog"),
        title = { Text(stringResource(R.string.backup_overwrite_title)) },
        text = {
            Text(
                text = pluralStringResource(
                    R.plurals.backup_overwrite_warning,
                    warning.currentCount,
                    warning.currentCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("backup_overwrite_text"),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("backup_overwrite_confirm")) {
                Text(stringResource(R.string.backup_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("backup_overwrite_cancel")) {
                Text(stringResource(R.string.backup_cancel))
            }
        },
    )
}

@Composable
internal fun BackupErrorDialog(
    error: BackupError,
    onDismiss: () -> Unit,
) {
    val text = when (error) {
        BackupError.NOT_LNX_BACKUP -> stringResource(R.string.backup_error_not_lnx)
        BackupError.UNSUPPORTED_VERSION -> stringResource(R.string.backup_error_version)
        BackupError.CORRUPTED -> stringResource(R.string.backup_error_corrupted)
        BackupError.FILE_IO -> stringResource(R.string.backup_error_io)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("backup_error_dialog"),
        title = { Text(stringResource(R.string.backup_error_title)) },
        text = { Text(text, modifier = Modifier.testTag("backup_error_text")) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("backup_error_ok")) {
                Text(stringResource(R.string.backup_confirm))
            }
        },
    )
}
