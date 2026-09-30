package com.lnx.app.core.settings

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 非 Compose/非 ViewModel 的地方取设置仓库的入口(目前是 `LocaleContext.ensureLoaded`)。
 *
 * 走 EntryPoint + 容错:与接收器同一套路 —— 组件没就绪时返回 null,调用方回落到出厂值,
 * 不允许"取不到依赖"把界面启动带崩。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun settingsRepository(): SettingsRepository
}

internal fun settingsEntryPoint(context: Context): SettingsEntryPoint? =
    runCatching {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsEntryPoint::class.java,
        )
    }.getOrNull()
