package com.aurelay

import androidx.compose.runtime.Composable
import com.aurelay.engine.AudioEngine
import com.aurelay.ui.AurelayTheme
import com.aurelay.ui.MainScreen
import com.aurelay.ui.ThemeMode

/**
 * Main entry point for the Aurelay application UI.
 * This composable can be called from platform-specific code (Android Activity, Desktop main).
 * 
 * Architecture:
 * - Uses Material 3 with Apple-inspired aesthetic
 * - Responsive layout that adapts to screen size (mobile vs desktop)
 * - System theme detection with override support
 * 
 * @param audioEngine Platform-specific audio engine implementation
 * @param themeMode Theme mode (default: SYSTEM for auto dark/light)
 */
@Composable
fun App(
    audioEngine: AudioEngine,
    themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    AurelayTheme(themeMode = themeMode) {
        MainScreen(audioEngine = audioEngine)
    }
}
