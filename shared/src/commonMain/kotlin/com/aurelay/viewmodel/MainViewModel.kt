package com.aurelay.viewmodel

import com.aurelay.engine.AudioEngine
import com.aurelay.engine.Receiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Main view model for managing app state.
 * Coordinates between the UI and the AudioEngine.
 * 
 * This is kept minimal - most logic lives in the platform-specific AudioEngine implementations.
 */
class MainViewModel(
    private val audioEngine: AudioEngine,
    private val scope: CoroutineScope
) {
    /**
     * Collect streaming state from audio engine
     */
    val streamState = audioEngine.streamState
    
    /**
     * Collect available devices
     */
    val availableDevices = audioEngine.availableDevices
    
    /**
     * Collect selected device
     */
    val selectedDevice = audioEngine.selectedDevice
    
    /**
     * Collect discovered receivers
     */
    val discoveredReceivers = audioEngine.discoveredReceivers
    
    /**
     * Collect logs
     */
    val logs = audioEngine.logs
    
    /**
     * Start streaming to a receiver
     */
    fun startStreaming(receiver: Receiver) {
        scope.launch {
            audioEngine.startStreaming(receiver)
        }
    }
    
    /**
     * Stop current stream
     */
    fun stopStreaming() {
        scope.launch {
            audioEngine.stopStreaming()
        }
    }
    
    /**
     * Start discovering receivers
     */
    fun startDiscovery() {
        scope.launch {
            audioEngine.startDiscovery()
        }
    }
    
    /**
     * Stop discovering receivers
     */
    fun stopDiscovery() {
        scope.launch {
            audioEngine.stopDiscovery()
        }
    }
    
    /**
     * Refresh available devices
     */
    fun refreshDevices() {
        scope.launch {
            audioEngine.refreshDevices()
        }
    }
    
    /**
     * Clean up resources
     */
    fun dispose() {
        audioEngine.dispose()
    }
}
