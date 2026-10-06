package com.lnx.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lnx.app.R
import com.lnx.app.core.common.LocalLnxLocale
import com.lnx.app.core.common.formatBackupTime

/**
 * 共享组(v0.3 A5):登录状态、自动同步开关、立即同步(带"上次同步时间/失败原因")、登出。
 *
 * 「上次同步」复用备份那套 [formatBackupTime]:两处时间的说法必须一致,
 * 同一个 App 里"上次备份 10-06 21:30"和"上次同步 Oct 6"会让人怀疑不是一台机器。
 */
@Composable
internal fun ShareSettingsSection(
    account: ShareViewModel.AccountUi,
    autoSync: Boolean,
    syncing: Boolean,
    lastSyncAt: Long,
    lastError: String?,
    serverUrl: String,
    onRefreshAccount: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLnxLocale.current

    SectionTitle(stringResource(R.string.settings_section_share), "settings_section_share", modifier)

    // —— 登录状态(点它=重新确认;断网时它会说"离线未确认"而不是谎称掉线) ——
    SettingRow(
        title = when (account) {
            ShareViewModel.AccountUi.Checking -> stringResource(R.string.share_status_checking)
            ShareViewModel.AccountUi.SignedOut -> stringResource(R.string.share_status_signed_out)
            is ShareViewModel.AccountUi.SignedIn ->
                stringResource(R.string.share_status_signed_in, account.username)

            ShareViewModel.AccountUi.SignedInUnconfirmed -> stringResource(R.string.share_status_unknown)
        },
        subtitle = if (serverUrl.isEmpty()) {
            stringResource(R.string.share_server_unset)
        } else {
            stringResource(R.string.share_server_addr, serverUrl)
        },
        onClick = onRefreshAccount,
        modifier = Modifier.testTag("share_account"),
    )

    // —— 自动同步:行本身**不挂 onClick**(和免打扰开关同一套路) ——
    // 否则点开关会连触"行点击"和"开关点击"两次,切了等于没切
    SettingRow(
        title = stringResource(R.string.share_auto_sync),
        subtitle = stringResource(R.string.share_auto_sync_subtitle),
        trailing = {
            Switch(
                checked = autoSync,
                onCheckedChange = onToggleAutoSync,
                modifier = Modifier.testTag("share_auto_sync_switch"),
            )
        },
        modifier = Modifier.testTag("share_auto_sync"),
    )

    // —— 立即同步:失败原因直接摆在副标题上,不藏通知栏 ——
    val syncSubtitle = when {
        syncing -> stringResource(R.string.share_syncing)
        lastError != null -> stringResource(R.string.share_sync_failed)
        lastSyncAt > 0L -> stringResource(R.string.share_sync_last_at, formatBackupTime(lastSyncAt, locale))
        else -> stringResource(R.string.share_sync_never)
    }
    SettingRow(
        title = stringResource(R.string.share_sync_now),
        subtitle = syncSubtitle,
        onClick = { if (!syncing) onSyncNow() },
        modifier = Modifier.testTag("share_sync_now"),
    )

    // —— 登出:只在"确实登着"时才出现,避免用户以为自己没登录还点了个登出 ——
    // 红字 + 可点,破坏性操作的长相(spec §5.3 的矢量图标约束不涉及这类纯文字动作)
    if (account is ShareViewModel.AccountUi.SignedIn ||
        account is ShareViewModel.AccountUi.SignedInUnconfirmed
    ) {
        Text(
            text = stringResource(R.string.share_sign_out),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clickable(onClick = onSignOut)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("share_sign_out"),
        )
    }
}