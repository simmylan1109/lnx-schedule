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
