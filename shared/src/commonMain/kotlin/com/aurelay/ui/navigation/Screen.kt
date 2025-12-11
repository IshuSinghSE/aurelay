package com.aurelay.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations for the app.
 * Matches the design mockups with Dashboard, Audio, and Settings.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Audio : Screen("audio", "Audio", Icons.Default.Star)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object About : Screen("about", "About", Icons.Default.Info)
}

val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Audio,
    Screen.Settings
)

val allScreens = listOf(
    Screen.Dashboard,
    Screen.Audio,
    Screen.Settings,
    Screen.About
)
