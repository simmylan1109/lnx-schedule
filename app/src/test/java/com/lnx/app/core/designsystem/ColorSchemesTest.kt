package com.lnx.app.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSchemesTest {
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
}
