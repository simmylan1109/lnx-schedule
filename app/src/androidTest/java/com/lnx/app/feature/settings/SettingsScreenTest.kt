package com.lnx.app.feature.settings

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.designsystem.warmLightScheme
import com.lnx.app.core.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 设置页(spec §3.11):抽屉入口进页、四组齐全、点主题卡即换肤、默认提醒改了被记住。
 *
 * 第二个用例是**实机踩出来的回归**:MainActivity 用 `Crossfade(themeSlot)` 包住整棵
 * 日历子树,换主题瞬间子树重建、`remember` 状态归零 —— 设置页开合当时记在 remember 里,
 * 点一下主题卡页面就把自己关了。修复后状态进了 ViewModel(活过重建),这里钉死:
 * 连点三张卡之后设置页必须还在。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        // 固定浅色:跟随系统的话模拟器切深色会让底色断言没法解释
        runBlocking { settings.setDarkMode(DarkMode.LIGHT) }
    }

    /** 汉堡菜单 → 抽屉 → 设置入口,与用户真实路径一致 */
    private fun openSettings() {
        rule.onNodeWithTag("menu_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_screen").assertExists()
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

    @Test
    fun 打开设置_四组标题都在() {
        openSettings()
        listOf(
            "settings_section_appearance",
            "settings_section_reminder",
            "settings_section_general",
            "settings_section_data",
        ).forEach { rule.onNodeWithTag(it).assertExists() }
    }

    @Test
    fun 连点三张主题卡_设置页不关闭_返回后日历底色变成最后那张() {
        openSettings()

        // 每点一张都等设置落盘 + Crossfade 走完(子树此时已重建),再断言页面还在 ——
        // 旧 bug 下重建会把 remember 状态清零,这里必然失败
        listOf(ThemeSlot.MATERIAL_YOU, ThemeSlot.PAPER, ThemeSlot.WARM).forEach { slot ->
            rule.onNodeWithTag("theme_card_${slot.name}").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runBlocking { settings.current().themeSlot } == slot
            }
            rule.waitForIdle()
            rule.onNodeWithTag("settings_screen").assertExists()
        }

        // 返回键关设置页,日历底色应是最后点的暖橙活力(spec §3.11 点一下即生效)
        rule.onNodeWithTag("settings_back").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            near(renderedBackground(), warmLightScheme().background)
        }
        assertEquals(
            "底色应对暖橙活力的暖白",
            true,
            near(renderedBackground(), warmLightScheme().background),
        )
    }

    @Test
    fun 改默认提醒为30分钟_设置里记住30() {
        openSettings()
        // chips 顺序:不提醒/5/15/30/60 → 30 分钟是第 4 个(tag lead_3)
        rule.onNodeWithTag("lead_3").performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { settings.current().reminderLeadMinutes } == 30
        }
        assertEquals(30, runBlocking { settings.current() }.reminderLeadMinutes)
    }

    /**
     * 去双源的端到端一半(spec §3.5:新建事件的提醒 = 设置中的默认值,出厂 15):
     * 设置里改成 30,新建日程的提醒行就该默认选中 30,而不是仍写死的 15。
     * 单测(草稿层)和这条(真界面)各钉一半,谁漏了都跑不掉。
     */
    @Test
    fun 改了默认提醒后新建日程的提醒行跟着变() {
        runBlocking { settings.setReminderLead(30) }

        rule.onNodeWithTag("fab_create").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("reminder_30").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "新建日程应默认选中 30 分钟提醒档",
            rule.onAllNodesWithTag("reminder_30").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * 外观模式三选(spec §3.11 ②):点"深色"→ 底色变深;点回"浅色"→ 变亮。
     *
     * 之前只有"从仓库写值"的旁路测试(`ThemeSwitchTest`),界面上的 index↔DarkMode 映射
     * 写反了也不会有人发现(终审点名)。这里走真实点击路径,并用 app_root 的真实渲染像素判定。
     */
    @Test
    fun 外观模式点深色变深_点回浅色变亮() {
        openSettings()

        rule.onNodeWithTag("mode_2").performScrollTo().performClick() // mode_2 = 深色
        rule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { settings.current().darkMode } == DarkMode.DARK
        }
        rule.waitUntil(timeoutMillis = 10_000) { isDarkBackground() }
        assertTrue("点深色后底色应变深", isDarkBackground())

        rule.onNodeWithTag("mode_1").performScrollTo().performClick() // mode_1 = 浅色
        rule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { settings.current().darkMode } == DarkMode.LIGHT
        }
        rule.waitUntil(timeoutMillis = 10_000) { !isDarkBackground() }
        assertTrue("点回浅色后底色应变亮", !isDarkBackground())
    }

    /** 底色是否偏深(三通道和 < 3×120) */
    private fun isDarkBackground(): Boolean {
        val c = renderedBackground()
        return AndroidColor.red(c) + AndroidColor.green(c) + AndroidColor.blue(c) < 3 * 120
    }
}
