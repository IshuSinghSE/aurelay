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
// 🎨 COLOR PALETTE (Apple-Material Hybrid)
// ==============================================================================

// --- Dark Mode Colors ---
private val DarkPrimary = Color(0xFF0A84FF)       // iOS System Blue (Active State)
private val DarkSecondary = Color(0xFF5E5CE6)     // iOS System Indigo (Accents)
private val DarkError = Color(0xFFFF453A)         // iOS System Red (Stop Button)
private val DarkBackground = Color(0xFF1C1C1E)    // Apple System Gray 6 (Not pure black)
private val DarkSurface = Color(0xFF2C2C2E)       // Apple System Gray 5 (Cards)
private val DarkSurfaceVariant = Color(0xFF3A3A3C) // Apple System Gray 4 (Hover/Input)
private val DarkOutline = Color(0xFF48484A)       // Apple System Gray 3 (Borders)
private val DarkTextPrimary = Color(0xFFFFFFFF)
private val DarkTextSecondary = Color(0xFFEBEBF5) // 60% White

// --- Light Mode Colors ---
private val LightPrimary = Color(0xFF007AFF)      // iOS System Blue
private val LightSecondary = Color(0xFF5856D6)    // iOS System Indigo
private val LightError = Color(0xFFFF3B30)        // iOS System Red
private val LightBackground = Color(0xFFF2F2F7)   // Apple System Grouped Background
private val LightSurface = Color(0xFFFFFFFF)      // Pure White Cards
private val LightSurfaceVariant = Color(0xFFE5E5EA) // Apple System Gray 5
private val LightOutline = Color(0xFFD1D1D6)      // Apple System Gray 4
private val LightTextPrimary = Color(0xFF000000)
private val LightTextSecondary = Color(0xFF3C3C43) // 60% Black

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
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    
    // Critical for the "Card" look
    surfaceVariant = DarkSurfaceVariant, 
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkSurfaceVariant
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
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,

    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = Color(0xFFE5E5EA)
)

// ==============================================================================
// 🔡 TYPOGRAPHY (San Francisco Style - Clean & Readable)
// ==============================================================================

private val AurelayTypography = Typography(
    // Large Titles (e.g., "Dashboard")
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    // Section Headers (e.g., "Nearby Devices")
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    // Card Titles (e.g., "Gaming PC")
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // Subtitles / Lists
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp, // Apple standard body size
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    // Secondary text / Captions
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    // Button Text
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)

// ==============================================================================
// 📐 SHAPES (Soft & Friendly)
// ==============================================================================

private val AurelayShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Buttons / Chips
    small = RoundedCornerShape(12.dp),       // Small Cards
    medium = RoundedCornerShape(16.dp),      // Dialogs
    large = RoundedCornerShape(24.dp),       // Main Cards / Bottom Sheets
    extraLarge = RoundedCornerShape(32.dp)   // Floating Action Buttons / Hero
)

// ==============================================================================
// 🎭 THEME COMPOSABLE
// ==============================================================================

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Aurelay Design System
 * * Uses an "Apple-Material Hybrid" philosophy:
 * - Dark Mode: Apple System Grays (Not pure black)
 * - Light Mode: Clean White surfaces on Gray background
 * - Shapes: High corner radius (24dp) for a modern, friendly feel.
 */
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

    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AurelayTypography,
        shapes = AurelayShapes,
        content = content
    )
}