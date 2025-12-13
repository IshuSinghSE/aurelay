package com.devindeed.aurelay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devindeed.aurelay.engine.AudioEngine

@Composable
fun SettingsScreen(
    audioEngine: AudioEngine,
    modifier: Modifier = Modifier
) {
    // State
    var autoStartService by remember { mutableStateOf(false) }
    var audioOutputMode by remember { mutableStateOf("Audio") }
    var appTheme by remember { mutableStateOf("App") }
    var dynamicColors by remember { mutableStateOf(true) }
    var showVolumeSlider by remember { mutableStateOf(true) }
    var showVisualizer by remember { mutableStateOf(true) }
    var audioVerification by remember { mutableStateOf(true) }
    var audioConnections by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val isWide = maxWidth >= 900.dp
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (isWide) 32.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .padding(horizontal = 8.dp)
            )

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        GeneralSection(
                            autoStartService, { autoStartService = it },
                            audioOutputMode, { audioOutputMode = it },
                            audioVerification, { audioVerification = it },
                            audioConnections, { audioConnections = it }
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        AppearanceSection(
                            appTheme, { appTheme = it },
                            dynamicColors, { dynamicColors = it },
                            showVolumeSlider, { showVolumeSlider = it },
                            showVisualizer, { showVisualizer = it }
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    GeneralSection(
                        autoStartService, { autoStartService = it },
                        audioOutputMode, { audioOutputMode = it },
                        audioVerification, { audioVerification = it },
                        audioConnections, { audioConnections = it }
                    )
                    AppearanceSection(
                        appTheme, { appTheme = it },
                        dynamicColors, { dynamicColors = it },
                        showVolumeSlider, { showVolumeSlider = it },
                        showVisualizer, { showVisualizer = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GeneralSection(
    autoStartService: Boolean, onAutoStartChange: (Boolean) -> Unit,
    audioOutputMode: String, onAudioOutputChange: (String) -> Unit,
    audioVerification: Boolean, onAudioVerificationChange: (Boolean) -> Unit,
    audioConnections: Boolean, onAudioConnectionsChange: (Boolean) -> Unit
) {
    SettingsGroup(title = "General") {
        SettingsItemToggle(
            title = "Auto-start Service",
            subtitle = "Start receiver when app launches",
            checked = autoStartService,
            onCheckedChange = onAutoStartChange
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SettingsItemSegmented(
            title = "Audio Output",
            options = listOf("Receive", "Audio"),
            selected = audioOutputMode,
            onSelected = onAudioOutputChange
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SettingsItemToggle(
            title = "Audio Verification",
            subtitle = "Verify device certificates",
            checked = audioVerification,
            onCheckedChange = onAudioVerificationChange
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SettingsItemToggle(
            title = "External Connections",
            subtitle = "Allow 3rd party devices",
            checked = audioConnections,
            onCheckedChange = onAudioConnectionsChange
        )
    }
}

@Composable
private fun AppearanceSection(
    appTheme: String, onAppThemeChange: (String) -> Unit,
    dynamicColors: Boolean, onDynamicColorsChange: (Boolean) -> Unit,
    showVolumeSlider: Boolean, onShowVolumeSliderChange: (Boolean) -> Unit,
    showVisualizer: Boolean, onShowVisualizerChange: (Boolean) -> Unit
) {
    SettingsGroup(title = "Appearance") {
        SettingsItemSegmented(
            title = "Theme",
            options = listOf("App", "System"),
            selected = appTheme,
            onSelected = onAppThemeChange
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SettingsItemToggle(
            title = "Dynamic Colors",
            subtitle = "Match system wallpaper",
            checked = dynamicColors,
            onCheckedChange = onDynamicColorsChange
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SettingsItemToggle(
            title = "Volume Slider",
            subtitle = "Show on dashboard",
            checked = showVolumeSlider,
            onCheckedChange = onShowVolumeSliderChange
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SettingsItemToggle(
            title = "Audio Visualizer",
            subtitle = "Show animated waveform",
            checked = showVisualizer,
            onCheckedChange = onShowVisualizerChange
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItemToggle(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsItemSegmented(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onSelected(option) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
