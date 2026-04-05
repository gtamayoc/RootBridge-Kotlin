package com.gtc.rootbridgekotlin.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DeepSiliconColorScheme = darkColorScheme(
    primary = AccentPlasma,
    secondary = AccentSignal,
    tertiary = AccentWarning,
    error = AccentError,
    
    background = DeepVoid,
    surface = DeepSurface,
    surfaceVariant = DeepElevated,
    surfaceContainer = DeepSurface,
    surfaceContainerHigh = DeepElevated,
    
    onPrimary = DeepVoid,
    onSecondary = DeepVoid,
    onTertiary = DeepVoid,
    onError = TextPrimary,
    
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    
    outline = BorderSubtle,
    outlineVariant = BorderSubtle
)

@Composable
fun RootBridgeKotlinTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DeepSiliconColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}