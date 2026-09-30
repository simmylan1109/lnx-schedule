package com.lnx.app.core.common

import androidx.compose.runtime.compositionLocalOf
import com.lnx.app.core.settings.SettingsDefaults
import java.util.Locale

/**
 * 当前 App 语言,供 Compose 里的"程序化文案"取用([LnxLocale] 那批纯函数)。
 *
 * 为什么不让这些函数直接读系统 Locale:`DateFormatter` / `RuleDescription` 活在
 * domain/common 层,规格要求它们是**可 JVM 单测的纯函数**;语言必须显式传进来。
 * 界面层从这里取,再当参数传下去。
 */
val LocalLnxLocale = compositionLocalOf { LnxLocale.resolve(SettingsDefaults.LANGUAGE) }
