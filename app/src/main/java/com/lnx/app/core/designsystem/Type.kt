package com.lnx.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/**
 * 每套主题的字(spec §5.2 / §5.3)。
 *
 * 基线取 Material3 默认,唯一的差异是**主题 2 极致留白开等宽数字(Tabular Figures)**:
 * 日历的时间列、日期数字必须等宽才排得齐 —— 比例数字下 `8` 比 `1` 宽,
 * 时间轴那一列每换一个整点宽度就跳一下。spec §5.2 原文「数字等宽对齐(Tabular Figures)」
 * 指的就是这个,用 `tnum` 字形特性实现,不必换字体(换了反而丢中文的系统字形)。
 * 只影响数字,汉字与拉丁字母不受影响。
 */
internal fun typographyFor(slot: ThemeSlot): Typography {
    val base = Typography()
    if (slot != ThemeSlot.PAPER) return base
    fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")
    return base.copy(
        displayLarge = base.displayLarge.tabular(),
        displayMedium = base.displayMedium.tabular(),
        displaySmall = base.displaySmall.tabular(),
        headlineLarge = base.headlineLarge.tabular(),
        headlineMedium = base.headlineMedium.tabular(),
        headlineSmall = base.headlineSmall.tabular(),
        titleLarge = base.titleLarge.tabular(),
        titleMedium = base.titleMedium.tabular(),
        titleSmall = base.titleSmall.tabular(),
        bodyLarge = base.bodyLarge.tabular(),
        bodyMedium = base.bodyMedium.tabular(),
        bodySmall = base.bodySmall.tabular(),
        labelLarge = base.labelLarge.tabular(),
        labelMedium = base.labelMedium.tabular(),
        labelSmall = base.labelSmall.tabular(),
    )
}

/** 保留旧引用名(测试与预览页用);运行时真正用的是 [typographyFor] */
val LnxTypography: Typography get() = Typography()
