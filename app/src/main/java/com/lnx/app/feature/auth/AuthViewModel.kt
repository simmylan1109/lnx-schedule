package com.lnx.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lnx.app.core.network.LnxApiClient
import com.lnx.app.core.network.LnxApiException
import com.lnx.app.core.network.TokenStorage
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 登录还是注册 */
enum class AuthMode { LOGIN, REGISTER }

/** 一次提交的结局。Success 由界面收尾;Failure 的 code 交给界面翻成当前语言的文案 */
sealed interface AuthOutcome {
    data object Success : AuthOutcome
    data class Failure(val code: String, val fallbackMessage: String) : AuthOutcome
}

/**
 * 登录/注册(A3)。字段值留在界面里,这里只管**提交后的流水线**:
 * 本地校验(镜像服务端规则,省一次注定失败的网络往返)→ 调 API → 存令牌 → 存服务器地址。
 *
 * 客户端本地校验的规则是服务端 `AuthRoutes.validateUsername` / `Passwords.validate`
 * 的**镜像**,两处必须一起改;服务端的消息永远是权威的,本地拦只是为了省往返。
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: LnxApiClient,
    private val tokenStorage: TokenStorage,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 设置里已存的服务器地址,输入框的预填值(没配过就是空串) */
    val savedBaseUrl: StateFlow<String> = settingsRepository.settings
        .map { it.serverBaseUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsDefaults.SERVER_BASE_URL)

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _outcome = MutableStateFlow<AuthOutcome?>(null)
    val outcome: StateFlow<AuthOutcome?> = _outcome.asStateFlow()

    fun consumeOutcome() {
        _outcome.value = null
    }

    fun submit(mode: AuthMode, rawBaseUrl: String, username: String, password: String, inviteCode: String) {
        if (_submitting.value) return
        viewModelScope.launch {
            _submitting.value = true
            // 上一次的错误在这里清掉:错误文本属于"上一次尝试",不该陪跑到这一次
            _outcome.value = null
            try {
                val baseUrl = rawBaseUrl.trim().trimEnd('/')
                if (!baseUrl.startsWith("https://")) {
                    fail(LocalError.BAD_URL); return@launch
                }
                // 服务端会 trim() 用户名;口令走 NFKC + trim,但**大小写有意义**,本地别动它。
                // 长度校验也对着 NFKC 之后的串做,否则含全角/连字的口令本地放行、服务端拒收
                val name = username.trim()
                if (name.length < USERNAME_MIN || name.length > USERNAME_MAX ||
                    name.any { it.isWhitespace() || it.isISOControl() }
                ) {
                    fail(LocalError.BAD_USERNAME); return@launch
                }
                val normalizedPassword = java.text.Normalizer
                    .normalize(password, java.text.Normalizer.Form.NFKC)
                    .trim()
                if (normalizedPassword.length < PASSWORD_MIN || normalizedPassword.length > PASSWORD_MAX) {
                    fail(LocalError.BAD_PASSWORD); return@launch
                }
                if (mode == AuthMode.REGISTER && inviteCode.isBlank()) {
                    fail(LocalError.EMPTY_INVITE); return@launch
                }

                val auth = when (mode) {
                    AuthMode.LOGIN -> api.login(baseUrl, name, password)
                    AuthMode.REGISTER -> api.register(baseUrl, name, password, inviteCode.trim())
                }
                tokenStorage.save(auth.token)
                settingsRepository.setServerBaseUrl(baseUrl)
                _outcome.value = AuthOutcome.Success
            } catch (e: LnxApiException) {
                _outcome.value = AuthOutcome.Failure(e.code, e.message)
            } catch (e: Exception) {
                // 意料外的异常不该裸奔到界面,也不该被谎报成服务端的话
                _outcome.value = AuthOutcome.Failure("unreachable", e.message ?: "")
            } finally {
                _submitting.value = false
            }
        }
    }

    private fun fail(code: String) {
        _outcome.value = AuthOutcome.Failure(code, "")
    }

    /** 本地校验失败用的 code,和服务端错误码共用一套界面映射 */
    object LocalError {
        const val BAD_URL = "bad_url"
        const val BAD_USERNAME = "bad_username"
        const val BAD_PASSWORD = "weak_password"
        const val EMPTY_INVITE = "invite_required"
    }

    private companion object {
        // 镜像自服务端 AuthRoutes.validateUsername / Passwords.kt,改要一起改
        const val USERNAME_MIN = 2
        const val USERNAME_MAX = 32
        const val PASSWORD_MIN = 8
        const val PASSWORD_MAX = 128
    }
}