package com.aurelay.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aurelay.engine.AudioEngine
import com.aurelay.ui.screens.DashboardScreen
import com.aurelay.ui.screens.SettingsScreen

/**
 * Main entry point for the Aurelay application UI.
 *
 * Architecture:
 * - Responsive Layout:
 *   - Mobile (< 800dp): Scaffold with Bottom Navigation.
 *   - Desktop (>= 800dp): Row with Navigation Rail (Left) + Dashboard.
 */
@Composable
fun App(
    audioEngine: AudioEngine,
    themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    AurelayTheme(themeMode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints {
                val isDesktop = maxWidth >= 800.dp
                var selectedScreen by rememberSaveable { mutableStateOf(Screen.Connect) }

                if (isDesktop) {
                    DesktopLayout(
                        audioEngine = audioEngine,
                        selectedScreen = selectedScreen,
                        onScreenSelected = { selectedScreen = it }
                    )
                } else {
                    MobileLayout(
                        audioEngine = audioEngine,
                        selectedScreen = selectedScreen,
                        onScreenSelected = { selectedScreen = it }
                    )
                }
            }
        }
    }
}

// Internal to file or public if needed outside
enum class Screen(val label: String, val icon: ImageVector) {
    // Using standard core icons to avoid missing extended dependency issues
    Connect("Connect", Icons.Default.PlayArrow),
    Audio("Audio", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
private fun DesktopLayout(
    audioEngine: AudioEngine,
    selectedScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.width(80.dp).fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Screen.values().forEach { screen ->
                NavigationRailItem(
                    selected = selectedScreen == screen,
                    onClick = { onScreenSelected(screen) },
                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                    label = { Text(screen.label) }
                )
            }
        }

        // Main Content Area
        Surface(modifier = Modifier.weight(1f)) {
            when (selectedScreen) {
                Screen.Connect -> DashboardScreen(audioEngine, isDesktop = true)
                Screen.Audio -> DashboardScreen(audioEngine, isDesktop = true) // Re-use for now
                Screen.Settings -> SettingsScreen(audioEngine)
            }
        }
    }
}

@Composable
private fun MobileLayout(
    audioEngine: AudioEngine,
    selectedScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.values().forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { onScreenSelected(screen) },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(modifier = Modifier.padding(paddingValues)) {
            when (selectedScreen) {
                Screen.Connect -> DashboardScreen(audioEngine, isDesktop = false)
                Screen.Audio -> DashboardScreen(audioEngine, isDesktop = false)
                Screen.Settings -> SettingsScreen(audioEngine)
            }
        }
    }
}
