package com.lnx.app.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.R
import com.lnx.app.core.backup.ImportMode
import com.lnx.app.core.common.LocaleContext
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.notification.findActivity

/**
 * 设置页(spec §3.11):四组 —— 外观(4 主题卡 + 深浅色)、提醒(默认提前/免打扰/权限)、
 * 通用(周起始/语言)、数据(导出/导入 + 版本号)。
 *
 * 与编辑页同构的**全屏 overlay**(无导航图,见 M6 T4 裁定):点一下即生效、自动记住,
 * 没有保存按钮。返回键/左上角箭头关掉回到日历。
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 换语言后重建一次界面:语言只在 attachBaseContext 里生效,那是同步方法,只能靠重建。
    // 判据是"当前生效的语言"和"设置里的语言"是否一致 —— 一致就不重建,避免自我循环;
    // 且设置流吐新值时进程缓存已经写好了(见 SettingsViewModel.setLanguage 的顺序)。
    val activity = context.findActivity()
    LaunchedEffect(settings.language) {
        if (LocaleContext.appliedLanguage != settings.language) activity?.recreate()
    }

    // —— 导入/导出(spec §3.13)——
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    // OpenDocument 走系统文件选择器。传 "*/*" 而不是 application/json:
    // 云盘里不少 .json 的 mime 标成 octet-stream 或空,按 json 过滤反而选不到。
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(backupViewModel::onFilePicked)
    }

    val exportedTemplate = stringResource(R.string.backup_exported)
    val mergedTemplate = stringResource(R.string.backup_imported_merge)
    LaunchedEffect(backupState.message) {
        val message = backupState.message ?: return@LaunchedEffect
        val text = when (message) {
            is BackupMessage.Exported -> exportedTemplate.format(message.fileName)
            is BackupMessage.Imported -> if (message.mode == ImportMode.MERGE) {
                mergedTemplate.format(message.added, message.skipped, message.replaced)
            } else {
                context.resources.getQuantityString(
                    R.plurals.backup_imported_overwrite,
                    message.added,
                    message.added,
                )
            }
        }
        snackbar.showSnackbar(text)
        backupViewModel.consumeMessage()
    }

    // **根节点必须自带不透明底色**:设置页是盖在日历之上的全屏 overlay(M6 T4 裁定),
    // 只填尺寸不画底色的话,下面那层日历会整片透出来 —— 顶栏标题和 "Day/Week/Month"
    // 叠在一起,滑到底连当前时刻的红线都看得见。
    // 这个洞从 M6 就带着,当时的验收截图 `05-settings-en.png` 里能直接看到,却没人看出来。
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(onClose = onClose)

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

                DataSettingsSection(
                    lastExportAt = settings.lastExportAt,
                    onExport = {
                        backupViewModel.export { uri, name ->
                            shareLauncher.launch(shareIntent(context, uri, name))
                        }
                    },
                    onImport = { pickLauncher.launch(arrayOf("*/*")) },
                )
            }
        }

        // 对话框叠在设置页之上。顺序即层级:出错 > 覆盖二次警告 > 摘要 ——
        // 覆盖确认是从摘要里点进来的,取消它要退回摘要而不是把内容丢掉。
        backupState.error?.let { error ->
            BackupErrorDialog(error = error, onDismiss = backupViewModel::consumeError)
        }
        backupState.overwriteWarning?.let { warning ->
            OverwriteWarningDialog(
                warning = warning,
                onConfirm = backupViewModel::confirmOverwrite,
                onCancel = backupViewModel::cancelOverwrite,
            )
        }
        backupState.pending?.let { pending ->
            BackupSummaryDialog(
                pending = pending,
                onMerge = backupViewModel::chooseMerge,
                onOverwrite = backupViewModel::chooseOverwrite,
                onDismiss = backupViewModel::dismissPending,
            )
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.testTag("settings_snackbar"))
    }
}

@Composable
private fun SettingsTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("settings_back")) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
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
}

/** 导出后弹系统分享面板(spec §3.13)。URI 授权由 FileProvider 授予,分享完即失效 */
private fun shareIntent(context: Context, uri: Uri, fileName: String): Intent {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(share, context.getString(R.string.backup_share_title))
}
