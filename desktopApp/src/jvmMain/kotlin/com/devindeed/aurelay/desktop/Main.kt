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
import androidx.compose.material3.AlertDialog

@Composable
@Preview
fun App() {
    var targetIp by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Idle") }
    var discovering by remember { mutableStateOf(false) }
    var showDiscoveryDialog by remember { mutableStateOf(false) }
    var discoveredDevices by remember { mutableStateOf(listOf<String>()) }

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
                    label = { Text("Target IP Address") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    enabled = !isStreaming
                )

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
                                    stopStream()
                                    isStreaming = false
                                    statusMessage = "Stopped"
                                } catch (e: Exception) {
                                    statusMessage = "Error stopping: ${'$'}{e.message}"
                                }
                            } else {
                                if (targetIp.isBlank()) {
                                    statusMessage = "Please enter a valid IP"
                                } else {
                                    try {
                                        startStream(targetIp)
                                        isStreaming = true
                                        statusMessage = "Streaming to ${'$'}targetIp..."
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

                Text(
                    text = "Status: ${'$'}statusMessage",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
