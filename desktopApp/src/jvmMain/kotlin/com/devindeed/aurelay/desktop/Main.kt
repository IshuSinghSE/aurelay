package com.devindeed.aurelay.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import kotlin.concurrent.thread
import uniffi.rust_engine.startStream
import uniffi.rust_engine.stopStream
import uniffi.rust_engine.discoverReceivers
import uniffi.rust_engine.requestConnectAndWait
import uniffi.rust_engine.startStreamFFmpeg
import uniffi.rust_engine.stopStreamFFmpeg
import androidx.compose.material3.AlertDialog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Composable
@Preview
fun App() {
    var targetIp by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Idle") }
    var discovering by remember { mutableStateOf(false) }
    var showDiscoveryDialog by remember { mutableStateOf(false) }
    var discoveredDevices by remember { mutableStateOf(listOf<String>()) }
    var deviceName by remember { mutableStateOf("") }
    var availableDevices by remember { mutableStateOf(listOf<String>()) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var backend by remember { mutableStateOf("Native") }
    var ffmpegProcess by remember { mutableStateOf<Process?>(null) }
    var nativeLogs by remember { mutableStateOf("") }
    var showNativeLog by remember { mutableStateOf(false) }

    // Start FFmpeg fallback via Rust (spawns `ffmpeg` from native code and pipes to TCP)
    fun startFFmpegStream(host: String, port: String, device: String?) {
        try {
            val p = port.toIntOrNull() ?: 5000
            startStreamFFmpeg(host, p, if (device.isNullOrBlank()) null else device)
            statusMessage = "Requested ffmpeg stream to $host:$p"
        } catch (e: Exception) {
            statusMessage = "FFmpeg start failed: ${e.message}"
        }
    }

    fun stopFFmpegStream() {
        try {
            stopStreamFFmpeg()
            statusMessage = "Requested ffmpeg stop"
        } catch (e: Exception) {
            statusMessage = "FFmpeg stop failed: ${e.message}"
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF009688), // Teal
            secondary = androidx.compose.ui.graphics.Color(0xFFE91E63) // Pink
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Aurelay Desktop Sender",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = targetIp,
                    onValueChange = { targetIp = it },
                    label = { Text("Target IP Address (ip:port)") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    enabled = !isStreaming
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Backend selection: Native (Rust) or FFmpeg (Python fallback)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                    Text("Backend:", modifier = Modifier.padding(end = 8.dp))
                    Button(onClick = { backend = "Native" }, enabled = backend != "Native" && !isStreaming) { Text("Native") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { backend = "FFmpeg" }, enabled = backend != "FFmpeg" && !isStreaming) { Text("FFmpeg") }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Selected: $backend")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { deviceName = it },
                        label = { Text("Audio device name (optional)") },
                        modifier = Modifier.weight(1f),
                        enabled = !isStreaming
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        // List PulseAudio/PipeWire sources (monitors)
                        thread(start = true) {
                            try {
                                val pb = ProcessBuilder("pactl", "list", "short", "sources")
                                    .redirectErrorStream(true)
                                    .start()
                                val out = BufferedReader(InputStreamReader(pb.inputStream))
                                val list = mutableListOf<String>()
                                var line: String? = out.readLine()
                                while (line != null) {
                                    val parts = line.trim().split(Regex("\\t+| +"))
                                    if (parts.size >= 2) {
                                        val name = parts[1]
                                        // Only show monitor sources (for system audio capture)
                                        if (name.contains("monitor") || name.contains("loopback")) {
                                            list.add(name)
                                        }
                                    }
                                    line = out.readLine()
                                }
                                out.close()
                                availableDevices = if (list.isEmpty()) {
                                    listOf("No monitor sources found. Use 'List CPAL' for direct device access.")
                                } else {
                                    list
                                }
                                showDeviceDialog = true
                            } catch (e: Exception) {
                                statusMessage = "Failed to list PulseAudio sources: ${e.message}"
                            }
                        }
                    }, enabled = !isStreaming) {
                        Text("List Monitors")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        // List CPAL devices (pipewire, pulse, default - the useful ones)
                        thread(start = true) {
                            try {
                                val list = uniffi.rust_engine.listCpalInputDevices()
                                availableDevices = list.ifEmpty {
                                    listOf("No CPAL devices found")
                                }
                                showDeviceDialog = true
                            } catch (e: Exception) {
                                statusMessage = "Failed to list CPAL devices: ${e.message}"
                            }
                        }
                    }, enabled = !isStreaming) {
                        Text("List CPAL")
                    }
                }

                // Device selection dialog
                if (showDeviceDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeviceDialog = false },
                        title = { Text("Available Audio Sources") },
                        text = {
                            Column {
                                if (availableDevices.isEmpty()) {
                                    Text("No devices found. Make sure PulseAudio/PipeWire is running.")
                                } else {
                                    availableDevices.forEach { dev ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = dev, modifier = Modifier.weight(1f))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(onClick = {
                                                deviceName = dev
                                                showDeviceDialog = false
                                            }) {
                                                Text("Select")
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDeviceDialog = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (!discovering) {
                                discovering = true
                                statusMessage = "Discovering receivers..."
                                thread(start = true) {
                                    try {
                                        val list = discoverReceivers(3)
                                        discoveredDevices = list
                                        showDiscoveryDialog = true
                                        statusMessage = "Found ${'$'}{list.size} device(s)"
                                    } catch (e: Exception) {
                                        statusMessage = "Discovery failed: ${'$'}{e.message}"
                                    } finally {
                                        discovering = false
                                    }
                                }
                            }
                        },
                        enabled = !isStreaming && !discovering,
                        modifier = Modifier.height(44.dp).width(140.dp)
                    ) {
                        Text(if (discovering) "Discovering..." else "Discover")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = {
                            if (isStreaming) {
                                try {
                                    if (backend == "FFmpeg") {
                                        stopFFmpegStream()
                                    } else {
                                        stopStream()
                                        isStreaming = false
                                        statusMessage = "Stopped"
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Error stopping: ${'$'}{e.message}"
                                }
                            } else {
                                if (targetIp.isBlank()) {
                                    statusMessage = "Please enter a valid IP"
                                } else {
                                    try {
                                        val parts = targetIp.split(":")
                                        val host = parts.getOrNull(0) ?: ""
                                        val port = parts.getOrNull(1) ?: "5000"
                                        if (backend == "FFmpeg") {
                                            startFFmpegStream(host, port, if (deviceName.isBlank()) null else deviceName)
                                            isStreaming = true
                                            statusMessage = "FFmpeg streaming to $host:$port..."
                                        } else {
                                            startStream(targetIp, deviceName)
                                            isStreaming = true
                                            statusMessage = "Streaming to ${'$'}targetIp..."
                                        }
                                    } catch (e: Exception) {
                                        statusMessage = "Error starting: ${'$'}{e.message}"
                                        e.printStackTrace()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.height(56.dp).width(200.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isStreaming) "STOP" else "START")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Discovery results dialog
                if (showDiscoveryDialog) {
                    AlertDialog(
                        onDismissRequest = { showDiscoveryDialog = false },
                        title = { Text("Discovered Receivers") },
                        text = {
                            Column {
                                if (discoveredDevices.isEmpty()) {
                                    Text("No devices found.")
                                } else {
                                    discoveredDevices.forEach { entry ->
                                        // entry format: ip:port;name
                                        val parts = entry.split(";")
                                        val host = parts.getOrNull(0)?.split(":")?.getOrNull(0) ?: ""
                                        val port = parts.getOrNull(0)?.split(":")?.getOrNull(1) ?: "5000"
                                        val name = parts.getOrNull(1) ?: "Unknown"
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = name, style = MaterialTheme.typography.bodyLarge)
                                                Text(text = "$host:$port", style = MaterialTheme.typography.bodySmall)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(onClick = {
                                                // When selected, fill targetIp and attempt handshake
                                                targetIp = "$host:$port"
                                                showDiscoveryDialog = false
                                                statusMessage = "Requesting connection to $host..."
                                                thread(start = true) {
                                                    try {
                                                        val deviceName = java.net.InetAddress.getLocalHost().hostName ?: System.getProperty("user.name")
                                                        val res = requestConnectAndWait(host, 10, deviceName)
                                                        when (res.lowercase()) {
                                                            "accepted" -> statusMessage = "Connection accepted by $name"
                                                            "rejected" -> statusMessage = "Connection rejected by $name"
                                                            else -> statusMessage = "Connection timed out"
                                                        }
                                                    } catch (e: Exception) {
                                                        statusMessage = "Handshake failed: ${'$'}{e.message}"
                                                    }
                                                }
                                            }) {
                                                Text("Select")
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDiscoveryDialog = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Status: ${'$'}statusMessage",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        // Refresh native logs
                        thread(start = true) {
                            try {
                                nativeLogs = uniffi.rust_engine.getNativeLogs()
                                showNativeLog = true
                            } catch (e: Exception) {
                                statusMessage = "Failed to fetch native logs: ${'$'}{e.message}"
                            }
                        }
                    }) {
                        Text("Show Logs")
                    }
                }

                if (showNativeLog) {
                    AlertDialog(onDismissRequest = { showNativeLog = false }, title = { Text("Native Logs") }, text = {
                        Column(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                            Text(nativeLogs)
                        }
                    }, confirmButton = { TextButton(onClick = { showNativeLog = false }) { Text("Close") } })
                }
            }
        }
    }
}

fun main() {
    // Initialize native library
    try {
        val osName = System.getProperty("os.name").lowercase()
        val libName = if (osName.contains("win")) "rust_engine.dll" else "librust_engine.so"

        val inputStream = object {}.javaClass.getResourceAsStream("/$libName")
        if (inputStream != null) {
            val tempFile = File.createTempFile("rust_engine", if (osName.contains("win")) ".dll" else ".so")
            tempFile.deleteOnExit()
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            // Set the system property for UniFFI to find the library
            // UniFFI generated code uses: System.getProperty("uniffi.component.$componentName.libraryOverride")
            System.setProperty("uniffi.component.rust_engine.libraryOverride", tempFile.absolutePath)
        } else {
            System.err.println("Could not find library $libName in resources. Assuming it's in java.library.path")
        }
    } catch (e: Exception) {
        System.err.println("Error loading native library: ${e.message}")
        e.printStackTrace()
    }

    application {
        Window(onCloseRequest = ::exitApplication, title = "Aurelay Sender") {
            App()
        }
    }
}
