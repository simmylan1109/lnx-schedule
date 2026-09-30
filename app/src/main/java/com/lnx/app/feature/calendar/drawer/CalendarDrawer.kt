package com.lnx.app.feature.calendar.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lnx.app.R
import com.lnx.app.core.designsystem.EventColors
import com.lnx.app.core.domain.model.Tag

/**
 * 抽屉(spec §3.10):顶部 lnx 标识;中部标签筛选清单(色点 + 名称 + 勾选,
 * 勾掉即隐藏该标签的事件)与「未分类」行;底部「设置」入口(M6 起可点,进设置页)。
 */
@Composable
fun CalendarDrawer(
    tags: List<Tag>,
    hiddenTagIds: Set<String>,
    hideUntagged: Boolean,
    onToggleTag: (String) -> Unit,
    onToggleUntagged: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 顶部:lnx 标识(spec §3.10)
                Text(
                    text = "lnx",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                )
                HorizontalDivider()

                Text(
                    text = stringResource(R.string.drawer_filter),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                tags.forEach { tag ->
                    FilterRow(
                        label = tag.name,
                        checked = tag.id !in hiddenTagIds,
                        dotColor = EventColors.of(tag.colorSlot),
                        onToggle = { onToggleTag(tag.id) },
                        modifier = Modifier.testTag("drawer_tag_${tag.id}"),
                    )
                }
                FilterRow(
                    label = stringResource(R.string.drawer_untagged),
                    checked = !hideUntagged,
                    dotColor = null,
                    onToggle = onToggleUntagged,
                    modifier = Modifier.testTag("drawer_untagged"),
                )
            }

            // 底部:设置入口(spec §3.10)。
            // 清单滚动、设置贴底:标签再多也把"设置"压在抽屉最下面。
            HorizontalDivider()
            Text(
                text = stringResource(R.string.drawer_settings),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .testTag("drawer_settings"),
            )
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    checked: Boolean,
    dotColor: androidx.compose.ui.graphics.Color?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 整行是复选框的本体(M9),CheckBox 自己不再单独成为可聚焦目标。
    // 原来 Row 挂 clickable、末尾又挂一个能点的 Checkbox,于是语义树里出现**两个**可点节点,
    // 而那个 Checkbox 什么标签都没有 —— 读屏用户滑到它只会听到"复选框,已勾选",
    // 根本不知道勾的是哪个标签。合并成 toggleable 之后只剩一个节点:"未分类,复选框,已勾选"。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(12.dp)
                .then(
                    if (dotColor != null) {
                        Modifier.clip(CircleShape).background(dotColor)
                    } else {
                        Modifier
                    }
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        Checkbox(checked = checked, onCheckedChange = null, enabled = true)
    }
}
