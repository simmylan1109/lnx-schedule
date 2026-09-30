package com.lnx.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四套主题的"风格差异"(spec §5.2 / §5.3)。M6 只做了配色,M7 把剩下的补齐:
 * 圆角、等宽数字、动效基线、主题 4 头部渐变。
 * 这些是纯 Kotlin 可判定的部分(形状/动效/颜色),渲染出的样子靠走查截图。
 */
class ThemeDifferentiationTest {

    @Test
    fun `四套主题的圆角各不相同且与spec一致`() {
        val my = shapesFor(ThemeSlot.MATERIAL_YOU)
        val paper = shapesFor(ThemeSlot.PAPER)
        val warm = shapesFor(ThemeSlot.WARM)
        val serene = shapesFor(ThemeSlot.SERENE)

        // Shapes 的档位是 Shape 而不是 Dp,按 RoundedCornerShape 的值比对
        // 主题 1:24dp 全套最大(spec §5.2)
        assertEquals(RoundedCornerShape(24.dp), my.extraLarge)
        // 主题 2:8–12dp,全套最小
        assertEquals(RoundedCornerShape(8.dp), paper.small)
        assertEquals(RoundedCornerShape(12.dp), paper.extraLarge)
        // 主题 3:16dp
        assertEquals(RoundedCornerShape(16.dp), warm.extraLarge)
        // 主题 4:20–24dp
        assertEquals(RoundedCornerShape(20.dp), serene.large)
        assertEquals(RoundedCornerShape(24.dp), serene.extraLarge)

        // 四套整套圆角两两不同(主题 1 与主题 4 的 extraLarge 恰好都是 24dp,
        // 但 large 档 16 vs 20 就分开了 —— 换主题看得出来)
        val all = listOf(my, paper, warm, serene)
        assertEquals(4, all.toSet().size)
    }

    @Test
    fun `主题2开等宽数字_其余三套不开`() {
        // fontFeatureSettings 是 JVM 侧就能读的字符串,不用上 Android
        assertTrue(typographyFor(ThemeSlot.PAPER).bodyMedium.fontFeatureSettings?.contains("tnum") == true)
        assertEquals(
            typographyFor(ThemeSlot.MATERIAL_YOU).bodyMedium.fontFeatureSettings,
            Typography().bodyMedium.fontFeatureSettings,
        )
        assertEquals(typographyFor(ThemeSlot.WARM).bodyMedium.fontFeatureSettings, null)
        assertEquals(typographyFor(ThemeSlot.SERENE).bodyMedium.fontFeatureSettings, null)
    }

    @Test
    fun `动效基线_主题4最慢_主题3弹簧_其余常规`() {
        val normal = LnxMotion.normal
        val slow = LnxMotion.slow
        val springy = LnxMotion.springy

        assertTrue(slow != normal)
        assertTrue(springy != normal)
        // 弹簧不是时长驱动的动画,所以和两条 tween 必然不同
        assertTrue(LnxThemeSpec(ThemeSlot.SERENE, dark = false).motion == slow)
        assertTrue(LnxThemeSpec(ThemeSlot.WARM, dark = false).motion == springy)
        assertTrue(LnxThemeSpec(ThemeSlot.MATERIAL_YOU, dark = false).motion == normal)
        assertTrue(LnxThemeSpec(ThemeSlot.PAPER, dark = false).motion == normal)
    }

    @Test
    fun `主题4头部渐变与spec色值一致`() {
        assertEquals(Color(0xFF74B9FF), SereneHeader.lightStart)
        assertEquals(Color(0xFFA29BFE), SereneHeader.lightEnd)
        assertEquals(Color(0xFF2C4A6E), SereneHeader.darkStart)
        assertEquals(Color(0xFF413A75), SereneHeader.darkEnd)
    }

    @Test
    fun `深浅两套渐变互不相同`() {
        assertFalse(
            SereneHeader.lightStart == SereneHeader.darkStart &&
                SereneHeader.lightEnd == SereneHeader.darkEnd,
        )
    }
}
