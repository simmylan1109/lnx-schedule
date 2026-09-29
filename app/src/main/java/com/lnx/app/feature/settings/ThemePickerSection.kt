package com.lnx.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.lnx.app.R
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.designsystem.schemeFor

/**
 * 外观组的 4 张主题卡(spec §3.11 ①):点一下即换,没有保存按钮,自动记住。
 * 卡片上直接铺该主题自己的色板,比只写个名字好认。
 */
@Composable
internal fun ThemePickerSection(
    current: ThemeSlot,
    darkMode: DarkMode,
    onPick: (ThemeSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionTitle(stringResource(R.string.settings_section_appearance), "settings_section_appearance", modifier)
    ThemeCardRow(current = current, darkMode = darkMode, onPick = onPick)
}

/** 横向一排 4 张主题卡;设置页和首启引导第 2 页(spec §3.12 ②)共用 */
@Composable
internal fun ThemeCardRow(
    current: ThemeSlot,
    darkMode: DarkMode,
    onPick: (ThemeSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(ThemeSlot.entries.toList()) { slot ->
            ThemeCard(
                slot = slot,
                darkMode = darkMode,
                selected = slot == current,
                onClick = { onPick(slot) },
            )
        }
    }
}

@Composable
private fun ThemeCard(
    slot: ThemeSlot,
    darkMode: DarkMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.FOLLOW_SYSTEM -> false
    }
    val scheme = schemeFor(slot, dark)
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick)
            .testTag("theme_card_${slot.name}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(scheme.background, RoundedCornerShape(12.dp))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ColorDot(scheme.primary)
                ColorDot(scheme.secondary)
                ColorDot(scheme.tertiary)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .size(width = 40.dp, height = 6.dp)
                    .background(scheme.onBackground, RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = stringResource(slot.labelRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(color, RoundedCornerShape(7.dp)),
    )
}

internal fun ThemeSlot.labelRes(): Int = when (this) {
    ThemeSlot.MATERIAL_YOU -> R.string.theme_material_you
    ThemeSlot.PAPER -> R.string.theme_paper
    ThemeSlot.WARM -> R.string.theme_warm
    ThemeSlot.SERENE -> R.string.theme_serene
}
