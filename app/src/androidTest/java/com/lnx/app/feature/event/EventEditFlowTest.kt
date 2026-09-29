package com.lnx.app.feature.event

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.MainActivity
import com.lnx.app.core.database.dao.EventDao
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
 * 端到端:新建事件(spec §3.5 三入口之一 —— ＋ 悬浮按钮),验证
 * 编辑页预填 → 保存 → 事件真的出现在周视图 → 数据落到数据库。
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EventEditFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var dao: EventDao

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `加号入口建事件并出现在周视图`() {
        rule.onNodeWithTag("fab_create").performClick()
        // 新建草稿要等设置读完才建成(默认提醒档位来自设置),标题栏在才说明可操作
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("field_title").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()

        // 编辑页:标题可输入,开始时间已预填
        rule.onNodeWithTag("field_title").performTextInput("端到端测试会")
        rule.onNodeWithTag("save_button").performClick()
        rule.waitForIdle()

        // 数据落库
        val stored = runBlocking { dao.allOnce() }
        assertEquals(1, stored.size)
        assertEquals("端到端测试会", stored.first().title)

        // 周视图里出现
        rule.onAllNodesWithText("端到端测试会").onFirst().assertIsDisplayed()
    }

    @Test
    fun `空标题保存被拦下且不落库`() {
        rule.onNodeWithTag("fab_create").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("field_title").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("save_button").performClick()
        rule.waitForIdle()
        assertTrue(runBlocking { dao.allOnce() }.isEmpty())
        // 编辑页仍在,给出校验提示(提示可能位于滚动区下方,故断言存在即可)
        rule.onAllNodesWithText("请填写标题").onFirst().assertExists()
    }
}
