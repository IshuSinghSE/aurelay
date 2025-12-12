package com.aurelay.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurelay.engine.AudioEngine
import com.aurelay.engine.StreamState
import com.aurelay.ui.components.DeviceListItem
import com.aurelay.ui.components.HeroControl
import com.aurelay.ui.components.StatsCard
import com.aurelay.ui.components.Visualizer
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.aurelay.engine.Receiver

/**
 * The Dashboard Screen (Main).
 *
 * Layout:
 * - Desktop: Two-Pane Split.
 *   - Left (35%): Hero + My IP.
 *   - Right (65%): Device List + Stats.
 * - Mobile: Vertical Scroll.
 */
@Composable
fun DashboardScreen(
    audioEngine: AudioEngine,
    isDesktop: Boolean
) {
    val streamState by audioEngine.streamState.collectAsState()
    val devices by audioEngine.discoveredReceivers.collectAsState()
    val visuals by audioEngine.visuals.collectAsState()
    val scope = rememberCoroutineScope()
    
    androidx.compose.runtime.LaunchedEffect(Unit) {
        audioEngine.startDiscovery()
    }

    if (isDesktop) {
        DesktopDashboard(
            streamState = streamState,
            devices = devices,
            visuals = visuals,
            onToggleStream = {
                scope.launch {
                    if (streamState == StreamState.Streaming) {
                        audioEngine.stopStreaming()
                    } else {
                        // Select a receiver to stream to properly
                        // For now, if there is a device, connect to the first one or just trigger UI state
                        if (devices.isNotEmpty()) {
                             audioEngine.startStreaming(devices.first())
                        }
                    }
                }
            },
            onConnect = { receiver ->
                scope.launch { audioEngine.startStreaming(receiver) }
            }
        )
    } else {
        MobileDashboard(
            streamState = streamState,
            devices = devices,
            visuals = visuals,
            onToggleStream = {
                scope.launch {
                    if (streamState == StreamState.Streaming) {
                        audioEngine.stopStreaming()
                    } else {
                        if (devices.isNotEmpty()) {
                            audioEngine.startStreaming(devices.first())
                        }
                    }
                }
            },
            onConnect = { receiver ->
                scope.launch { audioEngine.startStreaming(receiver) }
            }
        )
    }
}

@Composable
fun DesktopDashboard(
    streamState: StreamState,
    devices: List<Receiver>,
    visuals: List<Float>,
    onToggleStream: () -> Unit,
    onConnect: (Receiver) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Left Pane (35%)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.35f)
                .padding(end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HeroControl(
                streamState = streamState,
                onClick = onToggleStream,
                size = 200.dp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Visualizer(
                audioData = visuals,
                isStreaming = streamState == StreamState.Streaming,
                barCount = 30
            )
        }

        // Right Pane (65%)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.65f)
        ) {
            Text(
                text = "Nearby Devices",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(devices) { device ->
                    DeviceListItem(
                        device = device,
                        onClick = { onConnect(device) }
                    )
                }
                if (devices.isEmpty()) {
                    item {
                        Text(
                            text = "No devices found...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Active Stats",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatsCard("Latency", "21ms", Modifier.weight(1f))
                StatsCard("Bitrate", "34Mbps", Modifier.weight(1f))
                StatsCard("Packet Loss", "0%", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MobileDashboard(
    streamState: StreamState,
    devices: List<Receiver>,
    visuals: List<Float>,
    onToggleStream: () -> Unit,
    onConnect: (Receiver) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Top Section: Hero
        Box(
            modifier = Modifier.fillMaxWidth().weight(0.4f),
            contentAlignment = Alignment.Center
        ) {
            HeroControl(
                streamState = streamState,
                onClick = onToggleStream,
                size = 160.dp
            )
        }
        
        Visualizer(
            audioData = visuals,
            isStreaming = streamState == StreamState.Streaming,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Bottom Section: Devices
        Text(
            text = "Nearby Devices",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(0.6f)
        ) {
            items(devices) { device ->
                DeviceListItem(
                    device = device,
                    onClick = { onConnect(device) }
                )
            }
        }
    }
}
