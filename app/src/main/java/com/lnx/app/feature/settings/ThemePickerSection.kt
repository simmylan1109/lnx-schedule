package com.lnx.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.lnx.app.R
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.core.designsystem.schemeFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/**
 * 引导页自动演示的总闸。**仪器测试里要关掉**(`OnboardingTest` 在 @BeforeClass 置 false):
 * 演示动画与测试注入的滑动手势在同一帧上竞争,会让"滑动后第 4 张卡可达"那条测试
 * 出现"靠演示通过"的虚绿或偶发超时 —— 终审 P2。
 */
object PeekDemoHint {
    var enabled: Boolean = true
}

/**
 * 横向一排 4 张主题卡;设置页和首启引导第 2 页(spec §3.12 ②)共用。
 *
 * **4 张卡(4×112dp + 间距 + 边距 ≈ 524dp)在手机屏(约 392dp)上放不下,第 4 张
 * 「宁静冷色」完整地藏在屏幕外 —— 连一条边都不露**,用户不知道右边还有东西
 * (v0.2 补欠账 ①)。两件事告诉用户"能滑":
 *
 * 1. 底部一排位置圆点,滑到哪儿亮到哪儿;**放得下时不画**(平板上不凭空多一行);
 * 2. [autoPeek] = true(引导页)时,出现后自动滑到底再弹回来一次 —— 亲手演示比
 *    文字提示有效,只动一次。**用户任何时候自己动过,演示整体退出**
 *    (监听 interactionSource 的拖拽起手),包括动画进行中插手的 —— 否则会把
 *    用户正在看的位置硬拽回开头,正是这层保险要防的事。
 */
@Composable
internal fun ThemeCardRow(
    current: ThemeSlot,
    darkMode: DarkMode,
    onPick: (ThemeSlot) -> Unit,
    modifier: Modifier = Modifier,
    autoPeek: Boolean = false,
) {
    val listState = rememberLazyListState()
    Column(modifier = modifier) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().testTag("theme_card_row").padding(vertical = 8.dp),
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
        if (autoPeek && PeekDemoHint.enabled) {
            LaunchedEffect(listState) {
                var touched = false
                launch {
                    listState.interactionSource.interactions.collect { interaction ->
                        if (interaction is DragInteraction.Start) touched = true
                    }
                }
                // 等首帧画完、用户看清第一屏,再动
                delay(700)
                if (touched || listState.isScrollInProgress) return@LaunchedEffect
                listState.animateScrollToItem(ThemeSlot.entries.lastIndex)
                delay(350)
                if (touched || listState.isScrollInProgress) return@LaunchedEffect
                listState.animateScrollToItem(0)
            }
        }
        ThemeRowDots(listState)
    }
}

/** 位置圆点:能滚动才画;当前亮到哪一颗由滚动比例算出(见 [activeDot]) */
@Composable
private fun ThemeRowDots(listState: LazyListState) {
    val count = ThemeSlot.entries.size
    // canScroll* 在首次布局后才可信,期间两边都是 false → 不画,布局稳定后自然出现
    val overflow by remember {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    if (!overflow) return
    val active by remember { derivedStateOf { activeDot(listState, count) } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_row_dots")
            // 圆点是纯装饰,朗读用户靠卡片自己的文字走,不要让它出现在无障碍树里
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val color = if (index == active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .background(color, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** 滚动比例 → 亮第几颗:开头亮 0,滑到底亮最后一颗,中间按比例取整 */
private fun activeDot(listState: LazyListState, count: Int): Int {
    if (!listState.canScrollForward) return count - 1
    val info = listState.layoutInfo
    val first = info.visibleItemsInfo.firstOrNull() ?: return 0
    val scrolledItems = first.index - first.offset.toFloat() / first.size.coerceAtLeast(1)
    val scrollableItems = (info.totalItemsCount - info.visibleItemsInfo.size).coerceAtLeast(1)
    val fraction = (scrolledItems / scrollableItems).coerceIn(0f, 1f)
    return (fraction * (count - 1) + 0.5f).toInt()
}

@Composable
private fun ThemeCard(
    slot: ThemeSlot,
    darkMode: DarkMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 预览要与"点下去实际会得到什么"一致:跟随系统时必须看系统当前是深还是浅,
    // 之前写死 false,深色系统下 4 张卡全是浅色预览、点进去却是深色(终审 P2)
    val dark = when (darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
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
