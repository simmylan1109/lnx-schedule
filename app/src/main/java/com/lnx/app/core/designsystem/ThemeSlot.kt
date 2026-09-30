package com.lnx.app.core.designsystem

/**
 * spec §5.1:4 套主题;M1 仅实现 MATERIAL_YOU,其余槽位 M6 落地。
 *
 * **枚举里不带展示名** —— 主题名是界面文案,归 `strings.xml`(中英各一套),
 * 由 `ThemeSlot.labelRes()` 映射(spec §10)。
 */
enum class ThemeSlot {
    MATERIAL_YOU,
    PAPER,
    WARM,
    SERENE,
}
