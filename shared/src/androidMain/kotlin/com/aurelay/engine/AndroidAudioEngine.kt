package com.aurelay.engine

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Android implementation of AudioEngine.
 * Note: This is a SENDER implementation that discovers receivers and sends audio to them.
 * It integrates with the existing AudioCaptureService (not AudioRelayService which is for receiving).
 */
@SuppressLint("UnspecifiedRegisterReceiverFlag")
class AndroidAudioEngine(
    private val context: Context
) : AudioEngine {
    
    private val _streamState = MutableStateFlow(StreamState.Idle)
    override val streamState: StateFlow<StreamState> = _streamState.asStateFlow()
    
    private val _availableDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    override val availableDevices: StateFlow<List<AudioDevice>> = _availableDevices.asStateFlow()
    
    private val _selectedDevice = MutableStateFlow<AudioDevice?>(null)
    override val selectedDevice: StateFlow<AudioDevice?> = _selectedDevice.asStateFlow()
    
    private val _discoveredReceivers = MutableStateFlow<List<Receiver>>(emptyList())
    override val discoveredReceivers: StateFlow<List<Receiver>> = _discoveredReceivers.asStateFlow()
    
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    override val logs: StateFlow<List<String>> = _logs.asStateFlow()
    
    private var discoverySocket: DatagramSocket? = null
    private var discoveryThread: Thread? = null
    
    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                "com.devindeed.aurelay.CLIENT_CONNECTION" -> {
                    val connected = intent.getBooleanExtra("connected", false)
                    val ip = intent.getStringExtra("client_ip") ?: ""
                    
                    if (connected) {
                        _streamState.value = StreamState.Streaming
                        addLog("Connected to receiver at $ip")
                    } else {
                        _streamState.value = StreamState.Idle
                        addLog("Disconnected from receiver")
                    }
                }
            }
        }
    }
    
    init {
        addLog("Android AudioEngine initialized (Sender)")
        
        // Register broadcast receiver for connection state updates
        val filter = IntentFilter().apply {
            addAction("com.devindeed.aurelay.CLIENT_CONNECTION")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(connectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                connectionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        
        // Initialize with default device
        val defaultDevice = AudioDevice(
            id = "default",
            name = "System Audio",
            isDefault = true
        )
        _availableDevices.value = listOf(defaultDevice)
        _selectedDevice.value = defaultDevice
    }
    
    override suspend fun startStreaming(
        receiver: Receiver,
        device: AudioDevice?,
        transportMode: TransportMode
    ): Result<Unit> {
        return try {
            addLog("Starting stream to ${receiver.name} (${receiver.address}:${receiver.port})")
            _streamState.value = StreamState.Starting
            
            // Send connection request to receiver
            val deviceName = Build.MODEL.ifEmpty { "Android Sender" }
            sendConnectionRequest(receiver.address, deviceName)
            
            // Note: Connection approval and actual streaming will be handled by
            // AudioCaptureService via the existing flow. The state will be updated
            // by the connectionReceiver when the service broadcasts state changes.
            
            addLog("Connection request sent, awaiting approval...")
            Result.success(Unit)
        } catch (e: Exception) {
            _streamState.value = StreamState.Error
            addLog("Error starting stream: ${e.message}")
            Result.failure(e)
        }
    }
    
    private fun sendConnectionRequest(targetIp: String, deviceName: String) {
        Thread {
            try {
                val socket = DatagramSocket()
                val msg = "AURELAY_CONNECT;$deviceName".toByteArray()
                val packet = DatagramPacket(
                    msg, msg.size,
                    InetAddress.getByName(targetIp),
                    5002 // DISCOVERY_PORT
                )
                socket.send(packet)
                socket.close()
                Log.d("AndroidAudioEngine", "Connection request sent to $targetIp")
            } catch (e: Exception) {
                Log.e("AndroidAudioEngine", "Failed to send connection request: ${e.message}")
            }
        }.start()
    }
    
    override suspend fun stopStreaming(): Result<Unit> {
        return try {
            addLog("Stopping stream")
            _streamState.value = StreamState.Stopping
            
            // Send disconnect request
            _discoveredReceivers.value.firstOrNull()?.let { receiver ->
                val socket = DatagramSocket()
                val msg = "AURELAY_DISCONNECT".toByteArray()
                val packet = DatagramPacket(
                    msg, msg.size,
                    InetAddress.getByName(receiver.address),
                    5002
                )
                socket.send(packet)
                socket.close()
            }
            
            _streamState.value = StreamState.Idle
            addLog("Stream stopped")
            Result.success(Unit)
        } catch (e: Exception) {
            _streamState.value = StreamState.Error
            addLog("Error stopping stream: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun refreshDevices(): Result<Unit> {
        // Android sender typically uses system audio capture (MediaProjection)
        // No additional device selection needed
        addLog("Using system audio capture")
        return Result.success(Unit)
    }
    
    override suspend fun startDiscovery(): Result<Unit> {
        return try {
            addLog("Starting receiver discovery...")
            
            discoveryThread = Thread {
                try {
                    val socket = DatagramSocket()
                    socket.broadcast = true
                    
                    // Send discovery request
                    val msg = "AURELAY_DISCOVER".toByteArray()
                    val packet = DatagramPacket(
                        msg, msg.size,
                        InetAddress.getByName("255.255.255.255"),
                        5002
                    )
                    socket.send(packet)
                    addLog("Discovery broadcast sent")
                    
                    // Listen for responses
                    socket.soTimeout = 3000
                    val receivers = mutableListOf<Receiver>()
                    val buf = ByteArray(1024)
                    
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < 3000) {
                        try {
                            val respPacket = DatagramPacket(buf, buf.size)
                            socket.receive(respPacket)
                            val text = String(respPacket.data, 0, respPacket.length).trim()
                            
                            if (text.startsWith("AURELAY_RESPONSE")) {
                                val parts = text.split(";")
                                if (parts.size >= 3) {
                                    val port = parts[1].toIntOrNull() ?: 5000
                                    val name = parts[2]
                                    val address = respPacket.address.hostAddress ?: continue
                                    
                                    receivers.add(
                                        Receiver(
                                            id = address,
                                            name = name,
                                            address = address,
                                            port = port,
                                            isAvailable = true
                                        )
                                    )
                                    addLog("Found receiver: $name at $address")
                                }
                            }
                        } catch (_: Exception) {
                            // Timeout or other error, continue
                        }
                    }
                    
                    socket.close()
                    _discoveredReceivers.value = receivers
                    addLog("Discovery complete: found ${receivers.size} receiver(s)")
                    
                } catch (e: Exception) {
                    addLog("Discovery error: ${e.message}")
                    Log.e("AndroidAudioEngine", "Discovery failed", e)
                }
            }
            discoveryThread?.start()
            
            Result.success(Unit)
        } catch (e: Exception) {
            addLog("Error starting discovery: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun stopDiscovery(): Result<Unit> {
        discoveryThread?.interrupt()
        discoverySocket?.close()
        addLog("Discovery stopped")
        return Result.success(Unit)
    }
    
    override fun dispose() {
        try {
            context.unregisterReceiver(connectionReceiver)
        } catch (e: Exception) {
            Log.e("AndroidAudioEngine", "Error unregistering receiver", e)
        }
        discoveryThread?.interrupt()
        discoverySocket?.close()
        addLog("Android AudioEngine disposed")
    }
    
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        _logs.value = _logs.value + "[$timestamp] $message"
        
        // Keep only last 100 logs
        if (_logs.value.size > 100) {
            _logs.value = _logs.value.takeLast(100)
        }
    }
}
