package io.github.aurynk.audiorelay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.core.content.ContextCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent as AndroidIntent
import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface
import android.util.Log
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var connectionState by mutableStateOf(false)
    private var clientIpState by mutableStateOf("")
    private var audioLevels by mutableStateOf(FloatArray(24) { 0f })
    
    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: AndroidIntent?) {
            intent ?: return
            when (intent.action) {
                "io.github.aurynk.CLIENT_CONNECTION" -> {
                    val connected = intent.getBooleanExtra("connected", false)
                    val ip = intent.getStringExtra("client_ip") ?: ""
                    Log.d("MainActivity", "Broadcast received: connected=$connected, ip=$ip")
                    connectionState = connected
                    clientIpState = ip
                }
                AudioRelayService.ACTION_AUDIO_LEVEL -> {
                    val levels = intent.getFloatArrayExtra(AudioRelayService.EXTRA_AUDIO_LEVELS)
                    if (levels != null && levels.size == 24) {
                        audioLevels = levels
                    }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Make navigation bar transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        // Ensure the notification channel exists
        createNotificationChannel()
        
        // Check if auto-start is enabled in preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val autoStart = prefs.getBoolean("auto_start_service", false)
        
        if (autoStart) {
            val intent = Intent(this, AudioRelayService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
        
        // Register broadcast receiver with proper flags for all Android versions
        val filter = IntentFilter().apply {
            addAction("io.github.aurynk.CLIENT_CONNECTION")
            addAction(AudioRelayService.ACTION_AUDIO_LEVEL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(connectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(connectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(connectionReceiver, filter)
        }
        Log.d("MainActivity", "Broadcast receiver registered for CLIENT_CONNECTION")

        setContent {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
            var useDynamicColors by remember { mutableStateOf(prefs.getBoolean("use_dynamic_colors", true)) }
            
            // Listen for preference changes
            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
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
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AurynkReceiverApp(
                        context = this,
                        isClientConnected = connectionState,
                        clientIp = clientIpState,
                        audioLevels = audioLevels
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(connectionReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                "audioRelayChannel",
                "Audio Relay Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}

// Helper function to get device IP address
fun getDeviceIpAddress(context: Context): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    return address.hostAddress ?: "Unknown"
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "Unknown"
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AurynkReceiverApp(
    context: Context,
    isClientConnected: Boolean,
    clientIp: String,
    audioLevels: FloatArray = FloatArray(24) { 0f }
) {
    val deviceIp = remember { getDeviceIpAddress(context) }
    val port = "5000" // Fixed port matching the service
    
    // Get preferences
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val autoStart = prefs.getBoolean("auto_start_service", false)
    val showVisualizer = prefs.getBoolean("show_visualizer", true)
    val showVolumeSlider = prefs.getBoolean("show_volume_slider", true)
    val themeMode = prefs.getString("theme_mode", "system") ?: "system"
    val useDynamicColors = prefs.getBoolean("use_dynamic_colors", true)

    // --- STATE ---
    var isServiceRunning by remember { mutableStateOf(autoStart) } // Service state based on preference
    var volume by remember { mutableFloatStateOf(0.8f) }
    var isMuted by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Dynamic colors based on connection state
    val statusColor by animateColorAsState(
        if (isClientConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        label = "colorState"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Aurynk", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            // 1. HEADER SECTION: Status Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f))
                        .then(
                            if (isClientConnected) Modifier.clickable {
                                isMuted = !isMuted
                                val muteIntent = Intent(context, AudioRelayService::class.java).apply {
                                    action = AudioRelayService.ACTION_SET_VOLUME
                                    putExtra(AudioRelayService.EXTRA_VOLUME, if (isMuted) 0f else volume)
                                }
                                context.startService(muteIntent)
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (!isClientConnected) Icons.Rounded.LinkOff 
                                     else if (isMuted) Icons.Rounded.HeadsetOff 
                                     else Icons.Rounded.Headphones,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        modifier = Modifier.size(56.dp),
                        tint = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (isClientConnected) {
                        if (isMuted) "Muted" else "Streaming Audio"
                    } else if (isServiceRunning) "Waiting for Connection" else "Service Stopped",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isClientConnected) "Connected: $clientIp:$port" else "Press Start to begin playing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. MIDDLE SECTION: Visualizer OR Connection Info
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(visible = isClientConnected && showVisualizer) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Real Audio Visualizer
                        RealAudioVisualizer(audioLevels = audioLevels)
                        Spacer(Modifier.height(32.dp))
                        // Volume Slider - conditionally shown
                        if (showVolumeSlider) {
                            Text(
                                "Local Volume",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = volume,
                                onValueChange = { newVolume ->
                                    volume = newVolume
                                    isMuted = false // Unmute when user adjusts volume
                                    // Send volume change to service
                                    val volumeIntent = Intent(context, AudioRelayService::class.java).apply {
                                        action = AudioRelayService.ACTION_SET_VOLUME
                                        putExtra(AudioRelayService.EXTRA_VOLUME, newVolume)
                                    }
                                    context.startService(volumeIntent)
                                },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = !isClientConnected && isServiceRunning) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(44.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Connection Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(24.dp))

                                Text(
                                    "Device IP Address",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    deviceIp,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.height(20.dp))

                                Text(
                                    "Port",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    port,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.height(20.dp))

                                Text(
                                    "Use these details in your PC app to connect",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        if (isServiceRunning) {
                            Text(
                                "Listening for incoming connections...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // 3. BOTTOM SECTION: Service Control Button
                Button(
                onClick = {
                    isServiceRunning = !isServiceRunning
                    if (!isServiceRunning) {
                        val intent = Intent(context, AudioRelayService::class.java)
                        context.stopService(intent)
                    } else {
                        val intent = Intent(context, AudioRelayService::class.java)
                        ContextCompat.startForegroundService(context, intent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Icon(
                    Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isServiceRunning) "Stop" else "Start",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    
    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "About Aurynk",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { 
                            showSettingsDialog = true
                            showAboutDialog = false 
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        text = "Stream audio from your PC to your Android device over a secure TLS connection.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        text = "Developer",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ashutosh Singh",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Text(
                        text = "GitHub",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "github.com/IshuSinghSE/AudioRelay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        text = "© 2025 Ishu Singh. All rights reserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
    
    // Settings Dialog
    if (showSettingsDialog) {
        var tempAutoStart by remember { mutableStateOf(prefs.getBoolean("auto_start_service", false)) }
        var tempShowVisualizer by remember { mutableStateOf(prefs.getBoolean("show_visualizer", true)) }
        var tempShowVolumeSlider by remember { mutableStateOf(prefs.getBoolean("show_volume_slider", true)) }
        var tempThemeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
        var tempUseDynamicColors by remember { mutableStateOf(prefs.getBoolean("use_dynamic_colors", true)) }
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Auto-start service setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-start Service",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Start service automatically on app launch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tempAutoStart,
                            onCheckedChange = { tempAutoStart = it }
                        )
                    }
                    
                    HorizontalDivider()
                    
                    // Theme mode setting
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "App Theme",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Choose your preferred theme",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = tempThemeMode == "system",
                                onClick = { tempThemeMode = "system" },
                                label = { Text("System") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = tempThemeMode == "light",
                                onClick = { tempThemeMode = "light" },
                                label = { Text("Light") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = tempThemeMode == "dark",
                                onClick = { tempThemeMode = "dark" },
                                label = { Text("Dark") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Dynamic colors setting (Android 12+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dynamic Colors",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Use colors from your wallpaper",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = tempUseDynamicColors,
                                onCheckedChange = { tempUseDynamicColors = it }
                            )
                        }
                        
                        HorizontalDivider()
                    }
                    
                    // Show volume slider setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Volume Slider",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Display volume control slider",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tempShowVolumeSlider,
                            onCheckedChange = { tempShowVolumeSlider = it }
                        )
                    }
                    
                    HorizontalDivider()
                    
                    // Show visualizer setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Audio Visualizer",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Display real-time audio waveform",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tempShowVisualizer,
                            onCheckedChange = { tempShowVisualizer = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Save settings
                        prefs.edit().apply {
                            putBoolean("auto_start_service", tempAutoStart)
                            putBoolean("show_volume_slider", tempShowVolumeSlider)
                            putBoolean("show_visualizer", tempShowVisualizer)
                            putString("theme_mode", tempThemeMode)
                            putBoolean("use_dynamic_colors", tempUseDynamicColors)
                            apply()
                        }
                        showSettingsDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// A real visualizer component that responds to actual audio data
@Composable
fun RealAudioVisualizer(audioLevels: FloatArray) {
    val brush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )
    
    // Use spring animation with low bounce for smooth, gradual movement
    val animatedLevels = audioLevels.map { level ->
        animateFloatAsState(
            targetValue = level,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "audioLevel"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        animatedLevels.forEachIndexed { index, animatedLevel ->
            val barHeight = 10f + (animatedLevel.value * 60f) // Min 10dp, max 70dp per side
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.height(140.dp)
            ) {
                // Top bar (grows upward)
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(bottomStart = 1.5.dp, bottomEnd = 1.5.dp))
                        .background(brush)
                )
                
                // Center dot
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                
                // Bottom bar (grows downward)
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(topStart = 1.5.dp, topEnd = 1.5.dp))
                        .background(brush)
                )
            }
        }
    }
}

// A fake visualizer component just for the mockup look (kept for preview)
@Composable
fun FakeAudioVisualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    // Create a gradient brush for the bars
    val brush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(12) { index ->
            // Animate height randomly to simulate audio
            val height by infiniteTransition.animateFloat(
                initialValue = 15f,
                targetValue = Random.nextInt(30, 120).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = Random.nextInt(400, 800),
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ), label = "barHeight$index"
            )
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(brush)
            )
        }
    }
}

@Preview
@Composable
fun PreviewAurynkApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        // For preview, create a mock context
        AurynkReceiverApp(
            context = androidx.compose.ui.platform.LocalContext.current,
            isClientConnected = false,
            clientIp = "",
            audioLevels = FloatArray(24) { 0.5f }
        )
    }
}