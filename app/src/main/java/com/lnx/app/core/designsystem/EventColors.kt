package com.lnx.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.lnx.app.core.domain.model.Priority

/**
 * 事件色位(spec §5.4):存的是 0..7 的**编号**,不是颜色;
 * 每套主题把编号翻译成符合自身气质的色值,换主题时事件颜色自动跟随。
 *
 * M2 先落地主题 1(Material You)的一套,M6 接其余三套主题时只改这里。
 */
object EventColors {
    private val Light = listOf(
        Color(0xFFD06A5C), // 0 红
        Color(0xFFC58F3A), // 1 橙
        Color(0xFFC9B458), // 2 黄
        Color(0xFF5B9E52), // 3 绿
        Color(0xFF4E7BD0), // 4 蓝
        Color(0xFF6750A4), // 5 紫
        Color(0xFFB05CA8), // 6 粉
        Color(0xFF8B8B94), // 7 灰
    )

    private val Dark = listOf(
        Color(0xFFE39B90),
        Color(0xFFDCB978),
        Color(0xFFDDCE84),
        Color(0xFF8FCB86),
        Color(0xFF8FAEEA),
        Color(0xFFB9A5E8),
        Color(0xFFDD93D6),
        Color(0xFFB6B6BE),
    )

    @Composable
    @ReadOnlyComposable
    fun of(slot: Int): Color = list()[slot.coerceIn(0, 7)]

    /** 色位上的文字色:浅色位用深字、深色位用浅字,保证可读 */
    @Composable
    @ReadOnlyComposable
    fun on(slot: Int): Color {
        val background = of(slot)
        val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
        return if (luminance > 0.5f) Color(0xFF1B1B1B) else Color(0xFFF7F7F7)
    }

    @Composable
    @ReadOnlyComposable
    fun list(): List<Color> = if (isDark()) Dark else Light

    @Composable
    @ReadOnlyComposable
    fun priorityColor(priority: Priority): Color = when (priority) {
        Priority.P0 -> of(0)
        Priority.P1 -> of(1)
        Priority.P2 -> of(4)
        Priority.P3 -> of(7)
    }
}

@Composable
@ReadOnlyComposable
private fun isDark(): Boolean = androidx.compose.foundation.isSystemInDarkTheme()
