package io.github.aurynk.audiorelay

//noinspection SuspiciousImport
import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.io.IOException
import java.net.ServerSocket
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory

class AudioRelayService : Service() {
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat
    private var serverThread: Thread? = null
    @Volatile private var lastClientIp: String = ""

    @Volatile private var isServerRunning = true
    private var serverSocket: ServerSocket? = null
    private var useTls: Boolean = false // Default to plain TCP for easier testing (change to true for production TLS)
    private var audioTrack: AudioTrack? = null
    
    companion object {
        const val ACTION_SET_VOLUME = "io.github.aurynk.SET_VOLUME"
        const val EXTRA_VOLUME = "volume"
        const val ACTION_AUDIO_LEVEL = "io.github.aurynk.AUDIO_LEVEL"
        const val EXTRA_AUDIO_LEVELS = "audio_levels"
        const val ACTION_STOP_SERVICE = "io.github.aurynk.STOP_SERVICE"
        const val ACTION_OPEN_APP = "io.github.aurynk.OPEN_APP"

        // Discovery constants — desktop client will broadcast DISCOVERY_REQUEST
        // and the service will reply with DISCOVERY_RESPONSE;<port>;<name>
        const val DISCOVERY_PORT = 5002
        const val DISCOVERY_REQUEST = "AURYNK_DISCOVER"
        const val DISCOVERY_RESPONSE = "AURYNK_RESPONSE"
        const val CONNECT_REQUEST = "AURYNK_CONNECT"
        const val DISCONNECT_REQUEST = "AURYNK_DISCONNECT"
    }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "AudioRelay")
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        startForeground(1, buildNotification())
        // Start discovery responder so desktop clients can find this device
        startDiscoveryResponder()
        Log.i("AudioRelay", "Service onCreate called, foreground started.")
    }

    private var discoveryThread: Thread? = null

    private fun startDiscoveryResponder() {
        if (discoveryThread != null && discoveryThread!!.isAlive) return
        discoveryThread = Thread {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(DISCOVERY_PORT)
                socket.broadcast = true
                val buf = ByteArray(1024)
                while (!Thread.currentThread().isInterrupted) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length).trim()
                        if (msg == DISCOVERY_REQUEST) {
                            // Respond with service info — desktop will use packet source address
                            val deviceName = Build.MODEL.ifEmpty { "Android Device" }.replace(";", "_")
                            val response = "$DISCOVERY_RESPONSE;5000;$deviceName"
                            val respData = response.toByteArray()
                            val respPacket = DatagramPacket(respData, respData.size, packet.address, packet.port)
                            socket.send(respPacket)
                            Log.d("AudioRelay", "Discovery request responded to ${packet.address} with name: $deviceName")
                        } else if (msg.startsWith(CONNECT_REQUEST)) {
                            // A sender wants to connect — send confirmation request to UI
                            try {
                                val parts = msg.split(";")
                                val senderName = parts.getOrNull(1) ?: "Android Device"
                                val bcast = Intent(MainActivity.ACTION_CONNECTION_REQUEST)
                                bcast.setPackage(packageName)
                                bcast.putExtra("client_ip", packet.address.hostAddress ?: "")
                                bcast.putExtra("client_name", senderName)
                                sendBroadcast(bcast)
                                Log.i("AudioRelay", "Connect request from $senderName (${packet.address.hostAddress}), sent to UI for confirmation")
                            } catch (ex: Exception) {
                                Log.e("AudioRelay", "Failed to broadcast connect request: ${ex.message}", ex)
                            }
                        } else if (msg.startsWith("AURYNK_ACCEPT")) {
                            // Connection accepted - could be received by sender OR receiver
                            // If receiver gets this, it means the sender acknowledged the acceptance
                            try {
                                val bcast = Intent("io.github.aurynk.CLIENT_CONNECTION")
                                bcast.setPackage(packageName)
                                bcast.putExtra("connected", true)
                                bcast.putExtra("client_ip", packet.address.hostAddress ?: "")
                                sendBroadcast(bcast)
                                Log.i("AudioRelay", "Connection accepted notification from ${packet.address.hostAddress}")
                            } catch (ex: Exception) {
                                Log.e("AudioRelay", "Failed to broadcast connection accepted: ${ex.message}", ex)
                            }
                        } else if (msg.startsWith("AURYNK_REJECT")) {
                            // Connection rejected by receiver
                            try {
                                val bcast = Intent("io.github.aurynk.CLIENT_CONNECTION")
                                bcast.setPackage(packageName)
                                bcast.putExtra("connected", false)
                                bcast.putExtra("client_ip", "")
                                sendBroadcast(bcast)
                                Log.i("AudioRelay", "Connection rejected by ${packet.address.hostAddress}")
                            } catch (ex: Exception) {
                                Log.e("AudioRelay", "Failed to broadcast connection rejected: ${ex.message}", ex)
                            }
                        } else if (msg.startsWith(DISCONNECT_REQUEST)) {
                            try {
                                val bcast = Intent("io.github.aurynk.CLIENT_CONNECTION")
                                bcast.setPackage(packageName)
                                bcast.putExtra("connected", false)
                                bcast.putExtra("client_ip", "")
                                sendBroadcast(bcast)
                                Log.i("AudioRelay", "Disconnect request received from ${packet.address.hostAddress}, broadcasted CLIENT_CONNECTION false")
                            } catch (ex: Exception) {
                                Log.e("AudioRelay", "Failed to broadcast disconnect request: ${ex.message}", ex)
                            }
                        }
                    } catch (e: Exception) {
                        // ignore and continue
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioRelay", "Discovery responder failed: ${e.message}")
            } finally {
                try { socket?.close() } catch (e: Exception) {}
            }
        }
        discoveryThread?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.d("AudioRelay", "Stopping service from notification action")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_VOLUME -> {
                val volume = intent.getFloatExtra(EXTRA_VOLUME, 0.8f)
                setVolume(volume)
                Log.d("AudioRelay", "Volume set to: $volume")
                return START_STICKY
            }
            else -> {
                isServerRunning = true
                // Check for intent extra to override TLS (for dev/advanced users)
                // Default to false (plain TCP) unless explicitly set to true
                useTls = intent?.getBooleanExtra("useTls", false) ?: false
                if (serverThread == null || !serverThread!!.isAlive) {
                    serverThread = Thread { startAudioServer() }
                    serverThread?.start()
                    Log.i("AudioRelay", "Service onStartCommand: useTls=$useTls, server thread started.")
                }
                return START_STICKY
            }
        }
    }

    private fun buildNotification(): Notification {
        // Create intent to open the app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Create intent to disconnect (stop service)
        val disconnectIntent = Intent(this, AudioRelayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val builder =
            NotificationCompat.Builder(this, "audioRelayChannel")
                .setContentTitle("Aurynk")
                .setContentText("Playing audio from PC")
                .setSmallIcon(R.drawable.ic_media_play)
                .setContentIntent(openAppPendingIntent)
                .addAction(R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPendingIntent)
                .addAction(R.drawable.ic_menu_preferences, "Open App", openAppPendingIntent)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.sessionToken)
                        .setShowActionsInCompactView(0, 1)
                )
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    "audioRelayChannel",
                    "Audio Relay Channel",
                    NotificationManager.IMPORTANCE_LOW
                )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startAudioServer() {
        val port = 5000
        val sampleRate = 44100
        
        // Try stereo first, fallback to mono if device doesn't support it well
        var channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        var audioFormat = AudioFormat.ENCODING_PCM_16BIT
        var minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        
        // Check if stereo is supported (ERROR means not supported)
        if (minBufSize == AudioTrack.ERROR_BAD_VALUE || minBufSize == AudioTrack.ERROR) {
            Log.w("AudioRelay", "Stereo not supported, falling back to mono")
            channelConfig = AudioFormat.CHANNEL_OUT_MONO
            minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        }
        
        val isStereo = channelConfig == AudioFormat.CHANNEL_OUT_STEREO
        Log.i("AudioRelay", "Audio config: ${if (isStereo) "STEREO" else "MONO"}, sampleRate=$sampleRate, minBufSize=$minBufSize")
        
        // Use a larger buffer than the minimum to avoid underruns with network jitter
        val bufferSizeBytes = maxOf(minBufSize, minBufSize * 4)

            try {
                // --- TLS/Plain socket selection with fallback ---
                if (useTls) {
                    try {
                        // --- TLS Setup ---
                        val keystorePassword = "changeit" // Use your actual password
                        val keystoreStream = applicationContext.assets.open("server.p12") // Or use resources/raw
                        val keyStore = KeyStore.getInstance("PKCS12")
                        keyStore.load(keystoreStream, keystorePassword.toCharArray())

                        val kmfAlg = KeyManagerFactory.getDefaultAlgorithm()
                        val kmf = KeyManagerFactory.getInstance(kmfAlg)
                        kmf.init(keyStore, keystorePassword.toCharArray())

                        Log.i("AudioRelay", "Loaded keystore and initialized KeyManagerFactory (alg=$kmfAlg)")

                        val sslContext = SSLContext.getInstance("TLS")
                        sslContext.init(kmf.keyManagers, null, null)
                        val sslServerSocketFactory = sslContext.serverSocketFactory as SSLServerSocketFactory

                        serverSocket = sslServerSocketFactory.createServerSocket(port) as SSLServerSocket
                        (serverSocket as SSLServerSocket).needClientAuth = false
                        Log.i("AudioRelay", "TLS enabled. Listening on port ${serverSocket?.localPort} bound to ${serverSocket?.inetAddress}")
                        Log.i("AudioRelay", "ServerSocket implementation: ${serverSocket!!::class.java.name}")
                    } catch (tlsEx: Exception) {
                        Log.w("AudioRelay", "TLS setup failed (${tlsEx.message}), falling back to plain TCP: ${tlsEx}")
                        try {
                            serverSocket = ServerSocket(port)
                            Log.i("AudioRelay", "Plain TCP fallback. Listening on port $port")
                            Log.i("AudioRelay", "ServerSocket implementation: ${serverSocket!!::class.java.name}")
                        } catch (plainEx: Exception) {
                            throw plainEx
                        }
                    }
                } else {
                    // --- Plain TCP ---
                    serverSocket = ServerSocket(port)
                    Log.i("AudioRelay", "Plain TCP. Listening on port $port")
                }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioTrackFormat = AudioFormat.Builder()
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
                .setSampleRate(sampleRate)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioTrackFormat)
                .setBufferSizeInBytes(bufferSizeBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()

            // Log AudioTrack state before playing
            Log.i("AudioRelay", "AudioTrack created. State=${audioTrack?.state}, PlayState=${audioTrack?.playState}")
            
            audioTrack?.play()
            
            // Verify AudioTrack is actually playing
            val playState = audioTrack?.playState
            val state = audioTrack?.state
            Log.i("AudioRelay", "AudioTrack play() called. State=$state, PlayState=$playState (expected: STATE_INITIALIZED=1, PLAYSTATE_PLAYING=3)")
            
            if (state != AudioTrack.STATE_INITIALIZED || playState != AudioTrack.PLAYSTATE_PLAYING) {
                Log.e("AudioRelay", "AudioTrack failed to start properly! State=$state, PlayState=$playState")
            }

            while (isServerRunning) {
                try {
                    // Accept may throw SSLException if a non-TLS client connects to an SSLServerSocket
                    val maybeClient = try {
                        serverSocket?.accept()
                    } catch (sslEx: javax.net.ssl.SSLException) {
                        Log.e("AudioRelay", "SSL exception during accept (possible TLS/plain mismatch): ${sslEx.message}", sslEx)
                        null
                    }

                    maybeClient?.use { client ->
                        Log.i("AudioRelay", "Client connected: ${client.inetAddress.hostAddress}")
                        // Broadcast connection event so UI can update
                        try {
                            val bcast = Intent("io.github.aurynk.CLIENT_CONNECTION")
                            bcast.setPackage(packageName) // Make it explicit to this app
                            bcast.putExtra("connected", true)
                            bcast.putExtra("client_ip", client.inetAddress.hostAddress)
                            sendBroadcast(bcast)
                            Log.i("AudioRelay", "Broadcast sent: CLIENT_CONNECTION connected=true ip=${client.inetAddress.hostAddress}")
                        } catch (ex: Exception) {
                            Log.e("AudioRelay", "Failed to broadcast client connected: ${ex.message}", ex)
                        }
                        client.getInputStream().use { `in` ->
                            val buffer = ByteArray(minBufSize)
                            var read: Int
                            var frameCount = 0
                            val frameSize = 4 // Input is always stereo: 2 bytes per sample (16-bit) * 2 channels
                            val outputFrameSize = if (isStereo) 4 else 2 // Output frame size depends on AudioTrack config
                            
                            // Log buffer configuration for diagnostics
                            Log.d("AudioRelay", "minBufSize=$minBufSize, bufferSizeBytes=$bufferSizeBytes, frameSize=$frameSize, outputMode=${if (isStereo) "STEREO" else "MONO"}")
                            
                            // remember connected client
                            try {
                                lastClientIp = client.inetAddress.hostAddress
                            } catch (ex: Exception) { lastClientIp = "" }
                            
                            while (`in`.read(buffer).also { read = it } != -1 && isServerRunning) {
                                if (read > 0) {
                                    // Ensure we write frame-aligned data (critical for Android 12 and older HALs)
                                    // Input stream is always stereo s16le from ffmpeg
                                    val alignedBytes = (read / frameSize) * frameSize
                                    
                                    if (alignedBytes > 0) {
                                        try {
                                            // Use WRITE_BLOCKING only on Android 12 and below for compatibility
                                            // Android 13+ handles non-blocking writes better
                                            val writeMode = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                                AudioTrack.WRITE_BLOCKING
                                            } else {
                                                AudioTrack.WRITE_NON_BLOCKING
                                            }
                                            
                                            val written = if (isStereo) {
                                                // Direct write for stereo
                                                audioTrack?.write(buffer, 0, alignedBytes, writeMode)
                                            } else {
                                                // Convert stereo to mono by averaging L+R channels
                                                val monoBuffer = convertStereoToMono(buffer, alignedBytes)
                                                audioTrack?.write(monoBuffer, 0, monoBuffer.size, writeMode)
                                            }
                                            
                                            if (written != null && written < 0) {
                                                // Negative return codes indicate errors
                                                Log.e("AudioRelay", "AudioTrack.write returned error code: $written (${getAudioTrackErrorName(written)})")
                                                // Check if AudioTrack died
                                                val currentState = audioTrack?.playState
                                                Log.e("AudioRelay", "AudioTrack playState after error: $currentState")
                                            } else if (written != null) {
                                                val expectedBytes = if (isStereo) alignedBytes else alignedBytes / 2
                                                if (written != expectedBytes) {
                                                    Log.w("AudioRelay", "AudioTrack wrote $written bytes out of $expectedBytes expected")
                                                }
                                                // Log successful writes periodically
                                                if (frameCount % 100 == 0) {
                                                    Log.d("AudioRelay", "Frame $frameCount: Successfully wrote $written bytes (mode=${if (writeMode == AudioTrack.WRITE_BLOCKING) "BLOCKING" else "NON_BLOCKING"})")
                                                }
                                            }
                                        } catch (wex: Exception) {
                                            Log.e("AudioRelay", "AudioTrack write exception: ${wex.message}", wex)
                                        }
                                    }
                                    
                                    // If we had unaligned bytes, log it (shouldn't happen with TCP stream but good to track)
                                    if (read != alignedBytes) {
                                        Log.w("AudioRelay", "Dropped ${read - alignedBytes} unaligned bytes (read=$read, aligned=$alignedBytes)")
                                    }
                                }

                                // Broadcast audio levels periodically (every 5 frames ~= 11ms for smoother updates)
                                if (++frameCount % 5 == 0) {
                                    val levels = calculateAudioLevels(buffer, read)
                                    broadcastAudioLevels(levels)
                                }
                            }
                            audioTrack?.stop()
                            audioTrack?.flush()
                        }
                        Log.i("AudioRelay", "Client disconnected.")
                        // Broadcast disconnect event so UI can update
                        try {
                            val bcast = Intent("io.github.aurynk.CLIENT_CONNECTION")
                            bcast.setPackage(packageName) // Make it explicit to this app
                            bcast.putExtra("connected", false)
                            bcast.putExtra("client_ip", "")
                            sendBroadcast(bcast)
                            Log.i("AudioRelay", "Broadcast sent: CLIENT_CONNECTION connected=false")
                            lastClientIp = ""
                        } catch (ex: Exception) {
                            Log.e("AudioRelay", "Failed to broadcast client disconnected: ${ex.message}", ex)
                        }
                    }
                } catch (e: IOException) {
                    if (isServerRunning) {
                        Log.e("AudioRelay", "Error accepting client or reading data: ", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRelay", "Server setup or loop error: ", e)
        } finally {
            Log.i("AudioRelay", "Stopping server and releasing AudioTrack.")
            // Proper cleanup is handled in onDestroy
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            try {
                serverSocket?.close()
            } catch (e: IOException) {
                Log.e("AudioRelay", "Error closing server socket during cleanup.", e)
            }
        }
    }

    private fun setVolume(volume: Float) {
        try {
            // Clamp volume between 0.0 and 1.0
            val clampedVolume = volume.coerceIn(0f, 1f)
            audioTrack?.setVolume(clampedVolume)
            Log.d("AudioRelay", "AudioTrack volume updated to: $clampedVolume")
        } catch (e: Exception) {
            Log.e("AudioRelay", "Error setting volume: ${e.message}", e)
        }
    }
    
    // Helper function to map AudioTrack error codes to readable names
    private fun getAudioTrackErrorName(errorCode: Int): String {
        return when (errorCode) {
            AudioTrack.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
            AudioTrack.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
            AudioTrack.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
            AudioTrack.ERROR -> "ERROR"
            else -> "UNKNOWN_ERROR($errorCode)"
        }
    }
    
    // Convert stereo s16le PCM to mono by averaging left and right channels
    private fun convertStereoToMono(stereoBuffer: ByteArray, length: Int): ByteArray {
        val monoSize = length / 2 // Each stereo frame (4 bytes) becomes mono frame (2 bytes)
        val monoBuffer = ByteArray(monoSize)
        
        var monoIndex = 0
        var i = 0
        while (i < length) {
            // Read left channel (2 bytes)
            val left = (stereoBuffer[i].toInt() and 0xFF) or ((stereoBuffer[i + 1].toInt() and 0xFF) shl 8)
            // Read right channel (2 bytes)
            val right = (stereoBuffer[i + 2].toInt() and 0xFF) or ((stereoBuffer[i + 3].toInt() and 0xFF) shl 8)
            
            // Average the channels (treat as signed 16-bit)
            val leftSigned = left.toShort().toInt()
            val rightSigned = right.toShort().toInt()
            val mono = ((leftSigned + rightSigned) / 2).toShort()
            
            // Write mono sample (2 bytes)
            monoBuffer[monoIndex] = (mono.toInt() and 0xFF).toByte()
            monoBuffer[monoIndex + 1] = ((mono.toInt() shr 8) and 0xFF).toByte()
            
            i += 4 // Move to next stereo frame
            monoIndex += 2 // Move to next mono frame
        }
        
        return monoBuffer
    }
    
    private fun calculateAudioLevels(buffer: ByteArray, size: Int): FloatArray {
        // Calculate 24 frequency bands by sampling the PCM data
        val bands = 24
        val levels = FloatArray(bands)
        val samplesPerBand = size / (bands * 2) // 2 bytes per sample (16-bit PCM)
        
        for (i in 0 until bands) {
            var sum = 0f
            val start = i * samplesPerBand * 2
            val end = minOf(start + samplesPerBand * 2, size)
            
            for (j in start until end step 2) {
                if (j + 1 < size) {
                    // Convert two bytes to 16-bit sample
                    val sample = (buffer[j].toInt() and 0xFF) or ((buffer[j + 1].toInt() and 0xFF) shl 8)
                    val normalized = sample.toShort().toFloat() / 32768f
                    sum += kotlin.math.abs(normalized)
                }
            }
            
            levels[i] = (sum / samplesPerBand).coerceIn(0f, 1f)
        }
        
        return levels
    }
    
    private fun broadcastAudioLevels(levels: FloatArray) {
        try {
            val intent = Intent(ACTION_AUDIO_LEVEL)
            intent.setPackage(packageName)
            intent.putExtra(EXTRA_AUDIO_LEVELS, levels)
            sendBroadcast(intent)
        } catch (e: Exception) {
            // Silently ignore broadcast errors to avoid spam
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("AudioRelay", "onDestroy called, shutting down service.")
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e("AudioRelay", "Error closing server socket on destroy.", e)
        }
        serverThread?.interrupt() // Interrupt the thread
        try {
            discoveryThread?.interrupt()
            discoveryThread = null
        } catch (e: Exception) {
            // ignore
        }
        // Ensure UI knows we're disconnected when service stops
        try {
            val bcast = Intent("io.github.aurynk.CLIENT_CONNECTION")
            bcast.setPackage(packageName)
            bcast.putExtra("connected", false)
            bcast.putExtra("client_ip", "")
            sendBroadcast(bcast)
            Log.i("AudioRelay", "Broadcast sent from onDestroy: CLIENT_CONNECTION connected=false")
            lastClientIp = ""
        } catch (ex: Exception) {
            Log.e("AudioRelay", "Failed to broadcast disconnection on destroy: ${ex.message}", ex)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
