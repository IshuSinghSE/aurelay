package com.aurelay.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Large circular power button matching the design mockup.
 * 
 * Design principles from mockup:
 * - Huge circular button (200dp on mobile, 180dp on desktop)
 * - Glowing red color when streaming with "Stop" text
 * - Gradient background and border
 * - Pulsing animation when active
 * - "Broadcasting Audio" status text below (handled by parent)
 * 
 * @param isStreaming Current streaming state
 * @param enabled Whether the button is enabled
 * @param onClick Callback when button is clicked
 * @param modifier Optional modifier
 */
@Composable
fun PowerButton(
    isStreaming: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    @Suppress("ModifierParameter") modifier: Modifier = Modifier
) {
    // Scale animation
    val scale by animateFloatAsState(
        targetValue = if (isStreaming) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "power_button_scale"
    )
    
    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "power_button_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    val glowSize by infiniteTransition.animateFloat(
        initialValue = 200f,
        targetValue = 220f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_size"
    )
    
    Box(
        modifier = modifier.size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow when streaming (matches mockup's red glow)
        if (isStreaming) {
            Box(
                modifier = Modifier
                    .size(glowSize.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha * 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        
        // Main button
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(scale)
                .shadow(
                    elevation = if (isStreaming) 24.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = if (isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    spotColor = if (isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                .clip(CircleShape)
                .background(
                    brush = if (isStreaming) {
                        // Vibrant red gradient when streaming
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.error,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                            )
                        )
                    } else {
                        // Dark subtle gradient when idle
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                )
                .border(
                    width = 3.dp,
                    color = if (isStreaming) 
                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    else 
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isStreaming) "Stop" else "Start",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = if (isStreaming)
                    Color.White
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                letterSpacing = 1.5.sp
            )
        }
    }
}
