package com.lnx.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 事件色板(spec §5.4):8 色位 × 4 主题的浅色列必须逐值等于 spec 表;
 * 深色 = 同色位提亮降饱和;色位越界安全钳制。
 */
class EventColorsTest {

    private fun colors(vararg values: Long) = values.map { Color(it) }

    @Test
    fun `主题1浅色列与spec542表一致`() {
        assertEquals(
            colors(0xFFD06A5C, 0xFFC58F3A, 0xFFC9B458, 0xFF5B9E52, 0xFF4E7BD0, 0xFF6750A4, 0xFFB05CA8, 0xFF8B8B94),
            EventColors.resolve(ThemeSlot.MATERIAL_YOU, dark = false),
        )
    }

    @Test
    fun `主题2到4浅色列与spec542表一致`() {
        assertEquals(
            colors(0xFFC4453C, 0xFF8F6A3D, 0xFFB29228, 0xFF3D7C43, 0xFF2383E2, 0xFF6A4FB6, 0xFFC2185B, 0xFF787774),
            EventColors.resolve(ThemeSlot.PAPER, dark = false),
        )
        assertEquals(
            colors(0xFFF4511E, 0xFFFF9800, 0xFFFDD835, 0xFF4CAF50, 0xFF2196F3, 0xFF9C27B0, 0xFFC2185B, 0xFF607D8B),
            EventColors.resolve(ThemeSlot.WARM, dark = false),
        )
        assertEquals(
            colors(0xFFD97B8F, 0xFFE8A87C, 0xFFC9A86A, 0xFF7FBF8E, 0xFF5FA8DC, 0xFFA78FE0, 0xFFD97B8F, 0xFF8B93A1),
            EventColors.resolve(ThemeSlot.SERENE, dark = false),
        )
    }

    @Test
    fun `深色版提亮_非主题1按派生`() {
        listOf(ThemeSlot.PAPER, ThemeSlot.WARM, ThemeSlot.SERENE).forEach { slot ->
            val light = EventColors.resolve(slot, dark = false)
            val dark = EventColors.resolve(slot, dark = true)
            assertEquals(8, dark.size)
            assertNotEquals("深色应与浅色不同:$slot", light, dark)
            light.zip(dark).forEach { (l, d) ->
                assertTrue("深色应更亮或等亮:$slot", d.luminance() + 1e-3f >= l.luminance())
            }
        }
    }

    @Test
    fun `事件块上的文字色对四主题深浅八色位全部过AA`() {
        // spec §5.3 要求对比度满足 AA(正文 4.5:1)。深色版是派生出来的,
        // 过去没有任何断言兜着,坏色位会一路带到 M6 验收才暴露。
        ThemeSlot.entries.forEach { slot ->
            listOf(false, true).forEach { dark ->
                EventColors.resolve(slot, dark).forEachIndexed { index, background ->
                    val ratio = EventColors.contrastRatio(background, EventColors.textOn(background))
                    assertTrue(
                        "主题$slot 深色=$dark 色位$index 对比度只有 $ratio",
                        ratio >= 4.5,
                    )
                }
            }
        }
    }

    @Test
    fun `对比度按WCAG相对亮度算而不是RGB加权`() {
        // 中灰 #808080:直接 RGB 加权得 0.5,线性化后只有约 0.216。
        // 两种算法差一倍以上,算错会让深色底的文字色判反。
        assertEquals(0.216, EventColors.relativeLuminance(Color(0xFF808080)), 0.01)
        assertEquals(1.0, EventColors.relativeLuminance(Color.White), 0.001)
        assertEquals(21.0, EventColors.contrastRatio(Color.White, Color.Black), 0.1)
    }

    @Test
    fun `主题1深色用手调表`() {
        // 主题 1 的深色是手调的(动态取色的回退),不参与派生
        assertEquals(Color(0xFFE39B90), EventColors.resolve(ThemeSlot.MATERIAL_YOU, dark = true).first())
    }

    @Test
    fun `色位越界钳制到两端`() {
        assertEquals(0, EventColors.safeIndex(-1, size = 8))
        assertEquals(7, EventColors.safeIndex(9, size = 8))
        assertEquals(3, EventColors.safeIndex(3, size = 8))
    }
}
