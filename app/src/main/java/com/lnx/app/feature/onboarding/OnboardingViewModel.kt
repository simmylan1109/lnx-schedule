package com.lnx.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.settings.LnxSettings
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首启三页引导(spec §3.12):欢迎 → 选主题 → 通知权限,只在第一次启动出现。
 *
 * **当前页必须放 ViewModel,不能放 `remember`**:引导第 2 页是选主题,点一下就换主题,
 * 而 MainActivity 用 `Crossfade(themeSlot)` 包着整棵内容树 —— 换主题会让子树重建,
 * `remember` 状态归零,页面会被弹回第 1 页(和 M6 T4 设置页被"踢回日历"是同一个坑)。
 * ViewModel 挂在 Activity 上,活得过这次重建。
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<LnxSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsDefaults.snapshot())

    private val _page = MutableStateFlow(PAGE_WELCOME)
    val page: StateFlow<Int> = _page.asStateFlow()

    /** 「开始」「跳过」都只是往下走一页,不结束引导(spec §3.12 ② 的"可跳过"只跳选主题) */
    fun goTo(page: Int) {
        _page.update { page.coerceIn(PAGE_WELCOME, PAGE_PERMISSION) }
    }

    /** 第 2 页选主题:点一下就写进设置,和设置页同一套即时生效的逻辑 */
    fun pickTheme(slot: ThemeSlot) {
        viewModelScope.launch { settingsRepository.setThemeSlot(slot) }
    }

    /**
     * 走完引导:打上"已完成"标记,之后永不再弹。
     * 任何一步跳过/拒绝都走这里 —— spec §3.12:跳过或拒绝都不影响后续使用。
     */
    fun finish() {
        viewModelScope.launch { settingsRepository.setOnboardingDone() }
    }

    companion object {
        const val PAGE_WELCOME = 0
        const val PAGE_THEME = 1
        const val PAGE_PERMISSION = 2
    }
}
