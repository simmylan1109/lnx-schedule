package com.lnx.app.core.common

import android.content.Context
import android.content.res.Configuration
import com.lnx.app.core.settings.SettingsDefaults
import com.lnx.app.core.settings.settingsEntryPoint
import kotlinx.coroutines.runBlocking

/**
 * 进程内的当前语言,以及"把 Context 换成这个语言"的包装(spec §10 国际化)。
 *
 * **不引 appcompat**(M6 T6 裁定):`AppCompatDelegate.setApplicationLocales` 要求
 * Activity 是 `AppCompatActivity`,为了一个语言开关把整个 AppCompat 拖进来不划算;
 * API 33+ 也不必用 `LocaleManager` —— 我们自己换 `Configuration` 一样能覆盖到
 * 界面文案(资源解析)、Material 组件、以及我们自己的日期格式。
 *
 * 为什么要有这个**进程级缓存**:`attachBaseContext` 是同步方法,读不了 DataStore(挂起)。
 * 所以语言值在别处(设置页写入时)同步进这里,`attachBaseContext` 直接取,零阻塞。
 */
object LocaleContext {

    @Volatile
    var language: String = SettingsDefaults.LANGUAGE
        private set

    /**
     * 当前界面**实际生效**的语言(在 [wrap] 里记录)。
     * 设置页拿它和最新设置比对:不一致就重建一次 Activity,一致就不动 ——
     * 这样既能在换语言后立刻生效,又不会自我循环重建。
     */
    @Volatile
    var appliedLanguage: String = SettingsDefaults.LANGUAGE
        private set

    /** 设置页改了语言时调用;之后 `recreate()` 的 Activity 就会用新语言 */
    fun setLanguage(value: String) {
        language = value
        loaded = true
    }

    /** 进程内是否已从磁盘读过一次 */
    @Volatile
    private var loaded = false

    /**
     * **仅供测试**:仪器测试每跑一个用例就换一份设置存储(等价于"换了一个安装"),
     * 进程级缓存必须跟着失效,否则第二个用例读到的还是上一个用例的语言。
     * 由 `TestSettingsModule` 在创建新存储时调用。
     */
    @androidx.annotation.VisibleForTesting
    fun resetForTest() {
        loaded = false
        language = SettingsDefaults.LANGUAGE
        appliedLanguage = SettingsDefaults.LANGUAGE
    }

    /**
     * 进程内**第一次**需要语言时,同步从存储读一次并缓存。
     *
     * 调用点是 `MainActivity.attachBaseContext` 的最前面 —— 那是"界面语言即将定下来"的
     * 唯一时刻,比它晚了就来不及(终审 P1:读盘原本在 `onCreate`,而界面语言在
     * `attachBaseContext` 就定了,于是"上次设成 English"的用户冷启动后界面是系统语言、
     * 日期却已是英文,中英混排,必须开一次设置页才恢复)。
     *
     * 从 Application.onCreate 读更早,但仪器测试跑 HiltTestApplication、不执行被测的
     * Application 子类,那样这条链路就测不到;放在这里生产与测试走同一条路。
     */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val value = runCatching {
            runBlocking { settingsEntryPoint(context)?.settingsRepository()?.current()?.language }
        }.getOrNull()
        if (!value.isNullOrBlank()) language = value
    }

    /** 用当前语言包一层 Context;界面文案与日期格式都跟着变 */
    fun wrap(base: Context): Context {
        val locale = LnxLocale.resolve(language)
        appliedLanguage = language
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
