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

// Aurora palette: deep-space dark, aurora green accent.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF5DD9B8),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF00513F),
    onPrimaryContainer = Color(0xFF7CF6D5),
    secondary = Color(0xFFB6CCC4),
    background = Color(0xFF0E1116),
    surface = Color(0xFF151A21),
    surfaceVariant = Color(0xFF1F2630),
    onSurface = Color(0xFFE3E8EE),
    onBackground = Color(0xFFE3E8EE),
    tertiary = Color(0xFF7FA8FF)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8CF7D3),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4C6359),
    background = Color(0xFFFAFCF9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurface = Color(0xFF1B211E),
    tertiary = Color(0xFF3D5BA9)
)

@Composable
fun AuroraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
