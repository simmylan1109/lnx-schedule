package com.lnx.app.feature.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lnx.app.core.designsystem.EventColors
import com.lnx.app.core.domain.model.Priority
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 新建 / 编辑事件(spec §3.5 字段顺序:标题 → 全天 → 起止 → 地点 → 重复 → 提醒 → 标签 → 颜色 → 优先级 → 备注)。
 * 标签属 M3、重复规则属 M4,本里程碑对应位置显示只读占位。
 */
@Composable
fun EventEditScreen(
    /** 预填的开始时间(spec §3.5 三入口规则);编辑既有事件时传 null */
    start: LocalDateTime? = null,
    onClose: () -> Unit,
    onSaved: (overlapTitles: List<String>) -> Unit = {},
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = state.draft

    // 没有导航图时由调用方给定预填时间
    LaunchedEffect(start) {
        start?.let(viewModel::initialize)
    }

    // 只有真的保存成功才关闭:校验不过时留在页面并显示错误(否则用户会丢失输入)
    LaunchedEffect(state.saved) {
        if (state.saved) {
            onSaved(state.overlapTitles)
            onClose()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text("取消") }
                Text(
                    text = if (state.isEditing) "编辑事件" else "新建事件",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(
                    onClick = { viewModel.save() },
                    modifier = Modifier.testTag("save_button"),
                ) { Text("保存") }
            }
        },
    ) { padding ->
        if (draft == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::setTitle,
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_title"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("全天事件")
                Switch(
                    checked = draft.allDay,
                    onCheckedChange = viewModel::toggleAllDay,
                    modifier = Modifier.testTag("switch_allday"),
                )
            }

            if (draft.allDay) {
                DateField(
                    label = "开始日期",
                    date = draft.start.toLocalDate(),
                    onPick = { viewModel.setStart(it.atStartOfDay()) },
                    tag = "field_start_date",
                )
                DateField(
                    label = "结束日期",
                    date = draft.end.toLocalDate().minusDays(1),
                    onPick = { picked ->
                        // 结束日期排他:存次日零点
                        viewModel.setEnd(picked.plusDays(1).atStartOfDay())
                    },
                    tag = "field_end_date",
                )
            } else {
                DateTimeField(
                    label = "开始",
                    value = draft.start,
                    onPickDateTime = viewModel::setStart,
                    tag = "field_start",
                )
                DateTimeField(
                    label = "结束",
                    value = draft.end,
                    onPickDateTime = viewModel::setEnd,
                    tag = "field_end",
                )
            }

            OutlinedTextField(
                value = draft.location,
                onValueChange = viewModel::setLocation,
                label = { Text("地点(可选)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_location"),
            )

            ReadonlyRow("重复", "不重复(M4 起可设置)")
            ReadonlyRow("标签", "M3 起可添加")
            ReminderPicker(
                selected = draft.reminderLeadMinutes,
                onSelect = viewModel::setReminderLead,
            )
            ColorPicker(
                selected = draft.colorSlot,
                onSelect = viewModel::setColorSlot,
            )
            PriorityPicker(
                selected = draft.priority,
                onSelect = viewModel::setPriority,
            )

            OutlinedTextField(
                value = draft.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("备注") },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_notes"),
            )

            if (state.errors.contains(ValidationError.TITLE_REQUIRED)) {
                Text("请填写标题", color = MaterialTheme.colorScheme.error)
            }
            if (state.errors.contains(ValidationError.END_NOT_AFTER_START)) {
                Text("结束时间必须晚于开始时间", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATETIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 E HH:mm")
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 E")

@Composable
private fun ReadonlyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DateTimeField(
    label: String,
    value: LocalDateTime,
    onPickDateTime: (LocalDateTime) -> Unit,
    tag: String,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable {
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        TimePickerDialog(
                            context,
                            { _, h, min -> onPickDateTime(value.toLocalDate().atTime(h, min)) },
                            value.hour,
                            value.minute,
                            true,
                        ).show()
                    },
                    value.year,
                    value.monthValue - 1,
                    value.dayOfMonth,
                ).show()
            }
            .padding(vertical = 12.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value.format(DATETIME_FMT),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DateField(
    label: String,
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    tag: String,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable {
                DatePickerDialog(
                    context,
                    { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth,
                ).show()
            }
            .padding(vertical = 12.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(date.format(DATE_FMT), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReminderPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("提醒", style = MaterialTheme.typography.bodyMedium)
        listOf(null, 5, 15, 30, 60).forEach { minutes ->
            val label = minutes?.let { "$it 分" } ?: "不提醒"
            val isSelected = minutes == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    )
                    .clickable { onSelect(minutes) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("reminder_${minutes ?: "none"}"),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    val slots = EventColors.list() // 8 个色位(spec §5.4)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("颜色", style = MaterialTheme.typography.bodyMedium)
        slots.forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (index == selected) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(index) }
                    .testTag("color_$index"),
            )
        }
    }
}

@Composable
private fun PriorityPicker(selected: Priority, onSelect: (Priority) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("优先级", style = MaterialTheme.typography.bodyMedium)
        Priority.entries.forEach { p ->
            val isSelected = p == selected
            Text(
                text = p.label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    )
                    .clickable { onSelect(p) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("priority_${p.name}"),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
