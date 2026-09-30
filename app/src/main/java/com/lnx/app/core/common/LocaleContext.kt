package com.lnx.app.core.common

import android.content.Context
import android.content.res.Configuration
import com.lnx.app.core.settings.SettingsDefaults
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
    }

    /**
     * 首次进入进程时从存储读一次(同步)。
     * 只在 Application/首次 Activity 启动时调,读的是个几百字节的小文件;
     * 之后一律走缓存,不再碰磁盘。
     */
    fun refreshFromDisk(read: suspend () -> String) {
        language = runCatching { runBlocking { read() } }
            .getOrDefault(SettingsDefaults.LANGUAGE)
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
