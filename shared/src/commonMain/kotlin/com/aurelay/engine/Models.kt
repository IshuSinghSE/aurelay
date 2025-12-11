package com.aurelay.engine

/**
 * Shared data models for the audio engine.
 * These models are platform-agnostic and used across Android and Desktop.
 */

/**
 * Current state of the audio stream.
 */
enum class StreamState {
    /** No active stream */
    Idle,
    
    /** Stream is being initialized */
    Starting,
    
    /** Audio is actively streaming */
    Streaming,
    
    /** Stream is being terminated */
    Stopping,
    
    /** An error occurred during streaming */
    Error
}

/**
 * Represents an audio input device.
 * 
 * @param id Unique identifier for the device
 * @param name Human-readable device name
 * @param isDefault Whether this is the system default device
 */
data class AudioDevice(
    val id: String,
    val name: String,
    val isDefault: Boolean = false
)

/**
 * Represents a discovered receiver on the network.
 * 
 * @param id Unique identifier (typically IP address)
 * @param name Human-readable receiver name
 * @param address Network address (IP)
 * @param port Network port
 * @param isAvailable Whether the receiver is currently available
 * @param latency Estimated latency in milliseconds (optional)
 * @param bitrate Current bitrate in kbps (optional)
 */
data class Receiver(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val isAvailable: Boolean = true,
    val latency: Int? = null,
    val bitrate: Int? = null
)

/**
 * Transport modes for audio streaming.
 * 
 * Different modes offer different tradeoffs between security and latency.
 */
enum class TransportMode(val value: String) {
    /** Pure TCP streaming (current Android receiver uses this) */
    TcpOnly("tcp_only"),
    
    /** TCP handshake + UDP streaming (no authentication) */
    TcpUdp("tcp_udp"),
    
    /** TCP handshake + authenticated UDP (8-byte token) */
    TcpUdpAuth("tcp_udp_auth"),
    
    /** TLS handshake + authenticated UDP (future enhancement) */
    TlsUdpAuth("tls_udp_auth");
    
    companion object {
        fun fromString(value: String): TransportMode {
            return entries.find { it.value == value } ?: TcpOnly
        }
    }
}

/**
 * Connection state for tracking receiver connection status.
 */
data class ConnectionState(
    val isConnected: Boolean = false,
    val receiver: Receiver? = null,
    val error: String? = null
)
