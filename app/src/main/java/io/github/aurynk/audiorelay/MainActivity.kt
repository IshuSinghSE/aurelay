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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
        const val ACTION_CONNECTION_REQUEST = "io.github.aurynk.CONNECTION_REQUEST"
        const val ACTION_CONNECTION_RESPONSE = "io.github.aurynk.CONNECTION_RESPONSE"
        const val EXTRA_APPROVED = "approved"
    }
    
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
                ACTION_CONNECTION_REQUEST -> {
                    val ip = intent.getStringExtra("client_ip") ?: ""
                    val name = intent.getStringExtra("client_name") ?: "Unknown Device"
                    Log.d("MainActivity", "Connection request from: $name ($ip)")
                    
                    ctx ?: return
                    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                    val requireConfirm = prefs.getBoolean("require_connection_confirm", true)
                    
                    if (requireConfirm) {
                        pendingConnectionRequest = Pair(ip, name)
                    } else {
                        // Auto-accept
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
        
        // Make status bar and navigation bar transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        
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
            addAction(ACTION_CONNECTION_REQUEST)
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
                    AurynkApp(
                        context = this,
                        isClientConnected = connectionState,
                        clientIp = clientIpState,
                        audioLevels = audioLevels,
                        pendingConnectionRequest = pendingConnectionRequest,
                        onConnectionResponse = { approved ->
                            pendingConnectionRequest?.let { (ip, _) ->
                                sendConnectionResponse(ip, approved)
                                if (!approved) {
                                    Toast.makeText(this, "Connection rejected", Toast.LENGTH_SHORT).show()
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
                val msg = if (approved) "AURYNK_ACCEPT" else "AURYNK_REJECT"
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AurynkApp(
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
    val audioOutputMode = prefs.getString("audio_output_mode", "receiver") ?: "receiver"
    val themeMode = prefs.getString("theme_mode", "system") ?: "system"
    val useDynamicColors = prefs.getBoolean("use_dynamic_colors", true)

    // --- STATE ---
    var isBroadcastMode by remember { mutableStateOf(false) } // Default: Receiver Mode
    var isServiceRunning by remember { mutableStateOf(autoStart) } // Service state based on preference
    var volume by remember { mutableFloatStateOf(0.8f) }
    var isMuted by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val startMediaProjection = rememberLauncherForActivityResult(
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
            }
            ContextCompat.startForegroundService(context, intent)
            isServiceRunning = true
        }
    }
    
    // Reset selection when service stops unexpectedly
    LaunchedEffect(isClientConnected) {
        if (!isClientConnected && isBroadcastMode && isServiceRunning) {
            // Connection was lost
            isServiceRunning = false
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
            }
        } else {
             Toast.makeText(context, "Audio recording permission is required to broadcast audio.", Toast.LENGTH_LONG).show()
        }
    }

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
                modifier = Modifier.padding(vertical = 16.dp)
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
                    .padding(vertical = 16.dp),
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
                            .padding(horizontal = 20.dp, vertical = 16.dp)
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
                                    // Broadcast mode: show nearby discovered devices
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

                                                    val end = System.currentTimeMillis() + 2500
                                                    val buf = ByteArray(1024)
                                                    while (System.currentTimeMillis() < end) {
                                                        try {
                                                            val resp = DatagramPacket(buf, buf.size)
                                                            sock.receive(resp)
                                                            val text = String(resp.data, 0, resp.length).trim()
                                                            if (text.startsWith(AudioRelayService.DISCOVERY_RESPONSE)) {
                                                                val parts = text.split(';')
                                                                val respPort = parts.getOrNull(1)?.toIntOrNull() ?: 5000
                                                                val name = parts.getOrNull(2) ?: "Aurynk"
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
                                                    Log.e("Aurynk", "Discovery failed: ${e.message}")
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

                                    Text(
                                        "Nearby Devices",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    Spacer(Modifier.height(16.dp))
                                    
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(0.3f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                    
                                    Spacer(Modifier.height(16.dp))

                                    if (isDiscovering) {
                                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "Searching on local network...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        if (discoveredDevices.isEmpty()) {
                                            Text(
                                                "No nearby receivers found.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Button(
                                                onClick = { doDiscovery() },
                                                modifier = Modifier.fillMaxWidth(0.6f)
                                            ) {
                                                Text("Refresh")
                                            }
                                        } else {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                discoveredDevices.forEachIndexed { idx, item ->
                                                    val (ip, p, name) = item
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 10.dp, horizontal = 6.dp)
                                                            .clickable {
                                                                onClientIpSelected(ip)
                                                                Toast.makeText(context, "Selected $name ($ip:$p)", Toast.LENGTH_SHORT).show()
                                                            },
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                name,
                                                                fontWeight = FontWeight.SemiBold,
                                                                style = MaterialTheme.typography.bodyLarge
                                                            )
                                                            Spacer(Modifier.height(3.dp))
                                                            Text(
                                                                "$ip:$p",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        Spacer(Modifier.width(8.dp))
                                                        // Show connected state only if both selected AND service is running
                                                        val isConnectedToThis = clientIp == ip && isServiceRunning
                                                        if (isConnectedToThis) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    // Stop the service when disconnecting
                                                                    isServiceRunning = false
                                                                    val intent = Intent(context, AudioCaptureService::class.java)
                                                                    intent.action = AudioCaptureService.ACTION_STOP
                                                                    context.startService(intent)
                                                                    onClientIpSelected("")
                                                                    Toast.makeText(context, "Disconnected from $name", Toast.LENGTH_SHORT).show()
                                                                },
                                                                modifier = Modifier.wrapContentWidth(),
                                                                colors = ButtonDefaults.outlinedButtonColors(
                                                                    contentColor = MaterialTheme.colorScheme.error
                                                                )
                                                            ) {
                                                                Text("Disconnect", style = MaterialTheme.typography.labelMedium)
                                                            }
                                                        } else if (clientIp == ip && !isServiceRunning) {
                                                            // Selected but not streaming
                                                            OutlinedButton(
                                                                onClick = {
                                                                    onClientIpSelected("")
                                                                    Toast.makeText(context, "Deselected $name", Toast.LENGTH_SHORT).show()
                                                                },
                                                                modifier = Modifier.wrapContentWidth()
                                                            ) {
                                                                Text("Selected", style = MaterialTheme.typography.labelMedium)
                                                            }
                                                        } else {
                                                            Button(
                                                                onClick = {
                                                                    onClientIpSelected(ip)
                                                                    Toast.makeText(context, "Connected to $name ($ip:$p)", Toast.LENGTH_SHORT).show()
                                                                },
                                                                modifier = Modifier.wrapContentWidth()
                                                            ) {
                                                                Text("Connect", style = MaterialTheme.typography.labelMedium)
                                                            }
                                                        }
                                                    }
                                                    if (idx < discoveredDevices.size - 1) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(horizontal = 6.dp),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                                        )
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
                                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
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
                        text = "$name wants to connect and stream audio to this device.",
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
                }
            },
            confirmButton = {
                Button(
                    onClick = { onConnectionResponse(true) },
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
        var tempRequireConnectionConfirm by remember { mutableStateOf(prefs.getBoolean("require_connection_confirm", true)) }
        var tempAudioOutputMode by remember { mutableStateOf(prefs.getString("audio_output_mode", "receiver") ?: "receiver") }
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
                    modifier = Modifier
                        .fillMaxWidth()
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
                    
                    // Audio output mode setting
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Audio Output",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Where to play received audio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = tempAudioOutputMode == "This Device",
                                onClick = { tempAudioOutputMode = "This Device" },
                                label = { Text("This Device") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = tempAudioOutputMode == "sender",
                                onClick = { tempAudioOutputMode = "sender" },
                                label = { Text("Sender") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = tempAudioOutputMode == "both",
                                onClick = { tempAudioOutputMode = "both" },
                                label = { Text("Both") },
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                            putBoolean("require_connection_confirm", tempRequireConnectionConfirm)
                            putString("audio_output_mode", tempAudioOutputMode)
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
        AurynkApp(
            context = androidx.compose.ui.platform.LocalContext.current,
            isClientConnected = false,
            clientIp = "",
                audioLevels = FloatArray(24) { 0.5f },
                onClientIpSelected = {}
        )
    }
}
