package com.aurelay.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings screen matching the design mockup.
 * 
 * Features:
 * - Auto-start Service toggle
 * - Audio Output selector
 * - Audio Notification toggle  
 * - Audio Connections segmented button
 * - Layout Connections section
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    var autoStartService by remember { mutableStateOf(false) }
    var audioOutput by remember { mutableStateOf(true) }
    var audioNotification by remember { mutableStateOf(true) }
    var selectedAudioConnection by remember { mutableIntStateOf(0) } // 0=Off, 1=Main, 2=Sysdefault, 3=Mfd
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        // Auto-start Service
        SettingItem(
            title = "Auto-start Service",
            description = "Automatically start receiver service when Aurelay launches",
            checked = autoStartService,
            onCheckedChange = { autoStartService = it }
        )
        
        Divider()
        
        // Audio Output
        SettingItem(
            title = "Audio Output",
            description = "Enable audio output (Monitor)",
            checked = audioOutput,
            onCheckedChange = { audioOutput = it }
        )
        
        Divider()
        
        // Audio Notification
        SettingItem(
            title = "Audio Notification",
            description = "Warn when sender device could not be verified",
            checked = audioNotification,
            onCheckedChange = { audioNotification = it }
        )
        
        Divider()
        
        // Audio Connections
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Audio Connections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Audio enable output device (auto connections like precedent)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Segmented button group
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Off", "Main", "Sysdefault", "Mfd").forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedAudioConnection == index,
                        onClick = { selectedAudioConnection = index },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        Divider()
        
        // Layout Connections
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Layout Connections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
