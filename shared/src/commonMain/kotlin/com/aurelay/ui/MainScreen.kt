package com.devindeed.aurelay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devindeed.aurelay.engine.AudioEngine
import com.devindeed.aurelay.ui.components.*
import com.devindeed.aurelay.ui.screens.*
import com.devindeed.aurelay.ui.navigation.Screen

/**
 * Main screen with responsive navigation.
 * 
 * Responsive Architecture:
 * - Mobile (< 800dp): Bottom navigation + vertical layout
 * - Desktop (>= 800dp): Side navigation rail + two-pane dashboard
 * 
 * Design: Apple-Material hybrid with large corner radii and generous spacing
 */
@Composable
fun MainScreen(
    audioEngine: AudioEngine,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 800.dp
        
        if (isWideScreen) {
            // Desktop layout: Side nav + content
            Row(modifier = Modifier.fillMaxSize()) {
                SideNavRail(
                    currentScreen = currentScreen,
                    onNavigate = { currentScreen = it }
                )
                
                ScreenContent(
                    currentScreen = currentScreen,
                    audioEngine = audioEngine,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Mobile layout: Content + bottom nav
            Scaffold(
                bottomBar = {
                    BottomNavBar(
                        currentScreen = currentScreen,
                        onNavigate = { currentScreen = it }
                    )
                }
            ) { paddingValues ->
                ScreenContent(
                    currentScreen = currentScreen,
                    audioEngine = audioEngine,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Renders the appropriate screen based on navigation state
 */
@Composable
private fun ScreenContent(
    currentScreen: Screen,
    audioEngine: AudioEngine,
    modifier: Modifier = Modifier
) {
    when (currentScreen) {
        is Screen.Dashboard -> DashboardScreen(audioEngine, modifier)
        is Screen.Audio -> AudioVisualizerScreen(audioEngine, modifier)
        is Screen.Settings -> SettingsScreen(audioEngine,modifier)
        is Screen.About -> AboutScreen(modifier)
    }
}
