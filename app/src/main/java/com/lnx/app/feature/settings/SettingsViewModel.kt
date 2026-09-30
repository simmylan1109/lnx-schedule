package com.lnx.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.common.LocaleContext
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.notification.ReminderPlanner
import com.lnx.app.core.settings.LnxSettings
import com.lnx.app.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页状态(spec §3.11):四组设置直接读写 [SettingsRepository]。
 *
 * 改**提醒相关**的两项(默认提前量、免打扰)后要顺带重排闹钟 —— 这两项一变,
 * 已经排进去的提醒时刻就不再对了(spec §3.8)。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val reminderPlanner: ReminderPlanner,
) : ViewModel() {

    val settings: StateFlow<LnxSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.lnx.app.core.settings.SettingsDefaults.snapshot())

    fun setThemeSlot(slot: ThemeSlot) = viewModelScope.launch { repository.setThemeSlot(slot) }

    fun setDarkMode(mode: DarkMode) = viewModelScope.launch { repository.setDarkMode(mode) }

    fun setReminderLead(minutes: Int?) = viewModelScope.launch {
        repository.setReminderLead(minutes)
        reminderPlanner.reschedule()
    }

    fun setDnd(enabled: Boolean, startMinute: Int, endMinute: Int) = viewModelScope.launch {
        repository.setDnd(enabled, startMinute, endMinute)
        reminderPlanner.reschedule()
    }

    fun setWeekStartMonday(monday: Boolean) = viewModelScope.launch { repository.setWeekStartMonday(monday) }

    /**
     * 换语言(spec §3.11 ②)。**先写进程缓存、再落盘**:
     * 缓存是 `attachBaseContext` 唯一能同步读到的地方(见 [LocaleContext]),
     * 而设置流会在落盘后吐新值、触发界面重建 —— 那时缓存必须已经是新语言,
     * 否则重建出来的还是旧语言(这个顺序踩过一次)。
     */
    fun setLanguage(language: String) = viewModelScope.launch {
        LocaleContext.setLanguage(language)
        repository.setLanguage(language)
    }
}
