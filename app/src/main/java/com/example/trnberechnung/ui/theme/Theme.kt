package com.example.trnberechnung.ui.theme

import android.app.Activity
import android.os.Build
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

// ── Nautisches Dark-Theme (immer aktiv) ──
private val NauticalColorScheme = darkColorScheme(
    primary = NauticalPrimary,
    onPrimary = NauticalTextOnPrimary,
    primaryContainer = NauticalPrimaryDark,
    onPrimaryContainer = NauticalTextPrimary,
    secondary = NauticalSecondary,
    onSecondary = NauticalTextOnPrimary,
    secondaryContainer = NauticalSurfaceVariant,
    onSecondaryContainer = NauticalTextPrimary,
    tertiary = NauticalAccentWarm,
    onTertiary = NauticalTextOnPrimary,
    background = NauticalBackground,
    onBackground = NauticalTextPrimary,
    surface = NauticalSurface,
    onSurface = NauticalTextPrimary,
    surfaceVariant = NauticalSurfaceVariant,
    onSurfaceVariant = NauticalTextSecondary,
    error = NauticalNoGo,
    onError = Color.White,
    errorContainer = NauticalNoGoBg,
    onErrorContainer = NauticalNoGo,
    outline = NauticalDivider,
    outlineVariant = NauticalGridLine
)

@Composable
fun TörnberechnungTheme(
    darkTheme: Boolean = true, // Immer dark
    dynamicColor: Boolean = false, // Unser eigenes Theme
    content: @Composable () -> Unit
) {
    val colorScheme = NauticalColorScheme

    // StatusBar & NavigationBar einfärben
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = NauticalBackground.toArgb()
            window.navigationBarColor = NauticalBottomBar.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}