package com.lnx.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lnx.app.R
import com.lnx.app.core.designsystem.LocalLnxTheme
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.designsystem.headerContentColor
import com.lnx.app.feature.calendar.ViewMode

@Composable
fun ViewModeTabs(
    current: ViewMode,
    onSelect: (ViewMode) -> Unit,
) {
    // 主题 4 头部是渐变:Tab 不能再用不透明的 surface 药丸(浅色药丸压在蓝紫渐变上很脏),
    // 改成"未选中透明 + 选中半透明白"。其余三套维持原来的实底药丸。
    val serene = LocalLnxTheme.current.slot == ThemeSlot.SERENE
    val contentColor = headerContentColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ViewMode.entries.forEach { mode ->
            val selected = mode == current
            val background = when {
                serene && selected -> Color.White.copy(alpha = 0.28f)
                serene -> Color.Transparent
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
            Text(
                text = stringResource(
                    when (mode) {
                        ViewMode.DAY -> R.string.calendar_view_day
                        ViewMode.WEEK -> R.string.calendar_view_week
                        ViewMode.MONTH -> R.string.calendar_view_month
                    },
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(background)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .testTag("tab_${mode.name}"),
                style = MaterialTheme.typography.labelLarge,
                color = if (serene) contentColor
                else if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}