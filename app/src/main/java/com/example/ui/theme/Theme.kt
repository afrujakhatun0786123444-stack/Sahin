package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CosmicCyan,
    secondary = ElectricViolet,
    tertiary = NeonPink,
    background = CyberDark,
    surface = CyberSurface,
    onBackground = CyberTextPrimary,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = CosmicCyan,
    secondary = ElectricViolet,
    tertiary = NeonPink,
    background = CyberDark, // Force dark background for futuristic assistant look in light mode too!
    surface = CyberSurface,
    onBackground = CyberTextPrimary,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Force L99 custom sci-fi theme
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme // Force dark theme always for sci-fi look!

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
