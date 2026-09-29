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
import com.lnx.app.core.domain.recurrence.EditScope
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 事件详情卡的只读内容(spec §3.6:时间/重复/地点/标签/优先级/备注 + 编辑/删除)。
 * 重复事件的编辑/删除弹三选一(spec §3.6/§3.7/§4.4:仅本次 / 本次及以后 / 全部);
 * 单次事件走普通确认弹窗。作用范围由调用方落库(见 RecurrenceEditHandler)。
 */
@Composable
fun EventDetailContent(
    event: Event,
    onEdit: (EditScope) -> Unit,
    onDelete: (EditScope) -> Unit,
    tags: List<Tag> = emptyList(),
) {
    var confirmDelete by remember { mutableStateOf(false) }
    // 重复事件:按钮先选作用范围(编辑/删除共用一个弹窗,靠 pendingAction 区分)
    var pendingAction by remember { mutableStateOf<DetailAction?>(null) }

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
                onClick = {
                    if (event.isRecurring) pendingAction = DetailAction.EDIT else onEdit(EditScope.ALL)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_edit"),
            ) { Text("编辑") }
            Button(
                onClick = {
                    if (event.isRecurring) pendingAction = DetailAction.DELETE else confirmDelete = true
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_delete"),
            ) { Text("删除") }
        }
    }

    // 非重复事件:普通删除确认(spec §3.6)
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除事件") },
            text = { Text("确定删除「${event.title}」吗?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(EditScope.ALL)
                    },
                    modifier = Modifier.testTag("delete_confirm"),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }

    // 重复事件:三选一作用范围(spec §3.6/§4.4)
    val action = pendingAction
    if (action != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (action == DetailAction.EDIT) "修改范围" else "删除范围") },
            text = { Text("这个日程重复发生,请选择作用范围:") },
            confirmButton = {
                // 三个作用范围并排;AlertDialog 只有两个按钮槽,三选一塞进 confirm 槽
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScopeButton("仅本次", EditScope.THIS_ONLY) {
                        pendingAction = null
                        if (action == DetailAction.EDIT) onEdit(EditScope.THIS_ONLY) else onDelete(EditScope.THIS_ONLY)
                    }
                    ScopeButton("本次及以后", EditScope.THIS_AND_FUTURE) {
                        pendingAction = null
                        if (action == DetailAction.EDIT) {
                            onEdit(EditScope.THIS_AND_FUTURE)
                        } else {
                            onDelete(EditScope.THIS_AND_FUTURE)
                        }
                    }
                    ScopeButton("全部", EditScope.ALL) {
                        pendingAction = null
                        if (action == DetailAction.EDIT) onEdit(EditScope.ALL) else onDelete(EditScope.ALL)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ScopeButton(label: String, scope: EditScope, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag("scope_${scope.name}")) { Text(label) }
}

private val Event.isRecurring: Boolean
    get() = rule.type != com.lnx.app.core.domain.model.RuleType.NONE

private enum class DetailAction { EDIT, DELETE }

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

/** 重复规则描述(spec §3.6):M4 起是完整中文描述,与编辑器折叠行同一份文案 */
fun detailRuleText(rule: EventRule): String = com.lnx.app.core.domain.recurrence.RuleDescription.of(rule)
