package io.github.aurynk.audiorelay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    
    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: AndroidIntent?) {
            intent ?: return
            val connected = intent.getBooleanExtra("connected", false)
            val ip = intent.getStringExtra("client_ip") ?: ""
            Log.d("MainActivity", "Broadcast received: connected=$connected, ip=$ip")
            connectionState = connected
            clientIpState = ip
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure the notification channel exists and start the foreground service.
        createNotificationChannel()
        val intent = Intent(this, AudioRelayService::class.java)
        ContextCompat.startForegroundService(this, intent)
        
        // Register broadcast receiver with proper flags for all Android versions
        val filter = IntentFilter("io.github.aurynk.CLIENT_CONNECTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(connectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(connectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(connectionReceiver, filter)
        }
        Log.d("MainActivity", "Broadcast receiver registered for CLIENT_CONNECTION")

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AurynkReceiverApp(
                        context = this,
                        isClientConnected = connectionState,
                        clientIp = clientIpState
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
    clientIp: String
) {
    val deviceIp = remember { getDeviceIpAddress(context) }
    val port = "5000" // Fixed port matching the service

    // --- STATE ---
    var isServiceRunning by remember { mutableStateOf(true) } // Service auto-starts on app launch
    var volume by remember { mutableFloatStateOf(0.8f) }

    // Dynamic colors based on connection state
    val statusColor by animateColorAsState(
        if (isClientConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        label = "colorState"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Aurynk Receiver", fontWeight = FontWeight.Bold) },
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
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isClientConnected) Icons.Rounded.Headphones else Icons.Rounded.LinkOff,
                        contentDescription = "Status",
                        modifier = Modifier.size(56.dp),
                        tint = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (isClientConnected) "Streaming Audio" else if (isServiceRunning) "Waiting for Connection" else "Service Stopped",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isClientConnected) "Connected: $clientIp:$port" else "Ready to receive on $deviceIp:$port",
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
                androidx.compose.animation.AnimatedVisibility(visible = isClientConnected) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Mock Audio Visualizer
                        FakeAudioVisualizer()
                        Spacer(Modifier.height(32.dp))
                        // Volume Slider
                        Text(
                            "Local Volume",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = !isClientConnected) {
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
                    text = if (isServiceRunning) "Stop Service" else "Start Service",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// A fake visualizer component just for the mockup look
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
            clientIp = ""
        )
    }
}