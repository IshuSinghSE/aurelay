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

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "AudioRelay")
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        startForeground(1, buildNotification())
        Log.i("AudioRelay", "Service onCreate called, foreground started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServerRunning = true
        // Check for intent extra to override TLS (for dev/advanced users)
        useTls = intent?.getBooleanExtra("useTls", true) ?: true
        serverThread = Thread { startAudioServer() }
        serverThread?.start()
        Log.i("AudioRelay", "Service onStartCommand: useTls=$useTls, server thread started.")
        return START_STICKY
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

                val kmf = KeyManagerFactory.getInstance("X509")
                kmf.init(keyStore, keystorePassword.toCharArray())

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(kmf.keyManagers, null, null)
                val sslServerSocketFactory = sslContext.serverSocketFactory as SSLServerSocketFactory
                serverSocket = sslServerSocketFactory.createServerSocket(port) as SSLServerSocket
                (serverSocket as SSLServerSocket).needClientAuth = false
                Log.i("AudioRelay", "TLS enabled. Listening on port $port")
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
                        client.getInputStream().use { `in` ->
                            val buffer = ByteArray(minBufSize)
                            var read: Int
                            while (`in`.read(buffer).also { read = it } != -1 && isServerRunning) {
                                audioTrack?.write(buffer, 0, read)
                            }
                            audioTrack?.stop()
                            audioTrack?.flush()
                        }
                        Log.i("AudioRelay", "Client disconnected.")
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
