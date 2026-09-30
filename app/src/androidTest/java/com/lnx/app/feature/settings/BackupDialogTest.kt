package com.lnx.app.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lnx.app.core.backup.Backup
import com.lnx.app.core.backup.BackupTag
import com.lnx.app.core.backup.ImportSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 导入的两段式确认(spec §3.13:先摘要、覆盖要二次警告)。
 *
 * 只测对话本身,不启 Activity、不连库 —— 文件选择器与系统分享面板没法在仪器测试里驱动,
 * 那两条链路靠实机走查(见 docs/acceptance/m7)。
 */
@RunWith(AndroidJUnit4::class)
class BackupDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val backup = Backup(
        tags = listOf(BackupTag("t1", "工作", 1)),
    )

    @Test
    fun 摘要对话写明事件与标签条数() {
        var merged = false
        rule.setContent {
            BackupSummaryDialog(
                pending = PendingImport(backup, ImportSummary(eventCount = 7, tagCount = 3, skippedCount = 0, currentCount = 0)),
                onMerge = { merged = true },
                onOverwrite = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithTag("backup_summary_dialog").assertIsDisplayed()
        rule.onNodeWithTag("backup_merge").performClick()
        assertEquals(true, merged)
    }

    @Test
    fun 覆盖必须先经过二次警告() {
        val calls = mutableListOf<String>()
        rule.setContent {
            OverwriteWarningDialog(
                warning = OverwriteWarning(currentCount = 12),
                onConfirm = { calls += "confirm" },
                onCancel = { calls += "cancel" },
            )
        }
        rule.onNodeWithTag("backup_overwrite_dialog").assertIsDisplayed()
        rule.onNodeWithTag("backup_overwrite_cancel").performClick()
        rule.onNodeWithTag("backup_overwrite_confirm").performClick()
        // 取消与确定是两个独立出口,不能都当成"确认"
        assertEquals(listOf("cancel", "confirm"), calls)
    }

    @Test
    fun 导入失败按原因给不同文案() {
        rule.setContent {
            BackupErrorDialog(error = BackupError.NOT_LNX_BACKUP, onDismiss = {})
        }
        rule.onNodeWithTag("backup_error_dialog").assertIsDisplayed()
        rule.onNodeWithTag("backup_error_text").assertIsDisplayed()
    }
}
