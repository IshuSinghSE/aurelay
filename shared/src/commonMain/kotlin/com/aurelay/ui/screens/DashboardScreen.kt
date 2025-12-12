package com.aurelay.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurelay.engine.AudioEngine
import com.aurelay.engine.Receiver
import com.aurelay.engine.StreamState
import com.aurelay.ui.components.*
import kotlinx.coroutines.launch

/**
 * Dashboard screen - the main screen showing the power button and receiver list.
 * Matches the design mockup with the large circular power button centered.
 */
@Composable
fun DashboardScreen(
    audioEngine: AudioEngine,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val streamState by audioEngine.streamState.collectAsState()
    val discoveredReceivers by audioEngine.discoveredReceivers.collectAsState()
    val selectedDevice by audioEngine.selectedDevice.collectAsState()
    
    var selectedReceiver by remember { mutableStateOf<Receiver?>(null) }
    val isStreaming = streamState == StreamState.Streaming

    // Update selected receiver when list changes
    LaunchedEffect(discoveredReceivers) {
        if (selectedReceiver == null && discoveredReceivers.isNotEmpty()) {
            selectedReceiver = discoveredReceivers.first()
        }
    }
    
    // Start discovery on launch
    LaunchedEffect(Unit) {
        audioEngine.startDiscovery()
        audioEngine.refreshDevices()
    }
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 600.dp
        
        if (isWideScreen) {
            // Desktop/Tablet layout: Two columns
            Row(modifier = Modifier.fillMaxSize()) {
                // Left: Power button and status
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PowerSection(
                        isStreaming = isStreaming,
                        selectedReceiver = selectedReceiver,
                        onPowerClick = {
                            scope.launch {
                                if (isStreaming) {
                                    audioEngine.stopStreaming()
                                } else {
                                    selectedReceiver?.let { receiver ->
                                        audioEngine.startStreaming(
                                            receiver = receiver,
                                            device = selectedDevice
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
                
                VerticalDivider()
                
                // Right: Device list and visualizer
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp)
                ) {
                    ReceiverListSection(
                        receivers = discoveredReceivers,
                        selectedReceiver = selectedReceiver,
                        onReceiverSelected = { selectedReceiver = it },
                        isStreaming = isStreaming
                    )
                }
            }
        } else {
            // Mobile layout: Single column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Power button at top (when not streaming)
                if (!isStreaming) {
                    PowerSection(
                        isStreaming = false,
                        selectedReceiver = selectedReceiver,
                        onPowerClick = {
                            scope.launch {
                                selectedReceiver?.let { receiver ->
                                    audioEngine.startStreaming(
                                        receiver = receiver,
                                        device = selectedDevice
                                    )
                                }
                            }
                        }
                    )
                    
                    Spacer(Modifier.height(24.dp))
                }
                
                // Receivers list
                ReceiverListSection(
                    receivers = discoveredReceivers,
                    selectedReceiver = selectedReceiver,
                    onReceiverSelected = { selectedReceiver = it },
                    isStreaming = isStreaming,
                    modifier = Modifier.weight(1f)
                )
                
                // Show stop button when streaming (mobile)
                if (isStreaming) {
                    Spacer(Modifier.height(16.dp))
                    PowerButton(
                        isStreaming = true,
                        onClick = {
                            scope.launch {
                                audioEngine.stopStreaming()
                            }
                        },
                        modifier = Modifier.size(160.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PowerSection(
    isStreaming: Boolean,
    selectedReceiver: Receiver?,
    onPowerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Status text
        Text(
            text = if (isStreaming) "Broadcasting Audio" else "Ready to Stream",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        // Large power button
        PowerButton(
            isStreaming = isStreaming,
            enabled = selectedReceiver != null || isStreaming,
            onClick = onPowerClick,
            modifier = Modifier.size(200.dp)
        )
        
        // Connection info
        if (selectedReceiver != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (isStreaming) "Streaming To" else "Selected Receiver",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        selectedReceiver.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        selectedReceiver.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiverListSection(
    receivers: List<Receiver>,
    selectedReceiver: Receiver?,
    onReceiverSelected: (Receiver) -> Unit,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (receivers.isEmpty()) "Searching for Devices..." else "Nearby Devices",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        if (isStreaming) {
            // Show visualizer when streaming
            Visualizer(
                isStreaming = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
        
        // Receiver list
        DeviceList(
            receivers = receivers,
            selectedReceiver = selectedReceiver,
            onReceiverSelected = onReceiverSelected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
