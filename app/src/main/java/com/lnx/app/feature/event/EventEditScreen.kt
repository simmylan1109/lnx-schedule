package com.lnx.app.feature.event

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.lnx.app.core.domain.model.Occurrence
import com.lnx.app.core.domain.model.Priority
import com.lnx.app.core.domain.recurrence.EditScope
import com.lnx.app.core.domain.recurrence.RuleDescription
import com.lnx.app.core.notification.NotificationPermission
import com.lnx.app.core.notification.findActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 新建 / 编辑事件(spec §3.5 字段顺序:标题 → 全天 → 起止 → 地点 → 重复 → 提醒 → 标签 → 颜色 → 优先级 → 备注)。
 * 重复事件按作用范围进入:"全部" 走 [eventId];"仅本次"/"本次及以后" 走 [occurrence] + [scope]
 * (此时"重复"区只读显示,单次例外改不了规则)。
 */
@Composable
fun EventEditScreen(
    /** 预填的开始时间(spec §3.5 三入口规则);编辑既有事件时传 null */
    start: LocalDateTime? = null,
    /** 编辑既有事件的 id;新建时传 null。start / eventId 必须恰好给一个 */
    eventId: String? = null,
    /** 编辑重复事件的某一次("仅本次"/"本次及以后"):给 occurrence + scope,二选一配合 eventId */
    occurrence: Occurrence? = null,
    scope: EditScope? = null,
    onClose: () -> Unit,
    onSaved: (overlapTitles: List<String>) -> Unit = {},
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = state.draft
    val activity = LocalContext.current.findActivity()

    // 没有导航图时由调用方显式给定入口;ViewModel 跨多次打开存活,每次进入组合必须重置
    LaunchedEffect(start, eventId, occurrence, scope) {
        when {
            occurrence != null && scope != null -> viewModel.initializeOccurrenceEdit(occurrence, scope)
            eventId != null -> viewModel.initializeEvent(eventId)
            start != null -> viewModel.initialize(start)
        }
    }

    // 通知权限在"用户要提醒"这一刻才问(spec §3.8),触发点有两个:点了提醒档位、
    // 或直接保存了带提醒的事件(草稿默认 15 分钟,不碰提醒行也会走到)。
    // 冷启动就弹会挡住首屏(实测仪器测试全部找不到 Compose 树),也不该在用户
    // 还没搞懂 App 前就要权限。
    LaunchedEffect(state.askNotificationPermission) {
        if (state.askNotificationPermission) {
            if (activity != null) NotificationPermission.request(activity)
            viewModel.consumeNotificationPermissionRequest()
        }
    }

    // 保存成功才关页;索权要排在关闭之前,否则弹窗跟着页面一起消失,用户根本没机会授权
    LaunchedEffect(state.saved) {
        if (!state.saved) return@LaunchedEffect
        onSaved(state.overlapTitles)
        if (state.askNotificationPermission && activity != null) {
            NotificationPermission.request(activity)
            viewModel.consumeNotificationPermissionRequest()
        }
        onClose()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    // 应用是 edge-to-edge:顶栏必须避开状态栏,否则取消/保存会被系统栏盖住点不到
                    .statusBarsPadding()
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

            // 单次例外不能改规则(spec §4.3 裁定;改规则请用"本次及以后"或"全部")
            if (state.editContext?.scope != EditScope.THIS_ONLY) {
                RuleEditorSection(
                    rule = draft.rule,
                    seriesStart = draft.start,
                    onChange = viewModel::setRule,
                )
            } else {
                ReadonlyRow("重复", RuleDescription.of(draft.rule))
            }
            ReminderPicker(
                selected = draft.reminderLeadMinutes,
                onSelect = viewModel::setReminderLead,
            )
            TagPickerSection(
                tags = state.tags,
                selectedIds = state.selectedTagIds,
                onToggle = viewModel::toggleTag,
                onCreate = viewModel::createTag,
                createError = state.tagCreateError,
                onClearError = viewModel::clearTagCreateError,
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

// 应用界面文案是中文(v0.1 不做 i18n):钉住 locale,否则 "E" 会随设备语言变成 Mon/周日 混排
private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val DATETIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 E HH:mm", Locale.CHINA)
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)

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
            .padding(vertical = 14.dp)
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
internal fun DateField(
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
            .padding(vertical = 14.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(date.format(DATE_FMT), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 三个选择器共用的"标签行 + 选项行"结构;选项一律包在 48dp 触达目标里 */
@Composable
private fun ReminderPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("提醒", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, 5, 15, 30, 60).forEach { minutes ->
                ChipOption(
                    text = minutes?.let { "$it 分" } ?: "不提醒",
                    selected = minutes == selected,
                    onClick = { onSelect(minutes) },
                    tag = "reminder_${minutes ?: "none"}",
                )
            }
        }
    }
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("颜色", style = MaterialTheme.typography.bodyMedium)
        // 8 个 48dp 触达点一行放不下:允许横向滑动(固定 8 项,滑动成本很低)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EventColors.list().forEachIndexed { index, color ->
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // 触达区大,涟漪会糊;视觉反馈用选中描边
                        ) { onSelect(index) }
                        .testTag("color_$index"),
                    contentAlignment = Alignment.Center,
                ) {
                    // 视觉仍是 28dp 圆点;选中描边条件挂载(border 0dp 会画 1px 发丝线)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (index == selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityPicker(selected: Priority, onSelect: (Priority) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("优先级", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.entries.forEach { p ->
                ChipOption(
                    text = p.label,
                    selected = p == selected,
                    onClick = { onSelect(p) },
                    tag = "priority_${p.name}",
                )
            }
        }
    }
}

/** 胶囊选项:视觉小巧,触达目标不小于 48dp(Material 无障碍底线) */
@Composable
internal fun ChipOption(text: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
