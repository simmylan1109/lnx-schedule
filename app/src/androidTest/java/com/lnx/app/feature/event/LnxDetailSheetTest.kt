package com.lnx.app.feature.event

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.LnxTheme
import com.lnx.app.core.designsystem.ThemeSlot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LnxDetailSheetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `visible为true时展示标题与内容`() {
        rule.setContent {
            LnxTheme(ThemeSlot.MATERIAL_YOU, DarkMode.LIGHT) {
                LnxDetailSheet(visible = true, onDismiss = {}, title = "测试详情") {
                    Text("测试内容行")
                }
            }
        }
        rule.onNodeWithText("测试详情").assertExists()
        rule.onNodeWithText("测试内容行").assertExists()
    }
}
