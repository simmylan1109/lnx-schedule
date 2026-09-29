package com.lnx.app.feature.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lnx.app.core.designsystem.EventColors
import com.lnx.app.core.domain.model.Tag

/**
 * 编辑器标签选择(spec §3.5 字段序的"标签"位;§3.10 多对多、颜色取 8 色位)。
 * 已有标签 = 多选 chips;「新建标签」对话框 = 名称 + 8 色位。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerSection(
    tags: List<Tag>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onCreate: suspend (name: String, colorSlot: Int) -> Boolean,
    createError: String? = null,
    onClearError: () -> Unit = {},
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("标签", style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                TagChip(
                    tag = tag,
                    selected = tag.id in selectedIds,
                    onClick = { onToggle(tag.id) },
                )
            }
            // 新建入口:空清单时也能建(spec §3.10 标签全部由用户创建)
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        onClearError()
                        showCreateDialog = true
                    }
                    .padding(horizontal = 12.dp)
                    .testTag("tag_create"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "＋ 新建",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateTagDialog(
            createError = createError,
            onConfirm = onCreate,
            onDismiss = {
                onClearError()
                showCreateDialog = false
            },
            onCreated = { showCreateDialog = false },
        )
    }
}

/** 胶囊 = 色点 + 名称;触达不小于 48dp(M2 ChipOption 同款约定) */
@Composable
private fun TagChip(tag: Tag, selected: Boolean, onClick: () -> Unit) {
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
            .testTag("tag_chip_${tag.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(EventColors.of(tag.colorSlot)),
            )
            Text(
                text = tag.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateTagDialog(
    createError: String?,
    onConfirm: suspend (String, Int) -> Boolean,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var colorSlot by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tag_name_field"),
                )
                // 同 ColorPicker:8 个 48dp 触达点超过对话框正文宽度,必须允许横向滑动,
                // 否则第 8 个色点被挤到屏幕外点不到
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
                                    indication = null,
                                ) { colorSlot = index }
                                .testTag("tag_color_$index"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (index == colorSlot) {
                                            Modifier
                                                .size(34.dp)
                                                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
            if (createError != null) {
                Text(
                    text = createError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("tag_create_error"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 只有真建成才关窗;重名等失败留在窗内让用户改名字
                    if (name.isNotBlank()) scope.launch {
                        if (onConfirm(name.trim(), colorSlot)) onCreated()
                    }
                },
                modifier = Modifier.testTag("tag_create_confirm"),
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
