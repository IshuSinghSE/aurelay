package com.aurelay.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop implementation of AudioEngine.
 * 
 * NOTE: This is a minimal stub that should be extended in the desktopApp module
 * with actual Rust/CPAL integration via JNA bindings.
 * 
 * The desktopApp module should create a subclass or wrapper that adds:
 * - Rust engine integration via `uniffi.rust_engine.*`
 * - Native library loading
 * - CPAL device enumeration
 * - mDNS discovery
 */
open class DesktopAudioEngine : AudioEngine {
    
    protected val _streamState = MutableStateFlow(StreamState.Idle)
    override val streamState: StateFlow<StreamState> = _streamState.asStateFlow()
    
    protected val _availableDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    override val availableDevices: StateFlow<List<AudioDevice>> = _availableDevices.asStateFlow()
    
    protected val _selectedDevice = MutableStateFlow<AudioDevice?>(null)
    override val selectedDevice: StateFlow<AudioDevice?> = _selectedDevice.asStateFlow()
    
    protected val _discoveredReceivers = MutableStateFlow<List<Receiver>>(emptyList())
    override val discoveredReceivers: StateFlow<List<Receiver>> = _discoveredReceivers.asStateFlow()
    
    protected val _logs = MutableStateFlow<List<String>>(emptyList())
    override val logs: StateFlow<List<String>> = _logs.asStateFlow()

    override val visuals: StateFlow<List<Float>> = MutableStateFlow(emptyList())
    
    init {
        addLog("Desktop AudioEngine initialized (stub - override in desktopApp)")
        
        // Mock data for testing UI
        _availableDevices.value = listOf(
            AudioDevice("default", "Default Audio Device", true)
        )
        _selectedDevice.value = _availableDevices.value.first()
        
        _discoveredReceivers.value = listOf(
            Receiver("test-1", "Test Receiver", "192.168.1.100", 5000, true)
        )
    }
    
    override suspend fun startStreaming(
        receiver: Receiver,
        device: AudioDevice?,
        transportMode: TransportMode
    ): Result<Unit> = withContext(Dispatchers.IO) {
        addLog("startStreaming called - override this method in desktopApp with Rust integration")
        _streamState.value = StreamState.Streaming
        Result.success(Unit)
    }
    
    override suspend fun stopStreaming(): Result<Unit> = withContext(Dispatchers.IO) {
        addLog("stopStreaming called")
        _streamState.value = StreamState.Idle
        Result.success(Unit)
    }
    
    override suspend fun refreshDevices(): Result<Unit> = withContext(Dispatchers.IO) {
        addLog("refreshDevices called - override this method to use CPAL")
        Result.success(Unit)
    }
    
    override suspend fun startDiscovery(): Result<Unit> = withContext(Dispatchers.IO) {
        addLog("startDiscovery called - override this method with mDNS/UDP discovery")
        Result.success(Unit)
    }
    
    override suspend fun stopDiscovery(): Result<Unit> {
        addLog("stopDiscovery called")
        return Result.success(Unit)
    }
    
    override fun dispose() {
        addLog("Desktop AudioEngine disposed")
    }
    
    protected fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        _logs.value = _logs.value + "[$timestamp] $message"
        
        // Keep only last 100 logs
        if (_logs.value.size > 100) {
            _logs.value = _logs.value.takeLast(100)
        }
    }
}
