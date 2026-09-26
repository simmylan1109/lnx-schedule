package com.lnx.app.core.designsystem

/** spec §5.1:4 套主题;M1 仅实现 MATERIAL_YOU,其余槽位 M6 落地 */
enum class ThemeSlot(val label: String) {
    MATERIAL_YOU("Material You"),
    PAPER("极致留白"),
    WARM("暖橙活力"),
    SERENE("宁静冷色"),
}
