package com.aurelay

/**
 * Application-wide constants and configuration values.
 */
object Constants {
    /**
     * Network configuration
     */
    const val DEFAULT_PORT = 5000
    const val DISCOVERY_PORT = 5002
    const val UDP_BROADCAST_ADDRESS = "255.255.255.255"
    
    /**
     * Discovery protocol messages
     */
    const val AURELAY_DISCOVER = "AURELAY_DISCOVER"
    const val AURELAY_RESPONSE = "AURELAY_RESPONSE"
    const val AURELAY_CONNECT = "AURELAY_CONNECT"
    const val AURELAY_DISCONNECT = "AURELAY_DISCONNECT"
    
    /**
     * Timeouts (milliseconds)
     */
    const val DISCOVERY_TIMEOUT_MS = 5000L
    const val CONNECTION_TIMEOUT_MS = 10000L
    const val STREAM_TIMEOUT_MS = 30000L
    
    /**
     * Audio configuration
     */
    const val DEFAULT_SAMPLE_RATE = 44100
    const val DEFAULT_CHANNELS = 2
    const val BUFFER_SIZE = 4096
    
    /**
     * UI configuration
     */
    const val WIDE_SCREEN_BREAKPOINT_DP = 600
    const val ANIMATION_DURATION_MS = 300
    const val RIPPLE_ANIMATION_DURATION_MS = 2000
    
    /**
     * App metadata
     */
    const val APP_NAME = "Aurelay"
    const val APP_VERSION = "1.1.0"
}
