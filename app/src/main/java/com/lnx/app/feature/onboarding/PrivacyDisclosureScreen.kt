package com.lnx.app.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lnx.app.R

/**
 * 首启隐私披露页(v0.3 A1)。
 *
 * **为什么单独一页、而不是引导页里的一页**:它是"联网之前"必须先给用户看的东西
 * (Android 的 INTERNET 是安装时授予、不弹框,所以系统不会替我们问),所以必须
 * 由 App 自己挡在引导页前面。展示顺序在 [com.lnx.app.MainActivity]:
 * 披露页 → 引导三页 → 日历。
 *
 * **两个按钮都放行,这是有意的**:不同意联网的用户照样要能走完引导、用完整功能 ——
 * 拿"同意"当使用前提在国内合规语境下是明确禁止的(个人信息保护法第十六条),
 * 平台政策同样不允许用功能胁迫同意。所以这里不存在"拒绝"分支的后果:
 * 两个按钮的区别只是"我知道了"和"我知道而且现在不启用同步"。
 *
 * 文案由用户逐字确认(见 values/strings.xml 里的注释):不写"我们不收集任何数据"
 * (客观上确实处理日程数据)、不写"去中心化"、不把证书固定说成端到端加密。
 */
@Composable
fun PrivacyDisclosureScreen(
    /** 两个按钮共用:只表示"已读过",不记录同不同意(以后开同步是设置里的一次明确操作) */
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.privacy_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("privacy_title"),
        )
        Spacer(modifier = Modifier.height(16.dp))
        // 五段条文比屏幕长,必须能滚:内容被截掉一半的隐私说明等于没给用户看。
        // 按钮留在底部固定,免得正文一长就看不到"我知道了"
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .testTag("privacy_body"),
        ) {
            Section(
                heading = stringResource(R.string.privacy_now_heading),
                bodies = listOf(stringResource(R.string.privacy_now_body)),
                testTag = "privacy_section_now",
            )
            Section(
                heading = stringResource(R.string.privacy_sync_heading),
                // 四条要点各自一个语义节点:挤进一个 Text 的话读屏会连读成一长串,
                // 听不出"传输内容/目的/方式/保存期限"是四条并列的信息
                bodies = listOf(
                    stringResource(R.string.privacy_sync_item_content),
                    stringResource(R.string.privacy_sync_item_purpose),
                    stringResource(R.string.privacy_sync_item_how),
                    stringResource(R.string.privacy_sync_item_retention),
                ),
                testTag = "privacy_section_sync",
            )
            Section(
                heading = stringResource(R.string.privacy_no_do_heading),
                bodies = listOf(
                    stringResource(R.string.privacy_no_do_body),
                    stringResource(R.string.privacy_no_do_address),
                ),
                testTag = "privacy_section_no_do",
            )
            Section(
                heading = stringResource(R.string.privacy_rights_heading),
                bodies = listOf(
                    stringResource(R.string.privacy_rights_body),
                    stringResource(R.string.privacy_rights_contact),
                ),
                testTag = "privacy_section_rights",
            )
            Section(
                heading = stringResource(R.string.privacy_self_hosted_heading),
                bodies = listOf(stringResource(R.string.privacy_self_hosted_body)),
                testTag = "privacy_section_self_hosted",
                bottomPadding = true,
            )
        }
        Button(
            onClick = onAcknowledge,
            modifier = Modifier.fillMaxWidth().testTag("privacy_agree"),
        ) { Text(stringResource(R.string.privacy_agree)) }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onAcknowledge,
            modifier = Modifier.fillMaxWidth().testTag("privacy_local_only"),
        ) { Text(stringResource(R.string.privacy_local_only)) }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** 一段"小标题 + 若干行正文",每行一个语义节点(读屏逐条念,不连读成一长串) */
@Composable
private fun Section(
    heading: String,
    bodies: List<String>,
    testTag: String,
    bottomPadding: Boolean = false,
) {
    Column(modifier = Modifier.padding(bottom = if (bottomPadding) 24.dp else 12.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(testTag),
        )
        Spacer(modifier = Modifier.height(4.dp))
        bodies.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}