package com.aurelay.engine

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic audio engine interface.
 * Implementations handle audio capture, streaming, and device discovery
 * for their respective platforms (Android, Desktop).
 * 
 * See:
 * - AndroidAudioEngine for Android implementation
 * - DesktopAudioEngine for Desktop/JVM implementation
 */
interface AudioEngine {
    /**
     * Current streaming state (idle, streaming, error).
     */
    val streamState: StateFlow<StreamState>
    
    /**
     * List of available audio input devices.
     */
    val availableDevices: StateFlow<List<AudioDevice>>
    
    /**
     * Current selected device (null if using default).
     */
    val selectedDevice: StateFlow<AudioDevice?>
    
    /**
     * Discovered receivers on the network.
     */
    val discoveredReceivers: StateFlow<List<Receiver>>
    
    /**
     * Audio engine logs/status messages.
     */
    val logs: StateFlow<List<String>>

    /**
     * Audio visualization data (amplitudes).
     * Mock data or real FFT data depending on implementation.
     */
    val visuals: StateFlow<List<Float>>
    
    /**
     * Start streaming audio to the specified receiver.
     * @param receiver Target receiver to stream to
     * @param device Optional specific audio device (null for default)
     * @param transportMode Transport mode (tcp_only, tcp_udp, tcp_udp_auth, tls_udp_auth)
     */
    suspend fun startStreaming(
        receiver: Receiver,
        device: AudioDevice? = null,
        transportMode: TransportMode = TransportMode.TcpOnly
    ): Result<Unit>
    
    /**
     * Stop the current audio stream.
     */
    suspend fun stopStreaming(): Result<Unit>
    
    /**
     * Refresh the list of available audio input devices.
     */
    suspend fun refreshDevices(): Result<Unit>
    
    /**
     * Start discovering receivers on the local network.
     */
    suspend fun startDiscovery(): Result<Unit>
    
    /**
     * Stop discovering receivers.
     */
    suspend fun stopDiscovery(): Result<Unit>
    
    /**
     * Clean up resources.
     */
    fun dispose()
}
