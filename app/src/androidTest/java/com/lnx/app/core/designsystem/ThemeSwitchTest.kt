package com.lnx.app.core.designsystem

import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主题即时切换(spec §8.1 M6 ①:4 套主题即时切换,约 250ms)。
 *
 * 断言的是**屏幕上真正渲染出来的底色**(`captureToImage` 取像素),不是设置里的状态值 ——
 * 设置写对了但 `LnxTheme` 没接上,只断言状态是发现不了的。
 * 取样用"多点投票取众数",避开文字、分割线、当前时刻线这些非底色像素。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ThemeSwitchTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        // 固定浅色:跟随系统的话模拟器切深色会让断言没法解释
        runBlocking { settings.setDarkMode(DarkMode.LIGHT) }
    }

    /** 取界面底色:空白区多点取样,众数即底色(量化到 8 位一档抗噪声) */
    private fun renderedBackground(): Int {
        val bitmap = rule.onNodeWithTag("app_root").captureToImage().asAndroidBitmap()
        val votes = mutableMapOf<Int, Int>()
        for (x in intArrayOf(20, bitmap.width / 4, bitmap.width - 20)) {
            for (y in intArrayOf(bitmap.height / 6, bitmap.height / 3, bitmap.height / 2)) {
                val c = bitmap.getPixel(x, y)
                val key = c and 0xF8F8F8.toInt()
                votes[key] = (votes[key] ?: 0) + 1
            }
        }
        return votes.maxBy { it.value }.key
    }

    private fun near(actual: Int, expected: Color, tolerance: Int = 14): Boolean =
        kotlin.math.abs(AndroidColor.red(actual) - (expected.red * 255).toInt()) <= tolerance &&
            kotlin.math.abs(AndroidColor.green(actual) - (expected.green * 255).toInt()) <= tolerance &&
            kotlin.math.abs(AndroidColor.blue(actual) - (expected.blue * 255).toInt()) <= tolerance

    /** 切主题并等 Crossfade 走完(过渡期间是中间色,取样会拿到半路值) */
    private fun switchToAndAwait(slot: ThemeSlot, scheme: ColorScheme) {
        runBlocking { settings.setThemeSlot(slot) }
        rule.waitUntil(timeoutMillis = 10_000) { near(renderedBackground(), scheme.background) }
        assertEquals(
            "${slot.name} 的底色应对 ${hexOf(scheme.background)}",
            true,
            near(renderedBackground(), scheme.background),
        )
    }

    private fun hexOf(c: Color) = "#%06X".format(((c.value shr 32) and 0xFFFFFFuL).toInt())

    @Test
    fun 极致留白_切过去底色变成白() {
        switchToAndAwait(ThemeSlot.PAPER, paperLightScheme())
    }

    @Test
    fun 暖橙活力_切过去底色变成暖白() {
        switchToAndAwait(ThemeSlot.WARM, warmLightScheme())
    }

    @Test
    fun 宁静冷色_切过去底色变成云白() {
        switchToAndAwait(ThemeSlot.SERENE, sereneLightScheme())
    }

    @Test
    fun 静态三套主题底色各不相同_切了不会等于没切() {
        val backgrounds = mutableListOf<Pair<ThemeSlot, Int>>()
        listOf(
            ThemeSlot.PAPER to paperLightScheme(),
            ThemeSlot.WARM to warmLightScheme(),
            ThemeSlot.SERENE to sereneLightScheme(),
        ).forEach { (slot, scheme) ->
            switchToAndAwait(slot, scheme)
            backgrounds += slot to renderedBackground()
        }
        assertEquals("三套主题底色应两两不同,实测=$backgrounds", 3, backgrounds.map { it.second }.distinct().size)
    }

    @Test
    fun 切回MaterialYou_底色与切走前不同() {
        // Material You 在 12+ 取壁纸动态色,和静态回退方案不是一回事,
        // 所以这里只能断言"确实换掉了",不能拿 materialYouLightScheme() 去对像素
        switchToAndAwait(ThemeSlot.WARM, warmLightScheme())
        val warm = renderedBackground()

        runBlocking { settings.setThemeSlot(ThemeSlot.MATERIAL_YOU) }
        rule.waitUntil(timeoutMillis = 10_000) { renderedBackground() != warm }
        assertNotEquals("切到 Material You 后底色应当变了", warm, renderedBackground())
    }

    @Test
    fun 深色模式_切过去底色变深() {
        runBlocking {
            settings.setThemeSlot(ThemeSlot.PAPER)
            settings.setDarkMode(DarkMode.DARK)
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            val c = renderedBackground()
            val sum = AndroidColor.red(c) + AndroidColor.green(c) + AndroidColor.blue(c)
            sum < 3 * 120 // #191919 三通道和 ≈ 25*3
        }
        assertNotEquals(
            "深色下不该还是浅色底",
            renderedBackground(),
            (0xFFFFFF and 0xF8F8F8.toInt()),
        )
    }
}
