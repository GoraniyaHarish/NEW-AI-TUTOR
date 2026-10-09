package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9AC7AE),
    onPrimary = Color(0xFF17372C),
    primaryContainer = Color(0xFF315C49),
    onPrimaryContainer = Color(0xFFE3F2E7),
    secondary = BrandCyan,
    onSecondary = Color(0xFF0F172A),
    tertiary = BrandViolet,
    background = Color(0xFF171F1B),
    surface = Color(0xFF202A24),
    surfaceVariant = Color(0xFF2B3830),
    onBackground = Color(0xFFE9EAE2),
    onSurface = Color(0xFFE9EAE2),
    onSurfaceVariant = Color(0xFFBEC8BD),
    outline = Color(0xFF59675D)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9DE),
    onPrimaryContainer = Color(0xFF1B4938),
    secondary = BrandCyan,
    onSecondary = Color.White,
    tertiary = BrandViolet,
    background = Slate50,
    surface = Color(0xFFFFFEFA),
    surfaceVariant = Color(0xFFECEDE5),
    onBackground = Color(0xFF27372F),
    onSurface = Color(0xFF27372F),
    onSurfaceVariant = Slate600,
    outline = Color(0xFFD6D9CF)
)

@Composable
fun LearnMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our signature theme for brand consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
