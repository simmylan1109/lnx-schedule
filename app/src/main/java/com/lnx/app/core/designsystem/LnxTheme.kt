package com.lnx.app.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** 当前主题环境(色位翻译、组件差异化样式都从这里取,别再读系统状态) */
data class LnxThemeSpec(val slot: ThemeSlot, val dark: Boolean)

val LocalLnxTheme = staticCompositionLocalOf { LnxThemeSpec(ThemeSlot.MATERIAL_YOU, dark = false) }

@Composable
fun LnxTheme(
    slot: ThemeSlot,
    darkMode: DarkMode,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = when (slot) {
        ThemeSlot.MATERIAL_YOU -> {
            val context = LocalContext.current
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (dark) materialYouDarkScheme() else materialYouLightScheme()
            }
        }
        // PAPER / WARM / SERENE 在 M6 实现;先回退主题 1 静态方案
        else -> if (dark) materialYouDarkScheme() else materialYouLightScheme()
    }
    CompositionLocalProvider(LocalLnxTheme provides LnxThemeSpec(slot, dark)) {
        MaterialTheme(colorScheme = scheme, typography = LnxTypography, content = content)
    }
}
