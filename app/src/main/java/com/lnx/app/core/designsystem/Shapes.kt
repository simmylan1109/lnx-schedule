package com.lnx.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 每套主题的圆角(spec §5.2):
 * - 主题 1 Material You:**24dp,全套最大**(卡片式);
 * - 主题 2 极致留白:**8–12dp**,接近直角,配合无边框无阴影、只靠留白分隔;
 * - 主题 3 暖橙活力:16dp,居中;
 * - 主题 4 宁静冷色:20–24dp。spec 里同一条还写了"柔和投影",M7 裁定不做 ——
 *   头部渐变已经给出层次,再叠投影反而脏(那条裁定记在 SPEC §5.2 的备注里)。
 *
 * Material3 的 `Shapes` 只有 extraSmall/small/medium/large/extraLarge 五档,
 * 主题 2 的 8 与 12 分别落在 extraSmall 与 small 上,避免为了塞两档去动组件。
 */
internal fun shapesFor(slot: ThemeSlot): Shapes = when (slot) {
    ThemeSlot.MATERIAL_YOU -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
    ThemeSlot.PAPER -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(12.dp),
    )
    ThemeSlot.WARM -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )
    ThemeSlot.SERENE -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )
}
