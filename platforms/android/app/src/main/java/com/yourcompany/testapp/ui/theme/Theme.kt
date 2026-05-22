package com.yourcompany.testapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLightGray,
    secondary = SecondaryGray,
    tertiary = Color(0xFF9CA3AF),             // Gray 400
    background = DarkBg,
    surface = DarkSurface,
    onBackground = DarkOnBg,
    onSurface = DarkOnSurface,
    // user bubble
    primaryContainer = DarkUserBubble,
    onPrimaryContainer = DarkUserBubbleText,
    // bot bubble
    secondaryContainer = DarkBotBubble,
    onSecondaryContainer = DarkBotBubbleText,
    // input field
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFFD1D5DB)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGray,
    secondary = SecondaryGray,
    tertiary = Color(0xFF6B7280),             // Gray 500
    background = LightBg,
    surface = LightSurface,
    onBackground = LightOnBg,
    onSurface = LightOnSurface,
    // user bubble
    primaryContainer = LightUserBubble,
    onPrimaryContainer = LightUserBubbleText,
    // bot bubble
    secondaryContainer = LightBotBubble,
    onSecondaryContainer = LightBotBubbleText,
    // input field
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF4B5563)
)

@Composable
fun TestappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
