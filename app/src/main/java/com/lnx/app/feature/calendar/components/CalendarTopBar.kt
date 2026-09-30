package com.lnx.app.feature.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnx.app.R
import com.lnx.app.core.designsystem.LocalLnxTheme
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.designsystem.headerContentColor

@Composable
fun CalendarTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onTodayClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    // spec §5.2 主题 4「日期数字超大」+ §5.3「大日期数字 28–32sp」:
    // 顶栏标题就是那个"大日期数字",只在宁静冷色放大,其余三套保持 titleMedium。
    val serene = LocalLnxTheme.current.slot == ThemeSlot.SERENE
    val contentColor = headerContentColor()
    val titleStyle = if (serene) {
        MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 34.sp)
    } else {
        MaterialTheme.typography.titleMedium
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("top_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onMenuClick, modifier = Modifier.testTag("menu_button")) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = stringResource(R.string.calendar_menu),
                modifier = Modifier.padding(8.dp),
                tint = contentColor,
            )
        }
        Text(
            text = title,
            style = titleStyle,
            color = contentColor,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onTodayClick,
                modifier = Modifier.testTag("today_button"),
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
            ) {
                Text(stringResource(R.string.calendar_today))
            }
            IconButton(onClick = onSearchClick, modifier = Modifier.testTag("search_button")) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.calendar_search),
                    modifier = Modifier.padding(8.dp),
                    tint = contentColor,
                )
            }
        }
    }
}