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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lnx.app.R
import com.lnx.app.core.common.LnxLocale
import com.lnx.app.core.common.LocalLnxLocale
import com.lnx.app.core.designsystem.EventColors
import com.lnx.app.core.domain.model.Event
import com.lnx.app.core.domain.model.EventRule
import com.lnx.app.core.domain.model.RuleType
import com.lnx.app.core.domain.model.Tag
import com.lnx.app.core.domain.recurrence.EditScope
import com.lnx.app.core.domain.recurrence.RuleDescription
import java.time.LocalDate
import java.time.LocalTime
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
    val locale = LocalLnxLocale.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DetailRow(stringResource(R.string.detail_time), detailTimeText(event, locale))
        DetailRow(stringResource(R.string.detail_repeat), detailRuleText(event.rule, locale))
        event.location?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(R.string.detail_location), it) }
        if (tags.isNotEmpty()) {
            TagRow(tags)
        }
        DetailRow(stringResource(R.string.detail_priority), event.priority.label)
        event.notes?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(R.string.detail_notes), it) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    if (event.isRecurring) pendingAction = DetailAction.EDIT else onEdit(EditScope.ALL)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_edit"),
            ) { Text(stringResource(R.string.detail_edit)) }
            Button(
                onClick = {
                    if (event.isRecurring) pendingAction = DetailAction.DELETE else confirmDelete = true
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail_delete"),
            ) { Text(stringResource(R.string.detail_delete)) }
        }
    }

    // 非重复事件:普通删除确认(spec §3.6)
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_confirm, event.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(EditScope.ALL)
                    },
                    modifier = Modifier.testTag("delete_confirm"),
                ) { Text(stringResource(R.string.detail_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // 重复事件:三选一作用范围(spec §3.6/§4.4)
    val action = pendingAction
    if (action != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(
                    if (action == DetailAction.EDIT) {
                        stringResource(R.string.detail_scope_edit)
                    } else {
                        stringResource(R.string.detail_scope_delete)
                    },
                )
            },
            text = { Text(stringResource(R.string.detail_scope_prompt)) },
            confirmButton = {
                // 三个作用范围并排;AlertDialog 只有两个按钮槽,三选一塞进 confirm 槽
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScopeButton(stringResource(R.string.detail_scope_this_only), EditScope.THIS_ONLY) {
                        pendingAction = null
                        if (action == DetailAction.EDIT) onEdit(EditScope.THIS_ONLY) else onDelete(EditScope.THIS_ONLY)
                    }
                    ScopeButton(stringResource(R.string.detail_scope_this_and_future), EditScope.THIS_AND_FUTURE) {
                        pendingAction = null
                        if (action == DetailAction.EDIT) {
                            onEdit(EditScope.THIS_AND_FUTURE)
                        } else {
                            onDelete(EditScope.THIS_AND_FUTURE)
                        }
                    }
                    ScopeButton(stringResource(R.string.detail_scope_all), EditScope.ALL) {
                        pendingAction = null
                        if (action == DetailAction.EDIT) onEdit(EditScope.ALL) else onDelete(EditScope.ALL)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text(stringResource(R.string.action_cancel)) }
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
            text = stringResource(R.string.detail_tags),
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

/**
 * 时间行的**形状**(纯函数,可 JVM 单测):全天显示日期区间(排他存储 → 展示时收回到
 * "含当天"),定时显示起止、同日不重复日期。
 *
 * 这里只决定"用哪种句式、显示哪几天/几点",**不拼字符串** —— 句子在 `strings.xml` 里,
 * 日期格式在 [LnxLocale] 里,都由 Composable 那层按当前语言取。
 * (早先这里直接返回拼好的中文,换语言就露馅;那样也测不了。)
 */
data class DetailTimeParts(
    val kind: Kind,
    /** 要显示的日期:全天 1 或 2 天,定时 1 或 2 天 */
    val dates: List<LocalDate>,
    /** 只有定时事件才有 */
    val times: List<LocalTime> = emptyList(),
) {
    enum class Kind { ALL_DAY_SINGLE, ALL_DAY_RANGE, TIMED_SAME_DAY, TIMED_RANGE }
}

fun detailTimeParts(event: Event): DetailTimeParts {
    val firstDay = event.start.toLocalDate()
    return if (event.allDay) {
        val lastDay = event.end.toLocalDate().minusDays(1) // 存储排他,展示含当天
        DetailTimeParts(
            kind = if (firstDay == lastDay) {
                DetailTimeParts.Kind.ALL_DAY_SINGLE
            } else {
                DetailTimeParts.Kind.ALL_DAY_RANGE
            },
            dates = listOf(firstDay, lastDay).distinct(),
        )
    } else {
        val lastDay = event.end.toLocalDate()
        // 同日不重复日期,跨天才把结束那天的日期也带上
        DetailTimeParts(
            kind = if (firstDay == lastDay) {
                DetailTimeParts.Kind.TIMED_SAME_DAY
            } else {
                DetailTimeParts.Kind.TIMED_RANGE
            },
            dates = listOf(firstDay, lastDay).distinct(),
            times = listOf(event.start.toLocalTime(), event.end.toLocalTime()),
        )
    }
}

/** 时间行文案:按 [detailTimeParts] 决定的形状取对应句子 */
@Composable
fun detailTimeText(event: Event, locale: Locale): String {
    val parts = detailTimeParts(event)
    val days = parts.dates.map { LnxLocale.monthDay(it, locale) }
    val dated = parts.dates.map { LnxLocale.dateWithWeekday(it, locale) }
    return when (parts.kind) {
        DetailTimeParts.Kind.ALL_DAY_SINGLE ->
            stringResource(R.string.detail_all_day_single, days.single())

        DetailTimeParts.Kind.ALL_DAY_RANGE ->
            stringResource(R.string.detail_all_day_range, days[0], days[1])

        DetailTimeParts.Kind.TIMED_SAME_DAY -> {
            val times = parts.times.map { LnxLocale.time(it, locale) }
            stringResource(R.string.detail_time_range, "${dated.single()} ${times[0]}", times[1])
        }

        DetailTimeParts.Kind.TIMED_RANGE -> {
            val times = parts.times.map { LnxLocale.time(it, locale) }
            stringResource(
                R.string.detail_time_range,
                "${dated[0]} ${times[0]}",
                "${dated[1]} ${times[1]}",
            )
        }
    }
}

/** 重复规则描述(spec §3.6):M4 起是完整本地化描述,与编辑器折叠行同一份文案 */
fun detailRuleText(rule: EventRule, locale: Locale): String = RuleDescription.of(rule, locale)
