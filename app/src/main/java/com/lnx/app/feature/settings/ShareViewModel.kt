package com.lnx.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.network.LnxApiClient
import com.lnx.app.core.network.LnxApiException
import com.lnx.app.core.network.TokenStorage
import com.lnx.app.core.settings.LnxSettings
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.SettingsRepository
import com.lnx.app.core.sync.SyncEngine
import com.lnx.app.core.sync.SyncStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页「共享」段(v0.3 A5):登录状态、自动同步开关、立即同步、登出。
 *
 * 登录状态怎么来:**拿令牌去问一次 `me`** —— 令牌是否存在只说明"本机存着一把钥匙",
 * 销没销、过没过期只有服务端知道。401 = 令牌已废 → 本地清掉转未登录;
 * 网络类错误 = 问不出来 → 保留"已登录"但标注离线,不因为断网就吓唬用户"你被登出了"。
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val tokenStorage: TokenStorage,
    private val api: LnxApiClient,
    private val syncEngine: SyncEngine,
    private val syncStateStore: SyncStateStore,
) : ViewModel() {

    /** 登录状态的四种形态(界面直接映射成四句文案) */
    sealed interface AccountUi {
        data object Checking : AccountUi
        data object SignedOut : AccountUi
        data class SignedIn(val username: String) : AccountUi
        /** 本地有令牌,但这次没问到(断网/服务端 5xx)——照常显示"已登录",不显示用户名 */
        data object SignedInUnconfirmed : AccountUi
    }

    val settings: StateFlow<LnxSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsDefaults.snapshot())

    val syncState: StateFlow<SyncStateStore.SyncState> = syncStateStore.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncStateStore.SyncState())

    private val _account = MutableStateFlow<AccountUi>(AccountUi.Checking)
    val account: StateFlow<AccountUi> = _account.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    init {
        refreshAccount()
    }

    /** 进设置页问一次;点状态行也能重问(比如刚在别处登出) */
    fun refreshAccount() {
        viewModelScope.launch {
            val url = settingsRepository.current().serverBaseUrl
            val token = tokenStorage.read()
            if (url.isEmpty() || token == null) {
                _account.value = AccountUi.SignedOut
                return@launch
            }
            _account.value = AccountUi.Checking
            _account.value = try {
                AccountUi.SignedIn(api.me(url, token).user.username)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LnxApiException) {
                if (e.code == "unauthorized") {
                    // 401:这把钥匙在服务端已经作废,本地留着也没用,清掉换回未登录
                    tokenStorage.clear()
                    AccountUi.SignedOut
                } else {
                    AccountUi.SignedInUnconfirmed
                }
            }
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSync(enabled) }
    }

    /** 手动同步:引擎自己管超时/互斥/落账;这里只转 UI 状态 */
    fun syncNow() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                val url = settingsRepository.current().serverBaseUrl
                val token = tokenStorage.read()
                if (url.isNotEmpty() && token != null) {
                    syncEngine.syncNow(url, token)
                    // 失败的账本(engine 写的 lastError/lastSyncAt)从 syncState 流里读,
                    // 这里不重复记
                    refreshAccount() // 顺手校准登录态(同步可能撞上已废的令牌)
                }
            } finally {
                _syncing.value = false
            }
        }
    }

    /**
     * 退出登录:**服务端吊销尽力而为,本地必清**。
     * 断网时照样要能登出 —— 用户的意图是"这台设备离开账号",网络失败不该拦着他。
     * 服务器上的令牌等 TTL 过期自然作废(90 天),或下次他处登录换新。
     */
    fun signOut() {
        viewModelScope.launch {
            val url = settingsRepository.current().serverBaseUrl
            val token = tokenStorage.read()
            if (url.isNotEmpty() && token != null) {
                runCatching { api.logout(url, token) }
            }
            tokenStorage.clear()
            _account.value = AccountUi.SignedOut
        }
    }
}