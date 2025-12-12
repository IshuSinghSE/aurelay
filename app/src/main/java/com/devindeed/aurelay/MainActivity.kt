package com.devindeed.aurelay

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
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
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.BorderStroke
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent as AndroidIntent
import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.devindeed.aurelay.iap.PurchaseManager
import com.devindeed.aurelay.ui.SmartAdBanner
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast

class MainActivity : ComponentActivity() {
    private var connectionState by mutableStateOf(false)
    private var clientIpState by mutableStateOf("")
    private var audioLevels by mutableStateOf(FloatArray(24) { 0f })
    private var pendingConnectionRequest by mutableStateOf<Pair<String, String>?>(null) // IP, Name
    
    companion object {
        const val ACTION_CONNECTION_REQUEST = "com.devindeed.aurelay.CONNECTION_REQUEST"
        const val ACTION_CONNECTION_RESPONSE = "com.devindeed.aurelay.CONNECTION_RESPONSE"
        const val EXTRA_APPROVED = "approved"
    }
    
    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: AndroidIntent?) {
            intent ?: return
            when (intent.action) {
                "com.devindeed.aurelay.CLIENT_CONNECTION" -> {
                    val connected = intent.getBooleanExtra("connected", false)
                    val ip = intent.getStringExtra("client_ip") ?: ""
                    Log.d("MainActivity", "Broadcast received: connected=$connected, ip=$ip")
                    
                    if (!connected) {
                        // Connection rejected - show toast
                        Toast.makeText(ctx, "Connection rejected by receiver", Toast.LENGTH_SHORT).show()
                    }
                    
                    connectionState = connected
                    clientIpState = if (connected) ip else ""
                }
                ACTION_CONNECTION_REQUEST -> {
                    val ip = intent.getStringExtra("client_ip") ?: ""
                    val name = intent.getStringExtra("client_name") ?: "Unknown Device"
                    Log.d("MainActivity", "Connection request from: $name ($ip)")
                    
                    ctx ?: return
                    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                    val requireConfirm = prefs.getBoolean("require_connection_confirm", true)
                    
                    // Check if device is already paired
                    val pairedDevices = getPairedDevices(ctx)
                    val isPaired = pairedDevices.any { it.ip == ip }
                    
                    if (isPaired) {
                        // Auto-accept paired devices
                        sendConnectionResponse(ip, true)
                        Log.d("MainActivity", "Auto-accepted paired device: $name")
                    } else if (requireConfirm) {
                        // Show confirmation for unpaired devices
                        pendingConnectionRequest = Pair(ip, name)
                    } else {
                        // Auto-accept if confirmation not required
                        sendConnectionResponse(ip, true)
                    }
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
            addAction("com.devindeed.aurelay.CLIENT_CONNECTION")
            addAction(ACTION_CONNECTION_REQUEST)
            addAction(AudioRelayService.ACTION_AUDIO_LEVEL)
        }
        // Use ContextCompat.registerReceiver with explicit non-exported flag to satisfy Android U+ requirements
        ContextCompat.registerReceiver(this, connectionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
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
                // Sync system bars to match the app's Material color scheme and theme
                SideEffect {
                    try {
                        val controller = WindowInsetsControllerCompat(window, window.decorView)
                        // When in light theme we want dark icons; in dark theme we want light icons
                        controller.isAppearanceLightStatusBars = !isDarkTheme
                        controller.isAppearanceLightNavigationBars = !isDarkTheme
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Failed to set system bar appearance: ${e.message}")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                        // Main app content takes all available space so the banner can sit below it
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AurelayApp(
                                context = this@MainActivity,
                                isClientConnected = connectionState,
                                clientIp = clientIpState,
                                audioLevels = audioLevels,
                                pendingConnectionRequest = pendingConnectionRequest,
                                onConnectionResponse = { approved ->
                                    pendingConnectionRequest?.let { (ip, _) ->
                                        sendConnectionResponse(ip, approved)
                                        if (!approved) {
                                            Toast.makeText(this@MainActivity, "Connection rejected", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    pendingConnectionRequest = null
                                },
                                onClientIpSelected = { ip ->
                                    val previous = clientIpState
                                    clientIpState = ip
                                    if (ip.isNotEmpty()) {
                                        sendConnectRequest(ip)
                                    } else {
                                        // clearing selection -> send disconnect to previous if existed
                                        if (previous.isNotEmpty()) {
                                            sendDisconnectRequest(previous)
                                        }
                                    }
                                }
                            )
                        }

                        // Observe dev mock premium state and show SmartAdBanner only if globally enabled
                        val isPremium by PurchaseManager.isPremium.collectAsState(initial = false)
                        if (com.devindeed.aurelay.BuildConfig.ENABLE_ADS) {
                            SmartAdBanner(
                                isPremium = isPremium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .navigationBarsPadding() // keep banner above system nav
                            )
                        }
                    }
                }
                }
            }
        }
    }

    // Send a small UDP connect notification to the selected receiver so it can update its UI
    private fun sendConnectRequest(targetIp: String) {
        Thread {
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket()
                val deviceName = getDeviceName()
                val msg = "${AudioRelayService.CONNECT_REQUEST};$deviceName".toByteArray()
                val packet = DatagramPacket(msg, msg.size, InetAddress.getByName(targetIp), AudioRelayService.DISCOVERY_PORT)
                sock.send(packet)
                Log.d("MainActivity", "Sent CONNECT to $targetIp with device name: $deviceName")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to send connect request: ${e.message}")
            } finally {
                try { sock?.close() } catch (e: Exception) {}
            }
        }.start()
    }

    private fun sendDisconnectRequest(targetIp: String) {
        Thread {
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket()
                val msg = AudioRelayService.DISCONNECT_REQUEST.toByteArray()
                val packet = DatagramPacket(msg, msg.size, InetAddress.getByName(targetIp), AudioRelayService.DISCOVERY_PORT)
                sock.send(packet)
                Log.d("MainActivity", "Sent DISCONNECT to $targetIp")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to send disconnect request: ${e.message}")
            } finally {
                try { sock?.close() } catch (e: Exception) {}
            }
        }.start()
    }
    
    private fun sendConnectionResponse(targetIp: String, approved: Boolean) {
        Thread {
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket()
                val msg = if (approved) "AURELAY_ACCEPT" else "AURELAY_REJECT"
                val packet = DatagramPacket(msg.toByteArray(), msg.length, InetAddress.getByName(targetIp), AudioRelayService.DISCOVERY_PORT)
                sock.send(packet)
                Log.d("MainActivity", "Sent connection response: $msg to $targetIp")
                
                // Update local UI state if accepted (receiver side)
                if (approved) {
                    runOnUiThread {
                        connectionState = true
                        clientIpState = targetIp
                        Log.d("MainActivity", "Receiver: Updated local connection state to connected with $targetIp")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to send connection response: ${e.message}")
            } finally {
                try { sock?.close() } catch (e: Exception) {}
            }
        }.start()
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

// Helper function to get user-friendly device name
fun getDeviceName(): String {
    return try {
        // Try to get marketing name first (e.g., "Redmi Note 14 5G")
        val marketingName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Build.DEVICE
        } else {
            null
        }
        
        // Fallback: Use MANUFACTURER + MODEL or just MODEL
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
        val model = Build.MODEL ?: "Android Device"
        
        when {
            // If model already contains manufacturer name, just use model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            // Otherwise combine manufacturer + model
            manufacturer.isNotEmpty() -> "$manufacturer $model"
            else -> model
        }
    } catch (e: Exception) {
        "Android Device"
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

// Paired device data class
data class PairedDevice(
    val name: String,
    val ip: String,
    val port: Int
)

// Helper functions for paired devices
fun getPairedDevices(context: Context): List<PairedDevice> {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val json = prefs.getString("paired_devices", "[]") ?: "[]"
    return try {
        val devices = mutableListOf<PairedDevice>()
        // Simple JSON parsing (manual to avoid dependencies)
        if (json.startsWith("[") && json.endsWith("]")) {
            val content = json.substring(1, json.length - 1)
            if (content.isNotEmpty()) {
                val items = content.split("},{")
                items.forEach { item ->
                    val cleaned = item.replace("{", "").replace("}", "")
                    val parts = cleaned.split(",")
                    var name = ""
                    var ip = ""
                    var port = 5000
                    parts.forEach { part ->
                        val kv = part.split(":")
                        if (kv.size == 2) {
                            val key = kv[0].trim().replace("\"", "")
                            val value = kv[1].trim().replace("\"", "")
                            when (key) {
                                "name" -> name = value
                                "ip" -> ip = value
                                "port" -> port = value.toIntOrNull() ?: 5000
                            }
                        }
                    }
                    if (name.isNotEmpty() && ip.isNotEmpty()) {
                        devices.add(PairedDevice(name, ip, port))
                    }
                }
            }
        }
        devices
    } catch (e: Exception) {
        Log.e("Aurelay", "Failed to parse paired devices: ${e.message}")
        emptyList()
    }
}

fun savePairedDevice(context: Context, device: PairedDevice) {
    val devices = getPairedDevices(context).toMutableList()
    // Remove if already exists
    devices.removeAll { it.ip == device.ip }
    // Add new
    devices.add(device)
    savePairedDevices(context, devices)
}

fun removePairedDevice(context: Context, ip: String) {
    val devices = getPairedDevices(context).toMutableList()
    devices.removeAll { it.ip == ip }
    savePairedDevices(context, devices)
}

fun savePairedDevices(context: Context, devices: List<PairedDevice>) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    // Build JSON manually
    val json = devices.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ","
    ) { device ->
        "{\"name\":\"${device.name}\",\"ip\":\"${device.ip}\",\"port\":${device.port}}"
    }
    prefs.edit().putString("paired_devices", json).apply()
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AurelayApp(
    context: Context,
    isClientConnected: Boolean,
    clientIp: String,
    audioLevels: FloatArray = FloatArray(24) { 0f },
    pendingConnectionRequest: Pair<String, String>? = null,
    onConnectionResponse: (Boolean) -> Unit = {},
    onClientIpSelected: (String) -> Unit
) {
    val deviceIp = remember { getDeviceIpAddress(context) }
    val port = "5000" // Fixed port matching the service
    
    // Get preferences
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val autoStart = prefs.getBoolean("auto_start_service", false)
    val showVisualizer = prefs.getBoolean("show_visualizer", true)
    val showVolumeSlider = prefs.getBoolean("show_volume_slider", true)
    val requireConnectionConfirm = prefs.getBoolean("require_connection_confirm", true)
    val themeMode = prefs.getString("theme_mode", "system") ?: "system"
    val useDynamicColors = prefs.getBoolean("use_dynamic_colors", true)
    var audioOutputMode by remember { mutableStateOf(prefs.getString("audio_output_mode", "remote_only") ?: "remote_only") }

    // --- STATE ---
    var isBroadcastMode by remember { mutableStateOf(false) } // Default: Receiver Mode
    var isServiceRunning by remember { mutableStateOf(autoStart) } // Service state based on preference
    var volume by remember { mutableFloatStateOf(0.8f) }
    var isMuted by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var connectingToIp by remember { mutableStateOf("") } // Track which device we're connecting to

    // Only initialize media projection components when not in preview mode
    val isPreview = LocalInspectionMode.current
    val mediaProjectionManager = if (!isPreview) {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    } else null
    
    val startMediaProjection = if (!isPreview) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                // Check if a receiver is selected
                if (clientIp.isEmpty()) {
                    Toast.makeText(context, "Please select a receiver device first", Toast.LENGTH_LONG).show()
                    return@rememberLauncherForActivityResult
                }
                
                val intent = Intent(context, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_START
                    putExtra(AudioCaptureService.EXTRA_RESULT_DATA, result.data)
                    putExtra(AudioCaptureService.EXTRA_TARGET_IP, clientIp)
                    putExtra(AudioCaptureService.EXTRA_TARGET_PORT, 5000)
                    putExtra(AudioCaptureService.EXTRA_AUDIO_OUTPUT_MODE, audioOutputMode)
                }
                ContextCompat.startForegroundService(context, intent)
                isServiceRunning = true
            }
        }
    } else null
    
    // Reset selection when service stops unexpectedly
    LaunchedEffect(isClientConnected) {
        if (!isClientConnected && isBroadcastMode && isServiceRunning) {
            // Connection was lost
            isServiceRunning = false
        }
    }
    
    // Clear connecting state when connection is established or failed
    LaunchedEffect(isClientConnected, clientIp, isServiceRunning) {
        if (isServiceRunning || clientIp.isEmpty()) {
            connectingToIp = ""
        }
    }

    val recordAudioPermissionLauncher = if (!isPreview) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mediaProjectionManager?.let { manager ->
                        startMediaProjection?.launch(manager.createScreenCaptureIntent())
                    }
                }
            } else {
                 Toast.makeText(context, "Audio recording permission is required to broadcast audio.", Toast.LENGTH_LONG).show()
            }
        }
    } else null

    // Dynamic colors based on connection state
    val statusColor by animateColorAsState(
        if (isClientConnected && !isBroadcastMode) MaterialTheme.colorScheme.primary
        else if (isBroadcastMode && isServiceRunning) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.error,
        label = "colorState"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Aurelay", fontWeight = FontWeight.Bold) },
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

            // Mode Toggle Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Receiver",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (!isBroadcastMode) FontWeight.Bold else FontWeight.Normal,
                    color = if (!isBroadcastMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = isBroadcastMode,
                    onCheckedChange = {
                        if (it) { // Trying to switch to Broadcast (Sender) Mode
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                Toast.makeText(context, "Audio Capture requires Android 10+", Toast.LENGTH_LONG).show()
                                isBroadcastMode = false // Prevent switch
                            } else {
                                isBroadcastMode = true
                                // Stop Receiver Service if running when switching modes?
                                // Assuming we stop previous mode service to avoid conflicts or confusion
                                if (isServiceRunning) {
                                    val intent = Intent(context, AudioRelayService::class.java)
                                    context.stopService(intent)
                                    isServiceRunning = false
                                }
                            }
                        } else { // Switching back to Receiver Mode
                            isBroadcastMode = false
                             // Stop Sender Service if running
                            if (isServiceRunning) {
                                val intent = Intent(context, AudioCaptureService::class.java)
                                context.stopService(intent)
                                isServiceRunning = false
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Text(
                    text = "Sender",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isBroadcastMode) FontWeight.Bold else FontWeight.Normal,
                    color = if (isBroadcastMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. HEADER SECTION: Status Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f))
                        .then(
                            if (isClientConnected && !isBroadcastMode) Modifier.clickable {
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
                        imageVector = if (isBroadcastMode) Icons.Rounded.PowerSettingsNew
                                     else if (!isClientConnected) Icons.Rounded.LinkOff
                                     else if (isMuted) Icons.Rounded.HeadsetOff 
                                     else Icons.Rounded.Headphones,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        modifier = Modifier.size(56.dp),
                        tint = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))

                val statusText = if (isBroadcastMode) {
                    if (isServiceRunning) "Broadcasting Audio" else "Ready to Broadcast"
                } else {
                    if (isClientConnected) {
                         if (isMuted) "Muted" else "Streaming Audio"
                    } else if (isServiceRunning) "Waiting for Connection" else "Service Stopped"
                }
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isBroadcastMode) {
                         if (isServiceRunning) "Listening on port $port" else "Click Start to stream"
                    } else {
                         if (isClientConnected) "Connected: $clientIp:$port" else "Press Start to begin listening"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. MIDDLE SECTION: Visualizer OR Connection Info
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(visible = isClientConnected && !isBroadcastMode && showVisualizer) {
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

                androidx.compose.animation.AnimatedVisibility(visible = (!isClientConnected && !isBroadcastMode && isServiceRunning) || isBroadcastMode) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 16.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (!isBroadcastMode) {
                                    Text(
                                        "Connection Details",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    Spacer(Modifier.height(16.dp))
                                    
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(0.3f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                    
                                    Spacer(Modifier.height(20.dp))

                                    Text(
                                        "Device IP Address",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(Modifier.height(6.dp))
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        port,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(Modifier.height(20.dp))
                                    
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(0.3f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                    
                                    Spacer(Modifier.height(14.dp))

                                    Text(
                                        if (isBroadcastMode) "Connect from another device to hear audio" else "Use these details to connect",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                } else {
                                    // Broadcast mode: show paired and nearby discovered devices
                                    val pairedDevices = remember { mutableStateListOf<PairedDevice>().apply { addAll(getPairedDevices(context)) } }
                                    val discoveredDevices = remember { mutableStateListOf<Triple<String, Int, String>>() }
                                    var isDiscovering by remember { mutableStateOf(false) }
                                    val coroutineScope = rememberCoroutineScope()

                                    fun doDiscovery() {
                                        if (isDiscovering) return
                                        isDiscovering = true
                                        discoveredDevices.clear()
                                        coroutineScope.launch {
                                            withContext(Dispatchers.IO) {
                                                var sock: DatagramSocket? = null
                                                try {
                                                    sock = DatagramSocket()
                                                    sock.broadcast = true
                                                    sock.soTimeout = 500
                                                    val msg = AudioRelayService.DISCOVERY_REQUEST.toByteArray()
                                                    val packet = DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), AudioRelayService.DISCOVERY_PORT)
                                                    try { sock.send(packet) } catch (e: Exception) { }

                                                    val end = System.currentTimeMillis() + 10000 // 10 seconds
                                                    val buf = ByteArray(1024)
                                                    while (System.currentTimeMillis() < end) {
                                                        try {
                                                            val resp = DatagramPacket(buf, buf.size)
                                                            sock.receive(resp)
                                                            val text = String(resp.data, 0, resp.length).trim()
                                                            if (text.startsWith(AudioRelayService.DISCOVERY_RESPONSE)) {
                                                                val parts = text.split(';')
                                                                val respPort = parts.getOrNull(1)?.toIntOrNull() ?: 5000
                                                                val name = parts.getOrNull(2) ?: "Aurelay"
                                                                val ip = resp.address.hostAddress ?: ""
                                                                // Filter out self device IP
                                                                if (ip != deviceIp) {
                                                                    val triple = Triple(ip, respPort, name)
                                                                    if (!discoveredDevices.contains(triple)) discoveredDevices.add(triple)
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            // ignore
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("Aurelay", "Discovery failed: ${e.message}")
                                                } finally {
                                                    try { sock?.close() } catch (e: Exception) {}
                                                    isDiscovering = false
                                                }
                                            }
                                        }
                                    }

                                    LaunchedEffect(isBroadcastMode) {
                                        if (isBroadcastMode) doDiscovery()
                                    }
                                    
                                    // Paired Devices Section - Redesigned
                                    if (pairedDevices.isNotEmpty()) {
                                        Text(
                                            "Paired Devices",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        
                                        Spacer(Modifier.height(12.dp))
                                        
                                        pairedDevices.forEach { device ->
                                            val isConnectedToThis = clientIp == device.ip && isServiceRunning
                                            val isConnectingToThis = connectingToIp == device.ip
                                            
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isConnectedToThis) 
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                else 
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                tonalElevation = 2.dp
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        modifier = Modifier.weight(1f, fill = false),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Link,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(Modifier.width(12.dp))
                                                        Column {
                                                            Text(
                                                                device.name,
                                                                fontWeight = FontWeight.SemiBold,
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                maxLines = 1
                                                            )
                                                            Text(
                                                                "${device.ip}:${device.port}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    
                                                    Spacer(Modifier.width(12.dp))
                                                    
                                                    if (isConnectedToThis) {
                                                        FilledTonalButton(
                                                            onClick = {
                                                                isServiceRunning = false
                                                                val intent = Intent(context, AudioCaptureService::class.java)
                                                                intent.action = AudioCaptureService.ACTION_STOP
                                                                context.startService(intent)
                                                                onClientIpSelected("")
                                                                connectingToIp = ""
                                                            },
                                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                            )
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Close,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("Stop")
                                                        }
                                                    } else if (isConnectingToThis) {
                                                        FilledTonalButton(
                                                            onClick = { },
                                                            enabled = false
                                                        ) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(16.dp),
                                                                strokeWidth = 2.dp
                                                            )
                                                            Spacer(Modifier.width(6.dp))
                                                            Text("Connecting")
                                                        }
                                                    } else {
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            IconButton(
                                                                onClick = {
                                                                    pairedDevices.remove(device)
                                                                    removePairedDevice(context, device.ip)
                                                                },
                                                                modifier = Modifier.size(36.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.LinkOff,
                                                                    contentDescription = "Unpair",
                                                                    tint = MaterialTheme.colorScheme.error,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                            FilledTonalButton(
                                                                onClick = {
                                                                    connectingToIp = device.ip
                                                                    onClientIpSelected(device.ip)
                                                                }
                                                            ) {
                                                                Text("Connect")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Spacer(Modifier.height(20.dp))
                                    }

                                    Text(
                                        "Nearby Devices",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    Spacer(Modifier.height(12.dp))

                                    if (isDiscovering) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                                Spacer(Modifier.height(12.dp))
                                                Text(
                                                    "Searching...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        // Filter out paired devices from nearby list
                                        val pairedIps = pairedDevices.map { it.ip }.toSet()
                                        val nearbyDevices = discoveredDevices.filter { (ip, _, _) -> ip !in pairedIps }
                                        
                                        if (nearbyDevices.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 20.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DevicesOther,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(48.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(
                                                        "No devices found",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(Modifier.height(12.dp))
                                                    FilledTonalButton(
                                                        onClick = { doDiscovery() }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Refresh,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("Refresh")
                                                    }
                                                }
                                            }
                                        } else {
                                            nearbyDevices.forEach { item ->
                                                val (ip, p, name) = item
                                                val isConnectedToThis = clientIp == ip && isServiceRunning
                                                val isConnectingToThis = connectingToIp == ip
                                                
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp),
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isConnectedToThis) 
                                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                                    else 
                                                        MaterialTheme.colorScheme.surface,
                                                    tonalElevation = 1.dp,
                                                    border = if (!isConnectedToThis) 
                                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                                    else null
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.weight(1f, fill = false),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Wifi,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.secondary,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Spacer(Modifier.width(12.dp))
                                                            Column {
                                                                Text(
                                                                    name,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    style = MaterialTheme.typography.bodyLarge,
                                                                    maxLines = 1
                                                                )
                                                                Text(
                                                                    "$ip:$p",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                        
                                                        Spacer(Modifier.width(12.dp))
                                                        
                                                        if (isConnectedToThis) {
                                                            FilledTonalButton(
                                                                onClick = {
                                                                    isServiceRunning = false
                                                                    val intent = Intent(context, AudioCaptureService::class.java)
                                                                    intent.action = AudioCaptureService.ACTION_STOP
                                                                    context.startService(intent)
                                                                    onClientIpSelected("")
                                                                    connectingToIp = ""
                                                                },
                                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                                )
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Close,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Stop")
                                                            }
                                                        } else if (isConnectingToThis) {
                                                            FilledTonalButton(
                                                                onClick = { },
                                                                enabled = false
                                                            ) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(16.dp),
                                                                    strokeWidth = 2.dp
                                                                )
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("Connecting")
                                                            }
                                                        } else {
                                                            FilledTonalButton(
                                                                onClick = {
                                                                    connectingToIp = ip
                                                                    onClientIpSelected(ip)
                                                                }
                                                            ) {
                                                                Text("Connect")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        if (isServiceRunning) {
                            Text(
                                if (isBroadcastMode) "Broadcasting..." else "Listening for incoming connections...",
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
                    if (isServiceRunning) {
                        // STOP
                        isServiceRunning = false
                        if (isBroadcastMode) {
                             // Send disconnect to currently connected receiver
                             if (clientIp.isNotEmpty()) {
                                 onClientIpSelected("")
                             }
                             val intent = Intent(context, AudioCaptureService::class.java)
                             intent.action = AudioCaptureService.ACTION_STOP
                             context.startService(intent)
                        } else {
                            val intent = Intent(context, AudioRelayService::class.java)
                            context.stopService(intent)
                        }
                    } else {
                        // START
                        if (isBroadcastMode) {
                            // Check if receiver is selected first
                            if (clientIp.isEmpty()) {
                                Toast.makeText(context, "Please select a receiver device first", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                recordAudioPermissionLauncher?.launch(android.Manifest.permission.RECORD_AUDIO)
                            } else {
                                Toast.makeText(context, "Audio Capture requires Android 10+", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            isServiceRunning = true
                            val intent = Intent(context, AudioRelayService::class.java)
                            ContextCompat.startForegroundService(context, intent)
                        }
                    }
                },
                enabled = !isBroadcastMode || isServiceRunning || clientIp.isNotEmpty(),
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
    
    // Connection Request Confirmation Dialog
    pendingConnectionRequest?.let { (ip, name) ->
        var rememberDevice by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { onConnectionResponse(false) },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Incoming Connection",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "$name wants to connect to your device.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "IP: $ip",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Do you want to allow this connection?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Remember device checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberDevice = !rememberDevice }
                            .padding(8.dp)
                    ) {
                        Checkbox(
                            checked = rememberDevice,
                            onCheckedChange = { rememberDevice = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Remember this device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        onConnectionResponse(true)
                        if (rememberDevice) {
                            savePairedDevice(context, PairedDevice(name, ip, 5000))
                            Toast.makeText(context, "Device saved to paired devices", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = "Accept")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onConnectionResponse(false) }) {
                    Text(text = "Reject")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
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
                        text = "About Aurelay",
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
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Text(
                        text = "Stream audio wirelessly between Android devices and desktop over your local network.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Text(
                        text = "Developer",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Ishu Singh",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Text(
                        text = "© 2025 Aurelay Audio Relay. Open Source Project.",
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
        var tempRequireConnectionConfirm by remember { mutableStateOf(prefs.getBoolean("require_connection_confirm", true)) }
        var tempThemeMode by remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
        var tempUseDynamicColors by remember { mutableStateOf(prefs.getBoolean("use_dynamic_colors", true)) }
        var tempAudioOutputMode by remember { mutableStateOf(prefs.getString("audio_output_mode", "this_device") ?: "this_device") }
        
        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val dialogHeight = screenHeight * 0.65f  // Use 65% for better proportions
        
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dialogHeight)
                        .verticalScroll(rememberScrollState()),
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
                    
                    // Connection confirmation setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Require Connection Confirmation",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Ask before accepting incoming connections",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tempRequireConnectionConfirm,
                            onCheckedChange = { tempRequireConnectionConfirm = it }
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
                    
                    // Audio Output setting (Sender Mode Only)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Audio Output (Sender)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isBroadcastMode) {
                                when (tempAudioOutputMode) {
                                    "this_device" -> "This device only"
                                    "remote_only" -> "Remote device only"
                                    "both_devices" -> "Both devices"
                                    else -> "Select output device"
                                }
                            } else {
                                "Available in Sender mode"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isBroadcastMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = tempAudioOutputMode == "this_device",
                                onClick = { if (isBroadcastMode) tempAudioOutputMode = "this_device" },
                                label = { Text("This Device", style = MaterialTheme.typography.labelMedium) },
                                enabled = isBroadcastMode,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = tempAudioOutputMode == "remote_only",
                                onClick = { if (isBroadcastMode) tempAudioOutputMode = "remote_only" },
                                label = { Text("Remote", style = MaterialTheme.typography.labelMedium) },
                                enabled = isBroadcastMode,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = tempAudioOutputMode == "both_devices",
                                onClick = { if (isBroadcastMode) tempAudioOutputMode = "both_devices" },
                                label = { Text("Both", style = MaterialTheme.typography.labelMedium) },
                                enabled = isBroadcastMode,
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
                            putBoolean("require_connection_confirm", tempRequireConnectionConfirm)
                            putString("theme_mode", tempThemeMode)
                            putBoolean("use_dynamic_colors", tempUseDynamicColors)
                            putString("audio_output_mode", tempAudioOutputMode)
                            apply()
                        }
                        
                        // Restart service if audio output mode changed and service is running in broadcast mode
                        val modeChanged = audioOutputMode != tempAudioOutputMode
                        audioOutputMode = tempAudioOutputMode
                        
                        showSettingsDialog = false
                        
                        if (modeChanged && isBroadcastMode && isServiceRunning && clientIp.isNotEmpty()) {
                            // Stop current service
                            val stopIntent = Intent(context, AudioCaptureService::class.java)
                            stopIntent.action = AudioCaptureService.ACTION_STOP
                            context.startService(stopIntent)
                            isServiceRunning = false
                            
                            Toast.makeText(context, "Audio output changed. Please restart streaming to apply.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
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
fun PreviewAurelayApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        // For preview, create a mock context
        AurelayApp(
            context = androidx.compose.ui.platform.LocalContext.current,
            isClientConnected = false,
            clientIp = "",
                audioLevels = FloatArray(24) { 0.5f },
                onClientIpSelected = {}
        )
    }
}
