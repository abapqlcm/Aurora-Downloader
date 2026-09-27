package com.aurora.downloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

/**
 * Aurora palette: deep black + signature gold.
 *
 * Deliberately near-black rather than "dark grey": on OLED panels this saves
 * measurable power and makes the gold read as genuinely bright. No blur or
 * translucency anywhere — flat surfaces separated by hairline strokes.
 */
val AuroraBlack = Color(0xFF0A0A0C)
val AuroraSurface = Color(0xFF121317)
val AuroraSurfaceVariant = Color(0xFF1C1D22)
val AuroraGold = Color(0xFFF5B800)
val AuroraGoldSoft = Color(0xFFFFD466)

private val AuroraDark = darkColorScheme(
    primary = AuroraGold,
    onPrimary = Color(0xFF0A0A0C),
    primaryContainer = Color(0xFF2A2308),
    onPrimaryContainer = AuroraGoldSoft,
    secondary = Color(0xFFE8E6E1),
    onSecondary = Color(0xFF141416),
    secondaryContainer = Color(0xFF1E1F24),
    onSecondaryContainer = Color(0xFFE8E6E1),
    tertiary = Color(0xFF7DDFC0),
    onTertiary = Color(0xFF003829),
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF410001),
    errorContainer = Color(0xFF6B2018),
    onErrorContainer = Color(0xFFFFB4AB),
    background = AuroraBlack,
    onBackground = Color(0xFFEDEDED),
    surface = AuroraSurface,
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = AuroraSurfaceVariant,
    onSurfaceVariant = Color(0xFF9A9AA1),
    outline = Color(0xFF2A2B30),
    outlineVariant = Color(0xFF1F2025),
    scrim = Color(0xFF000000),
)

private val AuroraLight = lightColorScheme(
    primary = Color(0xFF8A6800),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFEFA8),
    onPrimaryContainer = Color(0xFF2A2000),
    secondary = Color(0xFF6B5F45),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF006C4C),
    background = Color(0xFFFCFBF6),
    onBackground = Color(0xFF1D1B13),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1B13),
    surfaceVariant = Color(0xFFE9E2D0),
    onSurfaceVariant = Color(0xFF4B4639),
    outline = Color(0xFF7D7768),
    error = Color(0xFFBA1A1A),
)

@Composable
fun AuroraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AuroraDark
        else -> AuroraLight
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = AuroraTypography,
        content = content
    )
}
