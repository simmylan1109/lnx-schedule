package com.lnx.app.feature.settings

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.common.LocaleContext
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
 * 语言切换端到端(spec §10):设置里点 English → 界面文案与日期一起变英文。
 *
 * 套件默认钉中文(见 `HiltTestRunner`),所以这里能放心断言中文;切英文后 Activity 会
 * `recreate()`,新语言从 `attachBaseContext` 的包装里生效 —— 这条链路只有仪器测试覆盖得到
 * (`LnxLocaleTest` / `RuleDescriptionTest` 只覆盖纯函数那一半)。
 *
 * 注意必须走界面点击那条路:直接调 `settings.setLanguage` 不会刷新进程内的语言缓存,
 * 重建时拿到的还是旧语言(缓存只由设置页那条路径与启动时的磁盘读取更新)。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LocaleSwitchTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        // **明确定义起点 = 中文**,不依赖上一个用例留下的语言:
        // 本类第一个用例会把语言切成英文并落盘,同类的第二个用例若直接跑,起点就是英文,
        // "中文环境下设置页应有「设置」"这类前置断言必然失败(踩过)。
        // 语言缓存是进程级的,所以除了写设置,还要同步缓存并重建一次界面。
        runBlocking { settings.setLanguage("zh") }
        LocaleContext.setLanguage("zh")
        rule.activityRule.scenario.onActivity { it.recreate() }
        rule.waitForIdle()
    }

    private fun openSettings() {
        rule.onNodeWithTag("menu_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_settings").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 设置页点 English(chips 顺序:中文 / English / 跟随系统 → lang_1) */
    private fun switchToEnglish() {
        openSettings()
        // 语言那一档在设置页底部、首屏之外:必须滚过去再点(不然点击落在屏幕外,
        // 看着"点了没反应" —— 这个坑实机手点时也踩过一次)
        rule.onNodeWithTag("lang_1").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        assertEquals("en", runBlocking { settings.current().language })
    }

    @Test
    fun 切到English后设置页文案与日期一起变英文() {
        openSettings()
        assertTrue(
            "中文环境下设置页应有「设置」",
            rule.onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty(),
        )

        rule.onNodeWithTag("lang_1").performScrollTo().performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Appearance").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        assertTrue(
            "切成英文后不该还有中文分组名",
            rule.onAllNodesWithText("外观").fetchSemanticsNodes().isEmpty(),
        )
        assertEquals("en", runBlocking { settings.current().language })
    }

    @Test
    fun 切成英文后日历顶栏用英文日期格式() {
        switchToEnglish()
        rule.onNodeWithTag("settings_back").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("top_bar").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()

        // 中文标题是「9月 · 29日 周二」,英文是「Sep 29, Tue」——用"还有没有中文月日"判断,
        // 不依赖跑测试那天是几月
        assertTrue(
            "英文环境下顶栏不该再出现中文「月 · 日」",
            rule.onAllNodesWithText("月 ·", substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }
}
