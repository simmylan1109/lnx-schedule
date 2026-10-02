package com.lnx.app.feature.onboarding

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.settings.SettingsRepository
import com.lnx.app.core.settings.TestSettingsModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首启引导三页(spec §3.12)。
 *
 * 默认所有仪器测试都预置"引导已完成"(见 [TestSettingsModule]) ——
 * 引导页会盖住日历首页,不预置的话 80 多条测试全灭。这个类把它关掉,专门测引导。
 * 开关必须放在 `@BeforeClass`:规则会在 `@Before` 之前就启动 Activity,那时设置已经建好了。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class OnboardingTest {

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
        fun showOnboarding() {
            TestSettingsModule.seedOnboardingDone = false
            // 演示动画与本测试注入的滑动在同一帧上竞争:关掉闸门,测的是用户自己的滑动
            // (与 TestSettingsModule.seedOnboardingDone 同一套测试闸门套路)
            com.lnx.app.feature.settings.PeekDemoHint.enabled = false
        }

        @AfterClass
        @JvmStatic
        fun restoreSeed() {
            // 漏掉这步,后面跑的测试类都会被引导页挡住
            TestSettingsModule.seedOnboardingDone = true
            com.lnx.app.feature.settings.PeekDemoHint.enabled = true
        }
    }

    private fun awaitPage(index: Int) {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("onboarding_page_$index").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    @Test
    fun 首次启动显示欢迎页_走完三页后进日历并打上标记() {
        awaitPage(OnboardingViewModel.PAGE_WELCOME)
        rule.onNodeWithTag("onboarding_start").performClick()

        awaitPage(OnboardingViewModel.PAGE_THEME)
        rule.onNodeWithTag("onboarding_skip").performClick()

        awaitPage(OnboardingViewModel.PAGE_PERMISSION)
        rule.onNodeWithTag("onboarding_later").performClick()

        // 收尾后直接进日历(spec §3.12:拒绝也不影响后续使用)
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("fab_create").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "走完引导应写入'已完成'标记",
            runBlocking { settings.current().onboardingDone },
        )
    }

    @Test
    fun 第二页选主题_立即生效且不会被换主题弹回第一页() {
        awaitPage(OnboardingViewModel.PAGE_WELCOME)
        rule.onNodeWithTag("onboarding_start").performClick()
        awaitPage(OnboardingViewModel.PAGE_THEME)

        rule.onNodeWithTag("theme_card_WARM").performClick()
        // 换主题会重建整棵子树(MainActivity 的 Crossfade),页面号必须活下来,
        // 否则用户点完卡就被弹回欢迎页 —— 这正是引导页状态放 ViewModel 的原因
        rule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { settings.current().themeSlot } == ThemeSlot.WARM
        }
        rule.waitForIdle()
        rule.onAllNodesWithTag("onboarding_page_${OnboardingViewModel.PAGE_THEME}")
            .fetchSemanticsNodes()
            .let { assertTrue("换主题后应仍停在选主题页", it.isNotEmpty()) }
    }

    @Test
    fun 主题没选也能走完_保持出厂主题() {
        awaitPage(OnboardingViewModel.PAGE_WELCOME)
        rule.onNodeWithTag("onboarding_start").performClick()
        awaitPage(OnboardingViewModel.PAGE_THEME)
        rule.onNodeWithTag("onboarding_skip").performClick()
        awaitPage(OnboardingViewModel.PAGE_PERMISSION)
        rule.onNodeWithTag("onboarding_later").performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("fab_create").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "没选主题时应保持出厂主题",
            com.lnx.app.core.designsystem.ThemeSlot.MATERIAL_YOU,
            runBlocking { settings.current().themeSlot },
        )
    }

    /**
     * v0.2 补欠账 ①:第 4 张主题卡(宁静冷色)完整地藏在屏幕右边缘之外 ——
     * 4 张卡加边距约 524dp,手机屏约 392dp,连"露一条边"都没有,用户不知道右边还有。
     * 修复 = 底部位置圆点 + 引导页自动演示一次。这里锁两件事:
     * 圆点必须出现;向左滑之后第 4 张卡必须真的能进语义树(LazyRow 不组装屏幕外的项,
     * 所以"存在"本身就是"看得到"的证据)。
     */
    @Test
    fun 第4张主题卡藏在屏幕外_有圆点提示_向左滑之后可达() {
        awaitPage(OnboardingViewModel.PAGE_WELCOME)
        rule.onNodeWithTag("onboarding_start").performClick()
        awaitPage(OnboardingViewModel.PAGE_THEME)

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("theme_row_dots").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithTag("theme_card_row").performTouchInput { swipeLeft() }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag("theme_card_SERENE")
                .fetchSemanticsNodes()
                .any { it.size.width > 0 }
        }
    }
}
