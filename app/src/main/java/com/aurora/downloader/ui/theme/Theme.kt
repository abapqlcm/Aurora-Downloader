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
 * Aurora palette.
 *
 * Deliberately near-black rather than "dark grey": on OLED panels this saves
 * measurable power, and it makes the accent read as genuinely bright. No blur
 * or translucency anywhere — flat surfaces separated by hairline strokes.
 */
private val AuroraDark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1B33),
    primaryContainer = Color(0xFF1B3A63),
    onPrimaryContainer = Color(0xFFC6E0FF),
    secondary = Color(0xFFA8C4E0),
    onSecondary = Color(0xFF102438),
    secondaryContainer = Color(0xFF1E3346),
    onSecondaryContainer = Color(0xFFD3E4F7),
    tertiary = Color(0xFF7DDFC0),
    onTertiary = Color(0xFF003829),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF08090B),
    onBackground = Color(0xFFE3E4E8),
    surface = Color(0xFF121418),
    onSurface = Color(0xFFE3E4E8),
    surfaceVariant = Color(0xFF23262C),
    onSurfaceVariant = Color(0xFFA9ADB6),
    outline = Color(0xFF34383F),
    outlineVariant = Color(0xFF23262C),
    scrim = Color(0xFF000000),
)

private val AuroraLight = lightColorScheme(
    primary = Color(0xFF2B6CB0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF55606E),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF006C4C),
    background = Color(0xFFFCFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
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
