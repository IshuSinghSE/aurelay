package com.aurelay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurelay.engine.Receiver

/**
 * List of discovered receivers on the network.
 * Displays "Proximity Cards" showing nearby devices with their status.
 * 
 * Design adapts based on context:
 * - Mobile: Bottom sheet presentation
 * - Desktop: Side panel presentation
 * 
 * @param receivers List of discovered receivers
 * @param selectedReceiver Currently selected receiver
 * @param onReceiverSelected Callback when a receiver is clicked
 * @param modifier Optional modifier
 */
@Composable
fun DeviceList(
    receivers: List<Receiver>,
    selectedReceiver: Receiver?,
    onReceiverSelected: (Receiver) -> Unit,
    modifier: Modifier = Modifier
) {
    if (receivers.isEmpty()) {
        // Empty state
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No Receivers Found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Searching for devices...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(receivers) { receiver ->
                ReceiverCard(
                    receiverName = receiver.name,
                    address = receiver.address,
                    latency = receiver.latency,
                    bitrate = receiver.bitrate,
                    isAvailable = receiver.isAvailable,
                    isSelected = receiver.id == selectedReceiver?.id,
                    onClick = { onReceiverSelected(receiver) }
                )
            }
        }
    }
}

/**
 * Receiver card showing discovered network receivers with proximity information.
 * "Proximity Card" design - shows device details with status indicators.
 * 
 * @param receiverName Name of the receiver
 * @param address IP address of the receiver
 * @param latency Latency in milliseconds (optional)
 * @param bitrate Bitrate in kbps (optional)
 * @param isAvailable Whether the receiver is available
 * @param isSelected Whether this receiver is currently selected
 * @param onClick Callback when card is clicked
 * @param modifier Optional modifier
 */
@Composable
fun ReceiverCard(
    receiverName: String,
    address: String,
    latency: Int? = null,
    bitrate: Int? = null,
    isAvailable: Boolean = true,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isAvailable) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isAvailable -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = receiverName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                        isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
                
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                // Show stats if available
                if (latency != null || bitrate != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        latency?.let {
                            Text(
                                text = "${it}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        bitrate?.let {
                            Text(
                                text = "${it}kbps",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
            
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isAvailable -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
            )
        }
    }
}
