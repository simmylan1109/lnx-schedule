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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lnx.app.R
import com.lnx.app.feature.calendar.ViewMode

@Composable
fun ViewModeTabs(
    current: ViewMode,
    onSelect: (ViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ViewMode.entries.forEach { mode ->
            val selected = mode == current
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
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .testTag("tab_${mode.name}"),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}