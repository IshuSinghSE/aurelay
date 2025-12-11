package com.aurelay.engine.ffi

import com.aurelay.engine.DesktopAudioEngine
import com.aurelay.engine.AudioDevice
import com.aurelay.engine.Receiver
import com.aurelay.engine.TransportMode
import com.aurelay.engine.StreamState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rust_engine.*

/**
 * Rust Bridge - Desktop AudioEngine implementation with Rust/CPAL integration.
 * This is the ONLY file in the Kotlin codebase allowed to import uniffi.rust_engine.
 * Acts as the translator between the shared UI and the Rust audio backend.
 * 
 * Extends the base DesktopAudioEngine from the shared module and provides
 * full implementation using the Rust audio engine via JNA/UniFFI bindings.
 */
class RustBridge : DesktopAudioEngine() {
    
    private var currentReceiver: Receiver? = null
    
    init {
        addLog("Rust Desktop AudioEngine initialized (CPAL + JNA)")
    }
    
    override suspend fun startStreaming(
        receiver: Receiver,
        device: AudioDevice?,
        transportMode: TransportMode
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            addLog("Starting stream to ${receiver.name} (${receiver.address}:${receiver.port})")
            addLog("Transport mode: ${transportMode.value}")
            _streamState.value = StreamState.Starting
            
            currentReceiver = receiver
            
            // Request connection approval from receiver
            addLog("Requesting connection approval...")
            val response = requestConnectAndWait(
                host = receiver.address,
                timeoutSecs = 10,
                deviceName = "Desktop"
            )
            
            when (response) {
                "accepted" -> {
                    addLog("Connection approved by receiver")
                    
                    // Start streaming with Rust engine
                    val deviceName = device?.name
                    startStream(
                        targetIp = receiver.address,
                        deviceName = deviceName
                    )
                    
                    _streamState.value = StreamState.Streaming
                    addLog("Streaming started successfully")
                    Result.success(Unit)
                }
                "rejected" -> {
                    _streamState.value = StreamState.Error
                    addLog("Connection rejected by receiver")
                    Result.failure(Exception("Connection rejected by receiver"))
                }
                else -> {
                    _streamState.value = StreamState.Error
                    addLog("Connection timeout or error")
                    Result.failure(Exception("Connection timeout"))
                }
            }
        } catch (e: Exception) {
            _streamState.value = StreamState.Error
            addLog("Error starting stream: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun stopStreaming(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            addLog("Stopping stream")
            _streamState.value = StreamState.Stopping
            
            stopStream()
            
            currentReceiver = null
            _streamState.value = StreamState.Idle
            addLog("Stream stopped")
            Result.success(Unit)
        } catch (e: Exception) {
            _streamState.value = StreamState.Error
            addLog("Error stopping stream: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun refreshDevices(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            addLog("Refreshing audio devices (CPAL)")
            
            val cpalDevices = listCpalInputDevices()
            val devices = cpalDevices.mapIndexed { index, name ->
                AudioDevice(
                    id = "cpal_$index",
                    name = name,
                    isDefault = index == 0
                )
            }
            
            _availableDevices.value = devices
            if (devices.isNotEmpty() && _selectedDevice.value == null) {
                _selectedDevice.value = devices.first()
            }
            
            addLog("Found ${devices.size} CPAL audio device(s)")
            
            // Also log pactl devices for reference
            try {
                val pactlOutput = Runtime.getRuntime()
                    .exec("pactl list short sources")
                    .inputStream
                    .bufferedReader()
                    .readText()
                
                addLog("PulseAudio/PipeWire sources available")
            } catch (e: Exception) {
                addLog("pactl not available or error: ${e.message}")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            addLog("Error refreshing devices: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun startDiscovery(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            addLog("Starting receiver discovery (mDNS)")
            
            val discovered = discoverReceivers(timeoutSecs = 3)
            val receivers = discovered.mapNotNull { entry ->
                // Format: "ip:port;name"
                val parts = entry.split(";")
                if (parts.size >= 2) {
                    val ipPort = parts[0].split(":")
                    if (ipPort.size == 2) {
                        Receiver(
                            id = parts[0],
                            name = parts[1],
                            address = ipPort[0],
                            port = ipPort[1].toIntOrNull() ?: 5000,
                            isAvailable = true
                        )
                    } else null
                } else null
            }
            
            _discoveredReceivers.value = receivers
            addLog("Discovered ${receivers.size} receiver(s)")
            
            Result.success(Unit)
        } catch (e: Exception) {
            addLog("Error during discovery: ${e.message}")
            Result.failure(e)
        }
    }
    
    override fun dispose() {
        addLog("Disposing Rust Desktop AudioEngine")
        if (_streamState.value == StreamState.Streaming) {
            stopStream()
        }
    }
    
    /**
     * Get latest logs from Rust engine and merge with Kotlin logs.
     */
    fun refreshNativeLogs() {
        try {
            val nativeLogs = getNativeLogs()
            if (nativeLogs.isNotBlank()) {
                val logLines = nativeLogs.split("\n").filter { it.isNotBlank() }
                logLines.forEach { addLog("[NATIVE] $it") }
            }
        } catch (e: Exception) {
            addLog("Error fetching native logs: ${e.message}")
        }
    }
}
