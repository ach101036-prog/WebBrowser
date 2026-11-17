package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BrowserDarkColorScheme = darkColorScheme(
    primary = CyanElectric,
    onPrimary = Color(0xFF001F26),
    primaryContainer = CyanElectricContainer,
    onPrimaryContainer = Color(0xFF9DF2FF),
    secondary = CoralPulse,
    onSecondary = Color.White,
    secondaryContainer = CoralPulseContainer,
    onSecondaryContainer = Color(0xFFFFD9DF),
    tertiary = AmberWarn,
    onTertiary = Color(0xFF281800),
    tertiaryContainer = AmberWarnContainer,
    onTertiaryContainer = Color(0xFFFFE0A6),
    background = BrowserObsidian,
    onBackground = TextPrimaryDark,
    surface = BrowserSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = BrowserElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = BrowserOutline,
    outlineVariant = Color(0xFF1F2739),
    error = Color(0xFFFF5A5F),
    onError = Color.White
)

private val BrowserLightColorScheme = lightColorScheme(
    primary = CyanElectricLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF6FF),
    onPrimaryContainer = Color(0xFF002029),
    secondary = CoralPulseLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9DF),
    onSecondaryContainer = Color(0xFF400010),
    tertiary = Color(0xFFB87400),
    onTertiary = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    outlineVariant = Color(0xFFE3E8F2)
)

val FlowBrowserShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FlowBrowserTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BrowserDarkColorScheme else BrowserLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = FlowBrowserTypography,
        shapes = FlowBrowserShapes,
        content = content
    )
}
