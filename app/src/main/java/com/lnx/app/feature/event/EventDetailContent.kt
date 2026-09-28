package com.lnx.app.feature.event

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnx.app.core.designsystem.EventColors
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.model.Tag
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 事件详情卡的只读内容(spec §3.6:时间/重复/地点/标签/优先级/备注 + 编辑/删除)。
 * 重复描述的全集在 M4 实现,这里只区分"不重复"。
 */
@Composable
fun EventDetailContent(
    event: Event,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    tags: List<Tag> = emptyList(),
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DetailRow("时间", detailTimeText(event))
        DetailRow("重复", detailRuleText(event.rule))
        event.location?.takeIf { it.isNotBlank() }?.let { DetailRow("地点", it) }
        if (tags.isNotEmpty()) {
            TagRow(tags)
        }
        DetailRow("优先级", event.priority.label)
        event.notes?.takeIf { it.isNotBlank() }?.let { DetailRow("备注", it) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_edit"),
            ) { Text("编辑") }
            Button(
                onClick = { confirmDelete = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_delete"),
            ) { Text("删除") }
        }
    }

    // M2 只有普通事件;重复事件的三选一弹窗(仅本次/本次及以后/全部)属 M4
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除事件") },
            text = { Text("确定删除「${event.title}」吗?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("delete_confirm"),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 标签行(spec §3.6):色点 + 名称,可横向滚动 */
@Composable
private fun TagRow(tags: List<Tag>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "标签",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EventColors.of(tag.colorSlot)),
                    )
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// 应用界面文案是中文(v0.1 不做 i18n):钉住 locale,保证设备语言不影响展示与测试
private val DETAIL_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
private val DETAIL_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

/** 时间行文案:全天显示日期区间(排他存储 → 展示时收回到"含当天"),定时显示起止 */
fun detailTimeText(event: Event): String {
    return if (event.allDay) {
        val firstDay = event.start.toLocalDate()
        val lastDay = event.end.toLocalDate().minusDays(1) // 存储排他,展示含当天
        if (firstDay == lastDay) {
            "${firstDay.format(DETAIL_DATE_FMT)} 全天"
        } else {
            "${firstDay.format(DETAIL_DATE_FMT)} 至 ${lastDay.format(DETAIL_DATE_FMT)} 全天"
        }
    } else {
        val firstDay = event.start.toLocalDate()
        val lastDay = event.end.toLocalDate()
        if (firstDay == lastDay) {
            "${firstDay.format(DETAIL_DATE_FMT)} ${event.start.format(DETAIL_TIME_FMT)} – ${event.end.format(DETAIL_TIME_FMT)}"
        } else {
            "${firstDay.format(DETAIL_DATE_FMT)} ${event.start.format(DETAIL_TIME_FMT)}" +
                " 至 ${lastDay.format(DETAIL_DATE_FMT)} ${event.end.format(DETAIL_TIME_FMT)}"
        }
    }
}

/** 重复规则描述(spec §3.6);M2 数据只会是"不重复",全集描述随 M4 重复引擎提供 */
fun detailRuleText(rule: EventRule): String =
    if (rule.type == RuleType.NONE) "不重复" else "重复"
