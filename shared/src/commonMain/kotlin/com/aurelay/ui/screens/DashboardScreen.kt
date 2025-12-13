package com.devindeed.aurelay.ui.screens

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devindeed.aurelay.engine.AudioEngine
import com.devindeed.aurelay.engine.Receiver
import com.devindeed.aurelay.ui.components.DeviceList
import com.devindeed.aurelay.ui.components.PowerButton

// Enums for UI state
enum class AppMode {
    Receiver, Sender
}

@Composable
fun DashboardScreen(
    audioEngine: AudioEngine,
    modifier: Modifier = Modifier
) {
    // State
    var appMode by remember { mutableStateOf(AppMode.Sender) }
    var isBroadcasting by remember { mutableStateOf(false) }
    
    // Dummy Data for UI Demo
    // Correct constructor: id, name, address, port, isAvailable, latency, bitrate
    val dummyReceivers = remember {
        listOf(
            Receiver("1", "Living Room TV", "192.168.1.10", 5000, true, 12, 1200),
            Receiver("2", "Bedroom Speaker", "192.168.1.14", 5000, true, 45, 980),
            Receiver("3", "Desktop PC", "192.168.1.20", 5000, false, null, null), // Offline
            Receiver("4", "Kitchen Tablet", "192.168.1.22", 5000, true, 8, 1400)
        )
    }
    var selectedReceiver by remember { mutableStateOf<Receiver?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val isWide = maxWidth >= 900.dp
        
        if (isWide) {
            DesktopLayout(
                appMode = appMode,
                isBroadcasting = isBroadcasting,
                receivers = dummyReceivers,
                selectedReceiver = selectedReceiver,
                onModeChanged = { appMode = it },
                onPowerClick = { isBroadcasting = !isBroadcasting },
                onReceiverSelected = { selectedReceiver = if (selectedReceiver == it) null else it }
            )
        } else {
            MobileLayout(
                appMode = appMode,
                isBroadcasting = isBroadcasting,
                receivers = dummyReceivers,
                selectedReceiver = selectedReceiver,
                onModeChanged = { appMode = it },
                onPowerClick = { isBroadcasting = !isBroadcasting },
                onReceiverSelected = { selectedReceiver = if (selectedReceiver == it) null else it }
            )
        }
    }
}

@Composable
private fun MobileLayout(
    appMode: AppMode,
    isBroadcasting: Boolean,
    receivers: List<Receiver>,
    selectedReceiver: Receiver?,
    onModeChanged: (AppMode) -> Unit,
    onPowerClick: () -> Unit,
    onReceiverSelected: (Receiver) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top: Segmented Control
        SegmentedModeToggle(
            selectedMode = appMode,
            onModeChanged = onModeChanged
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Center: Power Button
        Box(
            modifier = Modifier.weight(0.5f), // Take up some space but allow list to grow
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PowerButton(
                    isStreaming = isBroadcasting,
                    onClick = onPowerClick
                )

                Spacer(modifier = Modifier.height(24.dp))

                StatusText(isBroadcasting)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Bottom: Device List
        Text(
            "Nearby Devices",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        DeviceList(
            receivers = receivers,
            selectedReceiver = selectedReceiver,
            onReceiverSelected = onReceiverSelected,
            modifier = Modifier.weight(0.5f).fillMaxWidth()
        )
    }
}

@Composable
private fun DesktopLayout(
    appMode: AppMode,
    isBroadcasting: Boolean,
    receivers: List<Receiver>,
    selectedReceiver: Receiver?,
    onModeChanged: (AppMode) -> Unit,
    onPowerClick: () -> Unit,
    onReceiverSelected: (Receiver) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left Pane: Controls
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SegmentedModeToggle(
                selectedMode = appMode,
                onModeChanged = onModeChanged
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            PowerButton(
                isStreaming = isBroadcasting,
                onClick = onPowerClick,
                modifier = Modifier.graphicsLayer(scaleX = 1.2f, scaleY = 1.2f) // Slightly larger on desktop
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            StatusText(isBroadcasting)
        }

        // Right Pane: Device List
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
                .padding(24.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(24.dp)
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Nearby Devices",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Optional: Refresh button or count could go here
                Text(
                    "${receivers.size} found",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            DeviceList(
                receivers = receivers,
                selectedReceiver = selectedReceiver,
                onReceiverSelected = onReceiverSelected,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SegmentedModeToggle(
    selectedMode: AppMode,
    onModeChanged: (AppMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(48.dp).width(240.dp)
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            AppMode.values().forEach { mode ->
                val isSelected = mode == selectedMode
                val transition = updateTransition(isSelected, label = "Selection")

                val bgColor by transition.animateColor(label = "BgColor") { selected ->
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                }

                val contentColor by transition.animateColor(label = "ContentColor") { selected ->
                    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(bgColor, RoundedCornerShape(50))
                        .clickable { onModeChanged(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.name,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(isBroadcasting: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (isBroadcasting) "Broadcasting Audio" else "Ready to Broadcast",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isBroadcasting) "Listening on port 5000" else "Tap to start stream",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
