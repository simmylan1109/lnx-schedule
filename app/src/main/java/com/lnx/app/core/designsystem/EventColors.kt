package com.lnx.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lnx.app.core.domain.model.Priority

/**
 * 事件色板(spec §5.4):存的是 0..7 的**色位编号**,不是颜色;
 * 每套主题把编号翻译成符合自身气质的色值,换主题时事件颜色自动跟随。
 *
 * resolve 是纯函数(可 JVM 单测钉 spec 表);Composable 入口从 [LocalLnxTheme] 取当前主题。
 */
object EventColors {
    // 主题 1(Material You):动态取色的回退表,深色手调
    private val Theme1Light = listOf(
        Color(0xFFD06A5C), Color(0xFFC58F3A), Color(0xFFC9B458), Color(0xFF5B9E52),
        Color(0xFF4E7BD0), Color(0xFF6750A4), Color(0xFFB05CA8), Color(0xFF8B8B94),
    )

    private val Theme1Dark = listOf(
        Color(0xFFE39B90), Color(0xFFDCB978), Color(0xFFDDCE84), Color(0xFF8FCB86),
        Color(0xFF8FAEEA), Color(0xFFB9A5E8), Color(0xFFDD93D6), Color(0xFFB6B6BE),
    )

    // 主题 2-4 浅色 = spec §5.4 表;深色按"提亮降饱和"派生
    private val Theme2Light = listOf(
        Color(0xFFC4453C), Color(0xFF8F6A3D), Color(0xFFB29228), Color(0xFF3D7C43),
        Color(0xFF2383E2), Color(0xFF6A4FB6), Color(0xFFC2185B), Color(0xFF787774),
    )

    private val Theme3Light = listOf(
        Color(0xFFF4511E), Color(0xFFFF9800), Color(0xFFFDD835), Color(0xFF4CAF50),
        Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFC2185B), Color(0xFF607D8B),
    )

    private val Theme4Light = listOf(
        Color(0xFFD97B8F), Color(0xFFE8A87C), Color(0xFFC9A86A), Color(0xFF7FBF8E),
        Color(0xFF5FA8DC), Color(0xFFA78FE0), Color(0xFFD97B8F), Color(0xFF8B93A1),
    )

    /** 主题 × 深浅 → 8 色位色表(spec §5.4 浅色逐值钉死;深色 = 同色位向白混合 40%) */
    fun resolve(slot: ThemeSlot, dark: Boolean): List<Color> {
        val light = when (slot) {
            ThemeSlot.MATERIAL_YOU -> Theme1Light
            ThemeSlot.PAPER -> Theme2Light
            ThemeSlot.WARM -> Theme3Light
            ThemeSlot.SERENE -> Theme4Light
        }
        if (slot == ThemeSlot.MATERIAL_YOU) return if (dark) Theme1Dark else light
        if (!dark) return light
        return light.map { lerp(it, Color.White, 0.4f) }
    }

    /** 数据里的色位越界(脏数据/未来扩展)时钳到两端,不允许崩 */
    fun safeIndex(slot: Int, size: Int): Int = slot.coerceIn(0, size - 1)

    @Composable
    @ReadOnlyComposable
    fun list(): List<Color> = resolve(LocalLnxTheme.current.slot, LocalLnxTheme.current.dark)

    @Composable
    @ReadOnlyComposable
    fun of(slot: Int): Color = list()[safeIndex(slot, list().size)]

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
    fun priorityColor(priority: Priority): Color = when (priority) {
        Priority.P0 -> of(0)
        Priority.P1 -> of(1)
        Priority.P2 -> of(4)
        Priority.P3 -> of(7)
    }
}
