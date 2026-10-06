package com.lnx.app.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.R

/**
 * 登录/注册全屏页(v0.3 A3),从设置页「账号」段进入 —— 和设置页同构的 overlay,
 * 根节点必须自带不透明底色(见 SettingsScreen 里那个透了一整个里程碑的洞)。
 *
 * 服务器地址输入框在这里而不是设置页:它是登录的前提,放同一个流程里最顺;
 * 两个字段的错误都显示在提交按钮上方。
 *
 * 错误文案的规则:**code → 本地资源**映射,服务端的 message 只做兜底
 * (服务端消息可能随版本改写,且当前只有中文,英文界面要靠映射)。
 */
@Composable
fun AuthScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val savedBaseUrl by viewModel.savedBaseUrl.collectAsStateWithLifecycle()
    val submitting by viewModel.submitting.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()

    var mode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    // 密码和邀请码**不能**进 saved instance state:rememberSaveable 会落进 Bundle,
    // 进程被杀时凭据就写到磁盘上了 —— 丢状态(主题重建)让用户重输,是可接受的代价
    var password by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }

    // 预填已存的服务器地址;只在本地还是空的时候填,不打断用户正在输入的内容
    LaunchedEffect(savedBaseUrl) {
        if (server.isBlank() && savedBaseUrl.isNotBlank()) server = savedBaseUrl
    }

    // 成功 = 令牌已存 + 地址已存,收尾回设置页。
    // **Failure 绝不能在这里消费**:消费掉 outcome 等于把错误文本一帧就拆了,
    // 用户根本看不见哪里错了 —— 错误要一直挂着,直到下一次 submit 或离开本页。
    LaunchedEffect(outcome) {
        if (outcome is AuthOutcome.Success) {
            viewModel.consumeOutcome()
            onClose()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("auth_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("auth_back")) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.auth_back),
                    )
                }
                Text(
                    text = stringResource(R.string.auth_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                LabeledField(
                    label = stringResource(R.string.auth_server_address),
                    value = server,
                    onValueChange = { server = it },
                    hint = stringResource(R.string.auth_server_address_hint),
                    tag = "auth_server",
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledField(
                    label = stringResource(R.string.auth_username),
                    value = username,
                    onValueChange = { username = it },
                    tag = "auth_username",
                )
                Spacer(modifier = Modifier.height(12.dp))
                LabeledField(
                    label = stringResource(R.string.auth_password),
                    value = password,
                    onValueChange = { password = it },
                    tag = "auth_password",
                    hideInput = true,
                )
                if (mode == AuthMode.REGISTER) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LabeledField(
                        label = stringResource(R.string.auth_invite_code),
                        value = invite,
                        onValueChange = { invite = it },
                        hint = stringResource(R.string.auth_invite_hint),
                        tag = "auth_invite",
                    )
                }

                val failure = outcome as? AuthOutcome.Failure
                if (failure != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = failure.toLocalizedText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        // liveRegion:读屏用户不用挪焦点也能听到错误播报
                        modifier = Modifier
                            .testTag("auth_error")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        viewModel.submit(mode, server, username, password, invite)
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().testTag("auth_submit"),
                ) {
                    Text(
                        text = if (mode == AuthMode.LOGIN) {
                            stringResource(R.string.auth_submit_login)
                        } else {
                            stringResource(R.string.auth_submit_register)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        mode = if (mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
                    },
                    modifier = Modifier.fillMaxWidth().testTag("auth_mode_switch"),
                ) {
                    Text(
                        text = if (mode == AuthMode.LOGIN) {
                            stringResource(R.string.auth_switch_to_register)
                        } else {
                            stringResource(R.string.auth_switch_to_login)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    hint: String? = null,
    hideInput: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = if (hint != null) {
            { Text(hint) }
        } else {
            null
        },
        singleLine = true,
        visualTransformation = if (hideInput) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** code → 当前语言文案;认不出的 code 退回服务端原话(至少不是空白) */
@Composable
private fun AuthOutcome.Failure.toLocalizedText(): String = when (code) {
    "bad_url" -> stringResource(R.string.auth_error_bad_url)
    "bad_username" -> stringResource(R.string.auth_error_username)
    "weak_password" -> stringResource(R.string.auth_error_password)
    "invite_required" -> stringResource(R.string.auth_error_invite)
    "username_taken" -> stringResource(R.string.auth_error_taken)
    "locked_out" -> stringResource(R.string.auth_error_locked)
    "unauthorized" -> stringResource(R.string.auth_error_unauthorized)
    "registration_closed" -> stringResource(R.string.auth_error_closed)
    "unreachable" -> stringResource(R.string.auth_error_unreachable)
    else -> fallbackMessage
}