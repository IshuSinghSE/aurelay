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

class AudioRelayService : Service() {
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat
    private var serverThread: Thread? = null

    @Volatile
    private var isServerRunning = true
    private var serverSocket: ServerSocket? = null
    private var audioTrack: AudioTrack? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "AudioRelay")
        notificationManager = NotificationManagerCompat.from(this)

        // Before starting foreground, you need to create a notification channel (for Android 8.0+)
        // This should be done in your Application class or MainActivity, but is added here for completeness.
        createNotificationChannel()

        startForeground(1, buildNotification())
        isServerRunning = true
        serverThread = Thread { startAudioServer() }
        serverThread?.start()
        Log.i("AudioRelay", "Service onCreate called, starting foreground service.")
    }

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(this, "audioRelayChannel")
            .setContentTitle("AudioRelay")
            .setContentText("Playing audio from PC")
            .setSmallIcon(R.drawable.ic_media_play) // Use a valid drawable resource
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
            )
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
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

            serverSocket = ServerSocket(port)
            Log.i("AudioRelay", "Listening on port $port")

            while (isServerRunning) {
                try {
                    // This block should now be correctly recognized after adding imports
                    serverSocket?.accept()?.use { client ->
                        Log.i("AudioRelay", "Client connected: ${client.inetAddress.hostAddress}")
                        client.getInputStream().use { `in` ->
                            val buffer = ByteArray(minBufSize)
                            var read: Int
                            while (`in`.read(buffer).also { read = it } != -1 && isServerRunning) {
                                audioTrack?.write(buffer, 0, read)
                            }
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