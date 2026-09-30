package com.lnx.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

/**
 * 主题 4 宁静冷色的头部区(spec §5.2「头部区(顶栏 + Tab + 星期头):天空蓝→紫渐变」)。
 *
 * 只有 SERENE 走渐变,其余三套原样透出背景色 —— 所以调用方**不用写 if**,
 * 直接把头部内容套进这里即可。
 *
 * 前景色:渐变底上必须用白字。深色版渐变本身很暗(`#2C4A6E → #413A75`),
 * 白字对比度够;浅色版渐变亮,深色字反而更清楚。两套不能共用一套前景色。
 */
@Composable
fun SereneHeaderBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalLnxTheme.current
    if (spec.slot != ThemeSlot.SERENE) {
        Box(modifier = modifier, content = content)
        return
    }
    val (start, end) = if (spec.dark) {
        SereneHeader.darkStart to SereneHeader.darkEnd
    } else {
        SereneHeader.lightStart to SereneHeader.lightEnd
    }
    Box(
        modifier = modifier.background(Brush.horizontalGradient(listOf(start, end))),
        content = content,
    )
}

/** 落在 SERENE 头部渐变上的文字/图标该用什么色 */
@Composable
fun sereneHeaderForeground(): Color =
    if (LocalLnxTheme.current.dark) Color.White else SereneHeaderForegroundDark

private val SereneHeaderForegroundDark = Color(0xFF10233A)

/**
 * 头部区里"文字/图标"该用的颜色:SERENE 走渐变底,按深浅两套渐变分别取前景色;
 * 其余三套头部就是普通背景,照常用 `onSurface`。
 */
@Composable
fun headerContentColor(): Color = when (LocalLnxTheme.current.slot) {
    ThemeSlot.SERENE -> sereneHeaderForeground()
    else -> MaterialTheme.colorScheme.onSurface
}

/** 头部区里"次级文字"(Tab 未选中项、星期头)的颜色 */
@Composable
fun headerSecondaryContentColor(): Color = when (LocalLnxTheme.current.slot) {
    // 浅色渐变的紫端(#A29BFE)很亮,alpha 0.75 时对比度只有约 4.1:1,差 AA 一线;
    // 提到 0.85 才稳过 4.5:1(终审 P2-3)
    ThemeSlot.SERENE -> headerContentColor().copy(alpha = 0.85f)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
