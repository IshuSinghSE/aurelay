package io.github.aurynk.audiorelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.util.concurrent.CopyOnWriteArrayList

class AudioCaptureService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var clientSocket: Socket? = null
    private var targetIp: String = ""
    private var targetPort: Int = 5000
    private var isStreaming = false
    private var discoveryThread: Thread? = null
    private var discoverySocket: DatagramSocket? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_TARGET_IP = "EXTRA_TARGET_IP"
        const val EXTRA_TARGET_PORT = "EXTRA_TARGET_PORT"
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "AudioCaptureChannel"
        const val TAG = "AudioCaptureService"
        const val DISCOVERY_PORT = 5002
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startDiscoveryResponder()
    }
    
    private fun startDiscoveryResponder() {
        discoveryThread = Thread {
            try {
                discoverySocket = DatagramSocket(DISCOVERY_PORT)
                val buffer = ByteArray(1024)
                Log.d(TAG, "UDP listener started on port $DISCOVERY_PORT")
                
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        discoverySocket?.receive(packet)
                        val msg = String(packet.data, 0, packet.length).trim()
                        
                        Log.d(TAG, "Received UDP message: $msg from ${packet.address.hostAddress}")
                        
                        if (msg.startsWith("AURYNK_ACCEPT")) {
                            // Connection accepted by receiver
                            try {
                                val bcast = Intent("io.github.aurynk.CLIENT_CONNECTION")
                                bcast.setPackage(packageName)
                                bcast.putExtra("connected", true)
                                bcast.putExtra("client_ip", packet.address.hostAddress ?: "")
                                sendBroadcast(bcast)
                                Log.i(TAG, "Connection ACCEPTED by ${packet.address.hostAddress}")
                            } catch (ex: Exception) {
                                Log.e(TAG, "Failed to broadcast connection accepted: ${ex.message}", ex)
                            }
                        } else if (msg.startsWith("AURYNK_REJECT")) {
                            // Connection rejected by receiver
                            try {
                                val bcast = Intent("io.github.aurynk.CLIENT_CONNECTION")
                                bcast.setPackage(packageName)
                                bcast.putExtra("connected", false)
                                bcast.putExtra("client_ip", "")
                                sendBroadcast(bcast)
                                Log.i(TAG, "Connection REJECTED by ${packet.address.hostAddress}")
                                // Stop the service since connection was rejected
                                stopSelf()
                            } catch (ex: Exception) {
                                Log.e(TAG, "Failed to broadcast connection rejected: ${ex.message}", ex)
                            }
                        } else if (msg.startsWith("AURYNK_DISCONNECT")) {
                            // Receiver disconnected
                            try {
                                val bcast = Intent("io.github.aurynk.CLIENT_CONNECTION")
                                bcast.setPackage(packageName)
                                bcast.putExtra("connected", false)
                                bcast.putExtra("client_ip", "")
                                sendBroadcast(bcast)
                                Log.i(TAG, "Disconnect request from ${packet.address.hostAddress}")
                                stopSelf()
                            } catch (ex: Exception) {
                                Log.e(TAG, "Failed to broadcast disconnect: ${ex.message}", ex)
                            }
                        }
                    } catch (e: Exception) {
                        if (!Thread.currentThread().isInterrupted) {
                            Log.e(TAG, "Error receiving UDP packet: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP listener failed: ${e.message}")
            } finally {
                try { discoverySocket?.close() } catch (e: Exception) {}
            }
        }
        discoveryThread?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                
                targetIp = intent.getStringExtra(EXTRA_TARGET_IP) ?: ""
                targetPort = intent.getIntExtra(EXTRA_TARGET_PORT, 5000)

                if (resultData != null && targetIp.isNotEmpty()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            createNotification(),
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                            else 0
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, createNotification())
                    }

                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val projection = projectionManager.getMediaProjection(android.app.Activity.RESULT_OK, resultData)
                    if (projection != null) {
                         startCapture(projection)
                    } else {
                         Log.e(TAG, "MediaProjection is null")
                         stopSelf()
                    }
                } else {
                    Log.e(TAG, "Missing result data or target IP")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startCapture(projection: MediaProjection) {
        mediaProjection = projection

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)  // Match receiver's sample rate
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            try {
                audioRecord = AudioRecord.Builder()
                    .setAudioFormat(audioFormat)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()

                audioRecord?.startRecording()
                isStreaming = true

                // Connect and stream in a single coroutine to ensure proper sequencing
                connectAndStream()

            } catch (e: SecurityException) {
                Log.e(TAG, "Security Exception starting AudioRecord: ${e.message}")
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting AudioRecord: ${e.message}")
                stopSelf()
            }
        }

        // Check for permission again if needed, though service should have it.
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
             Log.e(TAG, "Missing RECORD_AUDIO permission")
             stopSelf()
             return
        }
    }

    private fun connectAndStream() {
        serviceScope.launch {
            try {
                // First, establish connection
                Log.d(TAG, "Connecting to receiver at $targetIp:$targetPort")
                clientSocket = Socket(targetIp, targetPort)
                clientSocket?.tcpNoDelay = true // Disable Nagle's algorithm for low latency
                Log.d(TAG, "Connected to receiver successfully")
                
                // Then start streaming
                val bufferSize = 1024 * 4
                val buffer = ByteArray(bufferSize)
                val outputStream = clientSocket?.getOutputStream()
                
                if (outputStream == null) {
                    Log.e(TAG, "Output stream is null")
                    stopSelf()
                    return@launch
                }
                
                Log.d(TAG, "Starting audio streaming...")
                var bytesWritten = 0L
                var lastLogTime = System.currentTimeMillis()
                
                while (isActive && isStreaming && clientSocket?.isConnected == true) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        try {
                            outputStream.write(buffer, 0, read)
                            outputStream.flush()
                            bytesWritten += read
                            
                            // Log progress every 5 seconds
                            val now = System.currentTimeMillis()
                            if (now - lastLogTime > 5000) {
                                Log.d(TAG, "Streamed ${bytesWritten / 1024} KB so far")
                                lastLogTime = now
                            }
                        } catch (e: IOException) {
                            Log.e(TAG, "Error writing to receiver: ${e.message}")
                            break
                        }
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord read error: $read")
                        break
                    }
                }
                
                Log.d(TAG, "Audio streaming stopped. Total bytes: $bytesWritten")
                
            } catch (e: IOException) {
                Log.e(TAG, "Connection/streaming failed: ${e.message}", e)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error: ${e.message}", e)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        isStreaming = false
        serviceJob.cancel()
        
        // Stop UDP listener
        try {
            discoveryThread?.interrupt()
            discoverySocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping UDP listener: ${e.message}")
        }
        
        try {
            clientSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        mediaProjection?.stop()
        mediaProjection = null

        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aurynk Audio Capture")
            .setContentText("Capturing and streaming system audio...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
