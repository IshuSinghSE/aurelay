package io.github.aurynk.audiorelay

//noinspection SuspiciousImport
import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory

class AudioRelayService : Service() {
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat
    private var serverThread: Thread? = null

    @Volatile private var isServerRunning = true
    private var serverSocket: ServerSocket? = null
    private var useTls: Boolean = true // Default to TLS for production
    private var audioTrack: AudioTrack? = null
    
    companion object {
        const val ACTION_SET_VOLUME = "io.github.aurynk.SET_VOLUME"
        const val EXTRA_VOLUME = "volume"
        const val ACTION_AUDIO_LEVEL = "io.github.aurynk.AUDIO_LEVEL"
        const val EXTRA_AUDIO_LEVELS = "audio_levels"
    }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "AudioRelay")
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        startForeground(1, buildNotification())
        Log.i("AudioRelay", "Service onCreate called, foreground started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_VOLUME -> {
                val volume = intent.getFloatExtra(EXTRA_VOLUME, 0.8f)
                setVolume(volume)
                Log.d("AudioRelay", "Volume set to: $volume")
                return START_STICKY
            }
            else -> {
                isServerRunning = true
                // Check for intent extra to override TLS (for dev/advanced users)
                useTls = intent?.getBooleanExtra("useTls", true) ?: true
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
        val builder =
            NotificationCompat.Builder(this, "audioRelayChannel")
                .setContentTitle("AudioRelay")
                .setContentText("Playing audio from PC")
                .setSmallIcon(
                    R.drawable.ic_media_play
                ) // Use a valid drawable resource
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.sessionToken)
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
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            // --- TLS/Plain socket selection ---
            if (useTls) {
                // --- TLS Setup ---
                // You must convert your PEM cert/key to a PKCS12 keystore:
                // openssl pkcs12 -export -in cert.pem -inkey key.pem -out server.p12 -name audiorelay -CAfile ca.pem -caname root
                // Place server.p12 in app's files or raw resources.
                val keystorePassword = "changeit" // Use your actual password
                val keystoreStream = applicationContext.assets.open("server.p12") // Or use resources/raw
                val keyStore = KeyStore.getInstance("PKCS12")
                keyStore.load(keystoreStream, keystorePassword.toCharArray())

                // Use the platform default KeyManagerFactory algorithm (more portable)
                val kmfAlg = KeyManagerFactory.getDefaultAlgorithm()
                val kmf = KeyManagerFactory.getInstance(kmfAlg)
                kmf.init(keyStore, keystorePassword.toCharArray())

                Log.i("AudioRelay", "Loaded keystore and initialized KeyManagerFactory (alg=$kmfAlg)")

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(kmf.keyManagers, null, null)
                val sslServerSocketFactory = sslContext.serverSocketFactory as SSLServerSocketFactory

                // Create server socket; this binds to the default wildcard address (0.0.0.0)
                serverSocket = sslServerSocketFactory.createServerSocket(port) as SSLServerSocket
                (serverSocket as SSLServerSocket).needClientAuth = false
                Log.i("AudioRelay", "TLS enabled. Listening on port ${serverSocket?.localPort} bound to ${serverSocket?.inetAddress}")
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
                .setBufferSizeInBytes(minBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            Log.i("AudioRelay", "AudioTrack playing.")

            while (isServerRunning) {
                try {
                    serverSocket?.accept()?.use { client ->
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
                            while (`in`.read(buffer).also { read = it } != -1 && isServerRunning) {
                                audioTrack?.write(buffer, 0, read)
                                
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
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
