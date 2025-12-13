package com.devindeed.aurelay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devindeed.aurelay.engine.AudioEngine
import com.devindeed.aurelay.engine.Receiver
import com.devindeed.aurelay.ui.components.DeviceList
import com.devindeed.aurelay.ui.components.Visualizer
import com.devindeed.aurelay.ui.components.CompactVisualizer

/**
 * Audio visualizer and active connections screen.
 * Corresponds to "Audio" tab in navigation.
 */
@Composable
fun AudioVisualizerScreen(
    audioEngine: AudioEngine? = null, // Optional for now to support preview/dummy
    modifier: Modifier = Modifier
) {
    // Dummy State for UI Demo (matching Dashboard pattern)
    var isStreaming by remember { mutableStateOf(true) }
    val activeReceivers = remember {
        listOf(
            Receiver("1", "Living Room TV", "192.168.1.10", 5000, true, 12, 1200),
            Receiver("4", "Kitchen Tablet", "192.168.1.22", 5000, true, 8, 1400)
        )
    }
    var selectedReceiver by remember { mutableStateOf<Receiver?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val isWide = maxWidth >= 900.dp

        if (isWide) {
            DesktopAudioLayout(
                isStreaming = isStreaming,
                receivers = activeReceivers,
                selectedReceiver = selectedReceiver,
                onReceiverSelected = { selectedReceiver = if (selectedReceiver == it) null else it }
            )
        } else {
            MobileAudioLayout(
                isStreaming = isStreaming,
                receivers = activeReceivers,
                selectedReceiver = selectedReceiver,
                onReceiverSelected = { selectedReceiver = if (selectedReceiver == it) null else it }
            )
        }
    }
}

@Composable
private fun MobileAudioLayout(
    isStreaming: Boolean,
    receivers: List<Receiver>,
    selectedReceiver: Receiver?,
    onReceiverSelected: (Receiver) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top: Visualizer
        Text(
            "Audio Visualizer",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.Start).padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                if (isStreaming) {
                    Visualizer(isStreaming = true, modifier = Modifier.fillMaxSize())
                } else {
                    Text("No Audio Stream", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Bottom: Active Connections
        Text(
            "Active Connections",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        DeviceList(
            receivers = receivers,
            selectedReceiver = selectedReceiver,
            onReceiverSelected = onReceiverSelected,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

@Composable
private fun DesktopAudioLayout(
    isStreaming: Boolean,
    receivers: List<Receiver>,
    selectedReceiver: Receiver?,
    onReceiverSelected: (Receiver) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // Left: Visualizer Panel (Main Focus)
        Column(
            modifier = Modifier.weight(0.6f).fillMaxHeight()
        ) {
            Text(
                "Real-time Audio",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (isStreaming) {
                        Visualizer(isStreaming = true, modifier = Modifier.fillMaxSize())
                    } else {
                        Text("Start broadcasting to see visualizer", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // Right: Stats & Connections
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Text(
                "Active Connections",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            DeviceList(
                receivers = receivers,
                selectedReceiver = selectedReceiver,
                onReceiverSelected = onReceiverSelected,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Mini Visualizer / Waveform for individual stats
            Text(
                "Signal Quality",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                 CompactVisualizer(isStreaming = isStreaming, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
