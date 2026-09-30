package com.lnx.app.feature.settings

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.settings.TestSettingsModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 语言设置**冷启动就生效**(spec §3.11 通用② / §10)。
 *
 * 这条是终审抓到的 P1 的回归:语言读磁盘原本发生在 `MainActivity.onCreate`,
 * 而界面语言在 `attachBaseContext`(更早、同步)就定了 —— 于是"上次设成 English"的用户
 * 再次冷启动时,界面文案回到系统语言、日期却已经按英文渲染(中英混排),
 * 必须打开一次设置页才恢复。
 *
 * 这里模拟"冷启动":预置 `language=en` 后启动 Activity,**全程不打开设置页**,
 * 直接断言抽屉与日历已经是英文。修好之前这条必红。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LanguagePersistenceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    companion object {
        @BeforeClass
        @JvmStatic
        fun seedEnglish() {
            TestSettingsModule.seedLanguage = "en"
        }

        @AfterClass
        @JvmStatic
        fun restoreSeed() {
            // 漏掉这步,后面跑的测试类都会变成英文界面(中文断言全红)
            TestSettingsModule.seedLanguage = null
        }
    }

    @Test
    fun 上次设为英文的用户冷启动后界面与日期都是英文() {
        // 抽屉里的固定文案:不打开设置页也看得到
        rule.onNodeWithTag("menu_button").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("drawer_untagged").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        assertTrue(
            "冷启动后抽屉应是英文(Untagged),而不是系统语言。" +
                "诊断:LocaleContext.language=${com.lnx.app.core.common.LocaleContext.language}, " +
                "appliedLanguage=${com.lnx.app.core.common.LocaleContext.appliedLanguage}",
            rule.onAllNodesWithText("Untagged").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "不该出现中文的「未分类」",
            rule.onAllNodesWithText("未分类").fetchSemanticsNodes().isEmpty(),
        )

        // 程序化文案(顶栏日期)也必须是英文,不能和界面文案两套语言
        rule.onNodeWithTag("menu_button").performClick()
        rule.waitForIdle()
        assertTrue(
            "顶栏日期应是英文格式(如 Sep 30, Wed),不该还是「9月 · 30日 周三」",
            rule.onAllNodesWithText("月 ·", substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }
}
