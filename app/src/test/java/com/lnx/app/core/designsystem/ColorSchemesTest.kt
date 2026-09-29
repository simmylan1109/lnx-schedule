package com.lnx.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四套主题的配色(spec §5.2)与**无障碍对比度**(§5.3:每套主题独立设计深色版,
 * 文字对比度满足 AA)。
 *
 * 对比度这一组是关键:M3 只验了事件色位上的文字(靠 `EventColors.textOn` 兜底),
 * 但界面文字(正文/辅助/按钮)走的是 `ColorScheme` 的 on* 配对,没有任何断言保护 ——
 * 主色定得好看却配了白字,就是 3.9:1 的"好看但看不清"。这里把八套方案全钉住。
 */
class ColorSchemesTest {

    // Color.value 是 0xAARRGGBB 打包(低 32 位是 RGBA),取高 32 位才是 spec 里的 RRGGBB
    private fun hex(c: Color): String = "#%06X".format(((c.value shr 32) and 0xFFFFFFuL).toInt())

    private fun Color.eq(spec: String) = assertEquals(spec, hex(this))

    // —— M1 已有 ——

    @Test
    fun `materialYou 回退浅色方案 背景为 FEF7FF`() {
        assertEquals(Color(0xFFFEF7FF), materialYouLightScheme().background)
    }

    @Test
    fun `materialYou 回退深色方案 背景为 1D1B20`() {
        assertEquals(Color(0xFF1D1B20), materialYouDarkScheme().background)
    }

    @Test
    fun `回退主色为内置蓝紫`() {
        assertEquals(Color(0xFF6750A4), materialYouLightScheme().primary)
        assertEquals(Color(0xFFCFBCFF), materialYouDarkScheme().primary)
    }

    // —— spec §5.2 逐值 ——

    @Test
    fun `极致留白_浅色与spec一致`() {
        val s = paperLightScheme()
        s.background.eq("#FFFFFF")
        s.onBackground.eq("#37352F")
        s.surface.eq("#FFFFFF")
        s.onSurface.eq("#37352F")
    }

    @Test
    fun `极致留白_深色与spec一致`() {
        val s = paperDarkScheme()
        s.background.eq("#191919")
        s.onBackground.eq("#E1E1E1")
    }

    @Test
    fun `暖橙活力_浅色与spec一致`() {
        val s = warmLightScheme()
        s.background.eq("#FFFAF5")
        s.primary.eq("#F86B3D")
    }

    @Test
    fun `暖橙活力_深色与spec一致`() {
        val s = warmDarkScheme()
        s.background.eq("#1A1A1A")
        s.primary.eq("#FF7A50")
    }

    @Test
    fun `宁静冷色_浅色与spec一致`() {
        val s = sereneLightScheme()
        s.background.eq("#F8F6F0")
    }

    @Test
    fun `宁静冷色_深色与spec一致`() {
        val s = sereneDarkScheme()
        s.background.eq("#1A2A3A")
    }

    // —— §5.3 无障碍 AA:正文 / 辅助文字 / 主色上的字都得过 4.5:1 ——

    private fun allSchemes(): List<Pair<String, ColorScheme>> = listOf(
        "Material You 浅" to materialYouLightScheme(),
        "Material You 深" to materialYouDarkScheme(),
        "极致留白 浅" to paperLightScheme(),
        "极致留白 深" to paperDarkScheme(),
        "暖橙活力 浅" to warmLightScheme(),
        "暖橙活力 深" to warmDarkScheme(),
        "宁静冷色 浅" to sereneLightScheme(),
        "宁静冷色 深" to sereneDarkScheme(),
    )

    @Test
    fun `每套主题的正文与背景对比度过AA`() {
        allSchemes().forEach { (name, s) ->
            assertTrue(
                "$name onBackground/background 只有 ${EventColors.contrastRatio(s.onBackground, s.background)}:1",
                EventColors.contrastRatio(s.onBackground, s.background) >= AA,
            )
            assertTrue(
                "$name onSurface/surface 对比度不足",
                EventColors.contrastRatio(s.onSurface, s.surface) >= AA,
            )
        }
    }

    @Test
    fun `每套主题的辅助文字过AA`() {
        allSchemes().forEach { (name, s) ->
            val ratio = EventColors.contrastRatio(s.onSurfaceVariant, s.surface)
            assertTrue("$name onSurfaceVariant/surface 只有 $ratio:1", ratio >= AA)
        }
    }

    @Test
    fun `每套主题的主色上文字过AA`() {
        allSchemes().forEach { (name, s) ->
            val ratio = EventColors.contrastRatio(s.onPrimary, s.primary)
            assertTrue("$name onPrimary/primary 只有 $ratio:1", ratio >= AA)
        }
    }

    @Test
    fun `主色容器上的文字也过AA`() {
        allSchemes().forEach { (name, s) ->
            val ratio = EventColors.contrastRatio(s.onPrimaryContainer, s.primaryContainer)
            assertTrue("$name onPrimaryContainer/primaryContainer 只有 $ratio:1", ratio >= AA)
        }
    }

    private companion object {
        const val AA = 4.5
    }
}
