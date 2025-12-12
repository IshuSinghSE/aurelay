package com.aurelay.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurelay.ui.Screen

/**
 * Bottom navigation bar for mobile layout.
 * Matches the design mockup with Dashboard, Audio, and Settings tabs.
 */
@Composable
fun BottomNavBar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
            label = { Text("Dashboard") },
            selected = currentScreen == Screen.Dashboard,
            onClick = { onNavigate(Screen.Dashboard) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        
        NavigationBarItem(
            icon = { Icon(Icons.Default.Star, contentDescription = "Audio") },
            label = { Text("Audio") },
            selected = currentScreen == Screen.Audio,
            onClick = { onNavigate(Screen.Audio) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = currentScreen == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

/**
 * Side navigation rail for desktop/tablet layout.
 */
@Composable
fun SideNavRail(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = {
            Icon(
                Icons.Default.Star,
                contentDescription = "Aurelay",
                modifier = Modifier.padding(vertical = 16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        Spacer(Modifier.weight(1f))
        
        NavigationRailItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
            label = { Text("Dashboard") },
            selected = currentScreen == Screen.Dashboard,
            onClick = { onNavigate(Screen.Dashboard) }
        )
        
        NavigationRailItem(
            icon = { Icon(Icons.Default.Star, contentDescription = "Audio") },
            label = { Text("Audio") },
            selected = currentScreen == Screen.Audio,
            onClick = { onNavigate(Screen.Audio) }
        )
        
        NavigationRailItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = currentScreen == Screen.Settings,
            onClick = { onNavigate(Screen.Settings) }
        )
        
        Spacer(Modifier.weight(1f))
    }
}
