package com.devindeed.aurelay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.aurelay.App
import com.aurelay.engine.AndroidAudioEngine
import com.devindeed.aurelay.iap.PurchaseManager
import com.devindeed.aurelay.ui.SmartAdBanner

/**
 * Android entry point - thin shell that launches the shared App.
 * 
 * Architecture:
 * - Creates AndroidAudioEngine (integrates with AudioCaptureService/AudioRelayService)
 * - Handles platform-specific setup (edge-to-edge, theming, auto-start)
 * - Wraps shared App() with platform-specific features (IAP, Ads)
 * 
 * This file should stay minimal - all shared logic is in the :shared module.
 */
class MainActivityShared : ComponentActivity() {
    
    companion object {
        const val ACTION_CONNECTION_REQUEST = "com.devindeed.aurelay.CONNECTION_REQUEST"
        const val ACTION_CONNECTION_RESPONSE = "com.devindeed.aurelay.CONNECTION_RESPONSE"
        const val EXTRA_APPROVED = "approved"
    }
    
    private lateinit var audioEngine: AndroidAudioEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Make status bar and navigation bar transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Initialize audio engine
        audioEngine = AndroidAudioEngine(this)
        
        // Auto-start service if enabled
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val autoStart = prefs.getBoolean("auto_start_service", false)
        if (autoStart) {
            val intent = Intent(this, AudioRelayService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
        
        setContent {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
            var useDynamicColors by remember { mutableStateOf(prefs.getBoolean("use_dynamic_colors", true)) }
            
            // Listen for preference changes
            DisposableEffect(Unit) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        "theme_mode" -> themeMode = prefs.getString("theme_mode", "system") ?: "system"
                        "use_dynamic_colors" -> useDynamicColors = prefs.getBoolean("use_dynamic_colors", true)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }
            
            val isDarkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            
            val colorScheme = when {
                useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (isDarkTheme) dynamicDarkColorScheme(this)
                    else dynamicLightColorScheme(this)
                }
                isDarkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }
            
            MaterialTheme(colorScheme = colorScheme) {
                // Sync system bars
                val sysColor = MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    try {
                        @Suppress("DEPRECATION")
                        window.statusBarColor = sysColor
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            @Suppress("DEPRECATION")
                            window.navigationBarColor = sysColor
                        }
                        val controller = WindowInsetsControllerCompat(window, window.decorView)
                        controller.isAppearanceLightStatusBars = !isDarkTheme
                        controller.isAppearanceLightNavigationBars = !isDarkTheme
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Failed to set system bar colors: ${e.message}")
                    }
                }
                
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Main app content (shared module)
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            App(audioEngine = audioEngine)
                        }
                        
                        // Ad banner at bottom
                        val isPremium by PurchaseManager.isPremium.collectAsState()
                        SmartAdBanner(
                            modifier = Modifier.fillMaxWidth(),
                            isPremium = isPremium
                        )
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        audioEngine.dispose()
    }
}
