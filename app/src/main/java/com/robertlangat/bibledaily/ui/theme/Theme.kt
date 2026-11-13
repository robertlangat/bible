package com.robertlangat.bibledaily.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.robertlangat.bibledaily.data.ThemeMode

// White surfaces with black text and black accents.
private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF1F1F1F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDED),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF1F1F1F),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF333333),
    surfaceTint = Color.White,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFF2F2F2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF7F7F7),
    surfaceContainerHighest = Color(0xFFF2F2F2),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFFDDDDDD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FCAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF1E4E79),
    onPrimaryContainer = Color(0xFFD3E4F7),
    secondary = Color(0xFFFFB951),
    onSecondary = Color(0xFF442B00),
    secondaryContainer = Color(0xFF5A3D00),
    onSecondaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF121416),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121416),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6CF),
)

/** Highlight palette (index 1..4). Index 0 means "no highlight". */
val HighlightColors = listOf(
    Color(0xFFFFE082), // yellow
    Color(0xFFA5D6A7), // green
    Color(0xFF90CAF9), // blue
    Color(0xFFF48FB1), // pink
)
val HighlightNames = listOf("yellow", "green", "blue", "pink")

fun highlightColor(index: Int, dark: Boolean): Color? {
    val base = HighlightColors.getOrNull(index - 1) ?: return null
    return if (dark) base.copy(alpha = 0.32f) else base.copy(alpha = 0.65f)
}

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun BibleDailyTheme(dark: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
