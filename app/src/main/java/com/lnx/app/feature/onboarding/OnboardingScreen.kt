package com.lnx.app.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.R
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.LnxMotion
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.notification.NotificationPermission
import com.lnx.app.core.notification.findActivity
import com.lnx.app.feature.settings.ThemeCardRow

/**
 * 首启引导三页(spec §3.12):① 欢迎 ② 选主题 ③ 通知权限,只在第一次启动出现。
 *
 * 没有"上一步":首次启动是线性引导,任何一步都能往下走或直接收尾;
 * 主题和权限之后都能在设置里改,引导页不设死路。
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val page by viewModel.page.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 权限框要先弹、引导页再收尾:反过来的话弹框就成了"凭空冒出来"
    val allowAndFinish = {
        context.findActivity()?.let(NotificationPermission::request)
        viewModel.finish()
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            fadeIn(tween(LnxMotion.NORMAL_MILLIS)) togetherWith fadeOut(tween(LnxMotion.NORMAL_MILLIS))
        },
        label = "onboarding-page",
        modifier = modifier.fillMaxSize(),
    ) { current ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
                .testTag("onboarding_page_$current"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(120.dp))
            when (current) {
                OnboardingViewModel.PAGE_WELCOME -> WelcomePage()
                OnboardingViewModel.PAGE_THEME -> ThemePage(
                    currentSlot = settings.themeSlot,
                    darkMode = settings.darkMode,
                    onPick = viewModel::pickTheme,
                    // 引导页是用户第一次见到这排卡:自动演示一次"右边还有"
                    autoPeek = true,
                )
                else -> PermissionPage()
            }
            Spacer(modifier = Modifier.weight(1f))
            when (current) {
                OnboardingViewModel.PAGE_WELCOME -> Button(
                    onClick = { viewModel.goTo(OnboardingViewModel.PAGE_THEME) },
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_start"),
                ) { Text(stringResource(R.string.onboarding_start)) }

                OnboardingViewModel.PAGE_THEME -> OutlinedButton(
                    onClick = { viewModel.goTo(OnboardingViewModel.PAGE_PERMISSION) },
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_skip"),
                ) { Text(stringResource(R.string.onboarding_skip)) }

                else -> {
                    Button(
                        onClick = allowAndFinish,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_allow"),
                    ) { Text(stringResource(R.string.onboarding_allow)) }
                    TextButton(
                        onClick = viewModel::finish,
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_later"),
                    ) { Text(stringResource(R.string.onboarding_later)) }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun WelcomePage() {
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("onboarding_welcome"),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.onboarding_tagline),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ThemePage(
    currentSlot: ThemeSlot,
    darkMode: DarkMode,
    onPick: (ThemeSlot) -> Unit,
    autoPeek: Boolean,
) {
    Text(
        text = stringResource(R.string.onboarding_pick_theme),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_pick_theme_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(24.dp))
    ThemeCardRow(current = currentSlot, darkMode = darkMode, onPick = onPick, autoPeek = autoPeek)
}

@Composable
private fun PermissionPage() {
    Text(
        text = stringResource(R.string.onboarding_notification_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag("onboarding_permission"),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_notification_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
