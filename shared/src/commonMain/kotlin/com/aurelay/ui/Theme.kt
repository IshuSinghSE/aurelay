package com.aurelay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==============================================================================
// 🎨 COLOR PALETTE (Cupertino Dark - Apple-Material Hybrid)
// ==============================================================================

// --- Dark Mode Colors ---
private val DarkBackground = Color(0xFF1C1C1E)    // Apple System Gray 6
private val DarkSurface = Color(0xFF2C2C2E)       // Apple System Gray 5 (Cards)
private val DarkOutlineVariant = Color(0xFF3A3A3C) // Borders (1dp outlineVariant)
private val DarkAccent = Color(0xFFFF453A)        // Coral Orange (Hero/Active)

private val DarkPrimary = DarkAccent
private val DarkSecondary = Color(0xFF0A84FF)     // iOS System Blue (Alternate)
private val DarkError = Color(0xFFFF453A)         // Stop Button Red
private val DarkSurfaceVariant = Color(0xFF3A3A3C) // Secondary surface

// --- Light Mode Colors (Fallback/Optional) ---
private val LightPrimary = Color(0xFF007AFF)
private val LightSecondary = Color(0xFF5856D6)
private val LightError = Color(0xFFFF3B30)
private val LightBackground = Color(0xFFF2F2F7)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFE5E5EA)
private val LightOutline = Color(0xFFD1D1D6)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.White,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = DarkPrimary,

    secondary = DarkSecondary,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkSecondary,

    error = DarkError,
    onError = Color.White,
    errorContainer = Color(0xFF3A0008),
    onErrorContainer = DarkError,

    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    
    // Critical for the "Card" look
    surfaceVariant = DarkSurfaceVariant, 
    onSurfaceVariant = Color(0xFFEBEBF5),
    outline = Color(0xFF48484A),
    outlineVariant = DarkOutlineVariant // The request specified border color
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEEAF6),
    onPrimaryContainer = LightPrimary,

    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightSecondary,

    error = LightError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = LightBackground,
    onBackground = Color.Black,
    surface = LightSurface,
    onSurface = Color.Black,

    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF3C3C43),
    outline = LightOutline,
    outlineVariant = Color(0xFFE5E5EA)
)

// ==============================================================================
// 🔡 TYPOGRAPHY (San Francisco Style)
// ==============================================================================

private val AurelayTypography = Typography(
    // Large Titles (e.g., "Dashboard")
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    // Section Headers
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),
    // Card Titles
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    // Body text
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.4).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp
    ),
    // Button Text
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
)

// ==============================================================================
// 📐 SHAPES
// ==============================================================================

private val AurelayShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), // Requested for Cards
    extraLarge = RoundedCornerShape(32.dp)
)

// ==============================================================================
// 🎭 THEME COMPOSABLE
// ==============================================================================

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Composable
fun AurelayTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemInDarkTheme = isSystemInDarkTheme()
    
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemInDarkTheme
    }

    // Default to dark theme if system is not specified and we want "Cupertino Dark" preference
    // But respecting the parameter.
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AurelayTypography,
        shapes = AurelayShapes,
        content = content
    )
}
