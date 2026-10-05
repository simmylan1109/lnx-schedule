package com.lnx.app.feature.onboarding

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.settings.SettingsRepository
import com.lnx.app.core.settings.TestSettingsModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首启隐私披露页(v0.3 A1)。
 *
 * 默认所有仪器测试都预置"披露已读"(见 [TestSettingsModule])—— 披露页排在引导页**前面**,
 * 不预置的话一启动就被它盖住,80 多条找日历首页的测试会集体报"找不到 fab_create"。
 * 这个类把它关掉,专门测披露本身;开关必须放 `@BeforeClass`(规则会在 `@Before` 之前
 * 启动 Activity,那时设置已经建好了)。
 *
 * 断言只锁三件事:①五段条文都在(少一段就是没给用户看全)②两个按钮都放行,
 * 不同意联网的人照样能进引导页③都写上"已读"标记。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PrivacyDisclosureTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun showDisclosure() {
            // 引导页**不预置**:这两个用例要验的正是"读完披露能进引导页",
            // 预置掉就变成直接进日历,onboarding_page_0 永远不会出现。
            // 第一个用例(只查五段条文与两个按钮都在)与这个无关,照样成立。
            TestSettingsModule.seedOnboardingDone = false
            TestSettingsModule.seedPrivacyDisclosureDone = false
        }

        @AfterClass
        @JvmStatic
        fun restoreSeed() {
            // 漏掉这步,后面跑的测试类都会被披露页挡住
            TestSettingsModule.seedPrivacyDisclosureDone = true
            TestSettingsModule.seedOnboardingDone = true
        }
    }

    private fun awaitTag(tag: String) {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    @Test
    fun 首次启动先显示披露页_五段条文与两个按钮都在() {
        awaitTag("privacy_title")
        awaitTag("privacy_body")
        awaitTag("privacy_section_now")
        awaitTag("privacy_section_sync")
        awaitTag("privacy_section_no_do")
        awaitTag("privacy_section_rights")
        awaitTag("privacy_section_self_hosted")
        rule.onAllNodesWithTag("privacy_agree").fetchSemanticsNodes()
            .let { assertTrue("应有'同意并继续'按钮", it.isNotEmpty()) }
        rule.onAllNodesWithTag("privacy_local_only").fetchSemanticsNodes()
            .let { assertTrue("应有'我先只用本地'按钮", it.isNotEmpty()) }
    }

    @Test
    fun 点同意_进引导页并写上已读标记() {
        awaitTag("privacy_agree")
        rule.onNodeWithTag("privacy_agree").performClick()

        awaitTag("onboarding_page_${OnboardingViewModel.PAGE_WELCOME}")
        assertTrue(
            "读完披露应写入'已读'标记",
            runBlocking { settings.current().privacyDisclosureDone },
        )
    }

    /**
     * 合规硬要求:不同意联网**不得**影响使用。个人信息保护法第十六条禁止拿同意当
     * 使用前提,所以"我先只用本地"必须和"同意"走同一个出口 —— 都要能进引导页。
     */
    @Test
    fun 点先只用本地_同样放行进引导页并写上已读标记() {
        awaitTag("privacy_local_only")
        rule.onNodeWithTag("privacy_local_only").performClick()

        awaitTag("onboarding_page_${OnboardingViewModel.PAGE_WELCOME}")
        assertTrue(
            "拒绝联网也应记为'已读过'",
            runBlocking { settings.current().privacyDisclosureDone },
        )
    }
}