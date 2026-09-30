package com.lnx.app.core.designsystem

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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

/**
 * 动效基线(spec §5.3):常规 150–300ms;**主题 4 宁静冷色放慢到 400–600ms**(呼吸感)。
 * 组件里不要各写各的 `tween(...)`,统一从这里取,换主题的"慢"才是全局一致的。
 */
object LnxMotion {
    const val NORMAL_MILLIS = 300
    const val SLOW_MILLIS = 500
    const val THEME_CROSSFADE_MILLIS = 250 // spec §5.3 主题切换约 250ms

    val normal: FiniteAnimationSpec<Float> get() = tween(NORMAL_MILLIS)
    val slow: FiniteAnimationSpec<Float> get() = tween(SLOW_MILLIS)

    /** 主题 3 暖橙活力(spec §5.2「动效弹簧曲线」):轻快、到位时有一点回弹 */
    val springy: FiniteAnimationSpec<Float> get() = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/**
 * 该主题下的动效基线(spec §5.2 / §5.3):
 * 宁静冷色 400–600ms 的"呼吸感",暖橙活力走弹簧,其余走常规 300ms。
 * 组件里不要各写各的 `tween(...)`,统一从这里取,换主题的手感才是全局一致的。
 */
val LnxThemeSpec.motion: FiniteAnimationSpec<Float>
    get() = when (slot) {
        ThemeSlot.SERENE -> LnxMotion.slow
        ThemeSlot.WARM -> LnxMotion.springy
        else -> LnxMotion.normal
    }

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
    val scheme = schemeFor(slot, dark)
    CompositionLocalProvider(LocalLnxTheme provides LnxThemeSpec(slot, dark)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typographyFor(slot),
            shapes = shapesFor(slot),
            content = content,
        )
    }
}

/**
 * 槽位 → 配色。Material You 在 12+ 取壁纸动态色,低版本用内置蓝紫回退(spec §5.2);
 * 其余三套是各自的静态方案,M6 起不再回退到主题 1。
 */
@Composable
fun schemeFor(slot: ThemeSlot, dark: Boolean) = when (slot) {
    ThemeSlot.MATERIAL_YOU -> {
        val context = LocalContext.current
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) materialYouDarkScheme() else materialYouLightScheme()
        }
    }
    ThemeSlot.PAPER -> if (dark) paperDarkScheme() else paperLightScheme()
    ThemeSlot.WARM -> if (dark) warmDarkScheme() else warmLightScheme()
    ThemeSlot.SERENE -> if (dark) sereneDarkScheme() else sereneLightScheme()
}
