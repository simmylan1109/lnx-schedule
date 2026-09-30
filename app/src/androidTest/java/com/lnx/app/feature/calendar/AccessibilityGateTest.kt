package com.lnx.app.feature.calendar

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 无障碍闸门(M9):**界面上所有能点的东西,读屏都得能念出点什么。**
 *
 * 查的是什么:可点击节点(自身或子孙)既没有文字、也没有 contentDescription。
 * 这类节点在 uiautomator 里叫 NAF("not accessibility friendly")——读屏用户会听到
 * 一个沉默的"按钮",完全不知道按下去会发生什么。走查时编辑器里 8 个颜色圆点就是
 * 这个样子(纯色块,零信息),读屏用户一个颜色都挑不了。
 *
 * **这道闸门管不了的事**(说清楚,免得以后误以为它万能):它只管"完全没标签",
 * 不管"标签信息够不够"。比如月历格子只念得出"30",它有标签、这道闸门放行,
 * 但对读屏用户毫无意义 —— 那是靠 `contentDescription` 里补全日期和日程数解决的。
 * "够不够"只能靠人读一遍。
 *
 * 遍历用 [SemanticsNode.fetchSemanticsChildren],它只给出真正暴露给无障碍层的节点,
 * 合并掉的子节点不会重复计入。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AccessibilityGateTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @Test
    fun 周视图里没有读屏念不出东西的可点目标() {
        awaitHome()
        rule.onNodeWithTag("week_pager").assertExists()
        rule.assertNoUnlabelledClickables("周视图")
    }

    @Test
    fun 月视图里没有读屏念不出东西的可点目标() {
        awaitHome()
        rule.onNodeWithTag("tab_MONTH").performClick()
        rule.waitForIdle()
        rule.assertNoUnlabelledClickables("月视图")
    }

    @Test
    fun 日视图里没有读屏念不出东西的可点目标() {
        awaitHome()
        rule.onNodeWithTag("tab_DAY").performClick()
        rule.waitForIdle()
        rule.assertNoUnlabelledClickables("日视图")
    }

    @Test
    fun 抽屉里没有读屏念不出东西的可点目标() {
        awaitHome()
        rule.onNodeWithTag("menu_button").performClick()
        rule.waitForIdle()
        rule.assertNoUnlabelledClickables("抽屉")
    }

    /**
     * 编辑器是**这道闸门的出身**:走查时 8 个颜色圆点就是 NAF(纯色块、零信息),
     * 读屏用户一个颜色都挑不了。不加这一条,那次的教训就只在人脑子里,不在测试里。
     */
    @Test
    fun 事件编辑器里没有读屏念不出东西的可点目标() {
        awaitHome()
        rule.onNodeWithTag("fab_create").performClick()
        rule.waitForIdle()
        rule.assertNoUnlabelledClickables("事件编辑器")
    }

    /**
     * 新建标签对话框:里面也有一排颜色圆点,和编辑器那个是两份独立代码。
     *
     * **这条不走通用闸门**:Compose 的 `AlertDialog` 是独立窗口,弹出来时
     * `onRoot()` 拿不到活动窗口的语义根(会报 "Failed: assertExists"),
     * 闸门遍历无从谈起。但按 testTag 查节点照常可用,所以改成针对性断言:
     * 8 个色点必须各有一个互不相同的颜色名。
     */
    @Test
    fun 标签对话框的8个色点读屏都能念出颜色名() {
        awaitHome()
        rule.onNodeWithTag("fab_create").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("tag_create").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("tag_create").performClick()
        rule.waitForIdle()

        val names = (0..7).map { index ->
            rule.onNodeWithTag("tag_color_$index").fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString().orEmpty()
        }
        val missing = names.withIndex().filter { it.value.isBlank() }.map { it.index }
        assertTrue(
            "8 个色点都该有颜色名,缺: $missing —— 读屏用户面对的是八个一模一样的色块",
            missing.isEmpty(),
        )
        assertTrue("颜色名不该互相重复: $names", names.distinct().size == 8)
    }

    /** 主屏就绪的标志是右下角 ＋(其它测试也这么等,首帧就点会扑空) */
    private fun awaitHome() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("fab_create").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    // ── 闸门本体 ──────────────────────────────────────────────────────────

    /**
     * 遍历语义树,挑出"可点 **且** 有面积 **且** 念不出内容"的节点。
     *
     * **边界这一条是必须的**:`boundsInRoot` 全零的节点没有被摆放,触摸和读屏都够不着
     * (闭着的抽屉就会在语义树里留下这种残留节点)。少了这个条件,闸门会对着用户
     * 根本走不到的节点报错,报多了就没人看了。
     */
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.assertNoUnlabelledClickables(
        where: String,
    ) {
        val offenders = mutableListOf<String>()
        walk(rule.onRoot().fetchSemanticsNode(), offenders)
        assertTrue(
            "$where 里有 ${offenders.size} 个可点目标读屏念不出任何内容(NAF):\n" +
                offenders.joinToString("\n") { "  · $it" } +
                "\n修法:给它加 contentDescription,或把子节点的文字合并进来" +
                "(Modifier.semantics(mergeDescendants = true) { contentDescription = … })",
            offenders.isEmpty(),
        )
    }

    private fun walk(node: SemanticsNode, offenders: MutableList<String>) {
        // 边界全零的节点直接跳过:读屏和触摸都够不着它(闭着的抽屉会在语义树里
        // 留下这种没被摆放的残留节点)。对它报警只会让真问题被淹没。
        val reachable = node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f
        if (SemanticsActions.OnClick in node.config && reachable && !node.hasLabelBelow()) {
            offenders += "testTag=${node.testTagOrNull()} role=${node.roleOrNull()} " +
                "bounds=${node.boundsInRoot} size=${node.size}"
        }
        node.children.forEach { walk(it, offenders) }
    }

    /**
     * 自己或任一子孙有没有可朗读内容。
     *
     * 往子孙找是必须的:`IconButton` / `clickable` 的 Box 自身不带文字,
     * 文字在里面的 `Text` 上;uiautomator 呈现时会把子节点文字并到父节点上,
     * 读屏也是这么念的。所以"父节点自己没文字"不等于"读屏念不出来"。
     */
    private fun SemanticsNode.hasLabelBelow(): Boolean {
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        if (!text.isNullOrBlank()) return true
        val spoken = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("")
        if (!spoken.isNullOrBlank()) return true
        return children.any { it.hasLabelBelow() }
    }

    private fun SemanticsNode.testTagOrNull(): String? =
        config.getOrNull(SemanticsProperties.TestTag)

    private fun SemanticsNode.roleOrNull(): String? =
        config.getOrNull(SemanticsProperties.Role)?.toString()

    private fun assertTrue(message: String, condition: Boolean) =
        org.junit.Assert.assertTrue(message, condition)
}
