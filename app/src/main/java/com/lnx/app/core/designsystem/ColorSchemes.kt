package com.lnx.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8DEF8),
    onPrimaryContainer = Color(0xFF1D192B),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4A4458),
    onPrimaryContainer = Color(0xFFE8DDFF),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DDFF),
    background = Color(0xFF1D1B20),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF36343B),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF948F99),
    error = Color(0xFFF2B8B5),
)

fun materialYouLightScheme(): ColorScheme = LightScheme
fun materialYouDarkScheme(): ColorScheme = DarkScheme

// —— 主题 2 极致留白(Notion 风:白底、无边框无阴影,只有一抹蓝) ——

/**
 * spec §5.2 给的强调色是 `#2383E2`,但它配白字只有 3.9:1,达不到 §5.3 要求的 AA。
 * 裁定:**同一色相压深到 `#1C6FD0`**(4.9:1)当 `primary`;spec 原色仍作强调用途
 * (事件色位 4 就是它,彩点/强调线不受影响)。
 */
private val PaperLight = lightColorScheme(
    primary = Color(0xFF1C6FD0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F0FC),
    onPrimaryContainer = Color(0xFF0A2E52),
    secondary = Color(0xFF5F5E5B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F1EF),
    onSecondaryContainer = Color(0xFF37352F),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF37352F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF37352F),
    surfaceVariant = Color(0xFFF7F7F5),
    // spec 辅助文字是 #787774,白底上实测 4.48:1,差 0.02 够不着 AA;同色系压深一档到 5.1:1
    onSurfaceVariant = Color(0xFF6F6D6A),
    outline = Color(0xFFD9D9D5),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val PaperDark = darkColorScheme(
    primary = Color(0xFF4D9CE4),
    onPrimary = Color(0xFF10233A),
    primaryContainer = Color(0xFF1E3A5C),
    onPrimaryContainer = Color(0xFFCFE3FA),
    secondary = Color(0xFFC9C9C5),
    onSecondary = Color(0xFF2A2A28),
    secondaryContainer = Color(0xFF2A2A28),
    onSecondaryContainer = Color(0xFFE1E1E1),
    background = Color(0xFF191919),
    onBackground = Color(0xFFE1E1E1),
    surface = Color(0xFF191919),
    onSurface = Color(0xFFE1E1E1),
    surfaceVariant = Color(0xFF232322),
    onSurfaceVariant = Color(0xFFA8A8A4),
    outline = Color(0xFF4A4A48),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

fun paperLightScheme(): ColorScheme = PaperLight
fun paperDarkScheme(): ColorScheme = PaperDark

// —— 主题 3 暖橙活力(TickTick 风:橙红主色、暖白底) ——

/**
 * 橙红 `#F86B3D` 配白字只有 2.9:1,配深字 5.9:1 —— 所以 `onPrimary` 用深棕而非白,
 * 这也是暖橙主题的常态观感(白字压在高饱和橙上本就刺眼)。
 */
private val WarmLight = lightColorScheme(
    primary = Color(0xFFF86B3D),
    onPrimary = Color(0xFF2B1508),
    primaryContainer = Color(0xFFFFE3D6),
    onPrimaryContainer = Color(0xFF5A1F08),
    secondary = Color(0xFFA2542C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFEEDD),
    onSecondaryContainer = Color(0xFF4A2A16),
    background = Color(0xFFFFFAF5),
    onBackground = Color(0xFF2E2320),
    surface = Color(0xFFFFFAF5),
    onSurface = Color(0xFF2E2320),
    surfaceVariant = Color(0xFFF5EAE2),
    onSurfaceVariant = Color(0xFF6B4A3A),
    outline = Color(0xFFE0CFC2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val WarmDark = darkColorScheme(
    primary = Color(0xFFFF7A50),
    onPrimary = Color(0xFF2B0F05),
    primaryContainer = Color(0xFF4A2313),
    onPrimaryContainer = Color(0xFFFFD9CC),
    secondary = Color(0xFFE8B79E),
    onSecondary = Color(0xFF3A2A22),
    secondaryContainer = Color(0xFF3A2A22),
    onSecondaryContainer = Color(0xFFF5E3D9),
    background = Color(0xFF1A1A1A),
    onBackground = Color(0xFFF5EDE8),
    surface = Color(0xFF1A1A1A),
    onSurface = Color(0xFFF5EDE8),
    surfaceVariant = Color(0xFF2C2724),
    onSurfaceVariant = Color(0xFFC4B3A8),
    outline = Color(0xFF5A4A40),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

fun warmLightScheme(): ColorScheme = WarmLight
fun warmDarkScheme(): ColorScheme = WarmDark

// —— 主题 4 宁静冷色(云白/深夜蓝,顶栏走渐变由组件层处理) ——

private val SereneLight = lightColorScheme(
    primary = Color(0xFF3F6FAE), // spec 主色 #4A7DBF 配白字 4.2:1 差一点,同色相压深过 AA
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE9F7),
    onPrimaryContainer = Color(0xFF14304F),
    secondary = Color(0xFF5C6B7D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6EAF0),
    onSecondaryContainer = Color(0xFF2A3A4C),
    background = Color(0xFFF8F6F0),
    onBackground = Color(0xFF1E2A38),
    surface = Color(0xFFF8F6F0),
    onSurface = Color(0xFF1E2A38),
    surfaceVariant = Color(0xFFECE9E1),
    onSurfaceVariant = Color(0xFF5A6472),
    outline = Color(0xFFD5D0C4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val SereneDark = darkColorScheme(
    primary = Color(0xFF8FB4DC),
    onPrimary = Color(0xFF0B2233),
    primaryContainer = Color(0xFF2A4560),
    onPrimaryContainer = Color(0xFFCFE3FA),
    secondary = Color(0xFFB8C6D4),
    onSecondary = Color(0xFF24364A),
    secondaryContainer = Color(0xFF24364A),
    onSecondaryContainer = Color(0xFFC9D8E6),
    background = Color(0xFF1A2A3A),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF1A2A3A),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF243545),
    onSurfaceVariant = Color(0xFFA9BACB),
    outline = Color(0xFF3C5065),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

fun sereneLightScheme(): ColorScheme = SereneLight
fun sereneDarkScheme(): ColorScheme = SereneDark

/** spec §5.2 主题 4 顶栏渐变(SERENE 头部:天空蓝 → 紫;深色各自一套) */
object SereneHeader {
    val lightStart = Color(0xFF74B9FF)
    val lightEnd = Color(0xFFA29BFE)
    val darkStart = Color(0xFF2C4A6E)
    val darkEnd = Color(0xFF413A75)
}
