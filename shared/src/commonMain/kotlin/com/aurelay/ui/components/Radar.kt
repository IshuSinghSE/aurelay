package com.aurelay.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Radar scanning animation displayed during device discovery.
 * Shows expanding ripple circles to indicate active scanning.
 * 
 * Design inspiration: Sonar/radar sweep effect
 * - Multiple expanding circles
 * - Fading alpha as they expand
 * - Continuous animation loop
 * 
 * @param isScanning Whether scanning is active
 * @param size Size of the radar animation
 * @param modifier Optional modifier
 */
@Composable
fun Radar(
    isScanning: Boolean,
    size: Dp = 200.dp,
    @Suppress("ModifierParameter") modifier: Modifier = Modifier
) {
    if (!isScanning) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "radar_animation")
    
    // Create three ripples with different start times for wave effect
    val ripple1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_1"
    )
    
    val ripple2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(666) // 1/3 delay
        ),
        label = "ripple_2"
    )
    
    val ripple3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1333) // 2/3 delay
        ),
        label = "ripple_3"
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val centerX = this.size.width / 2
            val centerY = this.size.height / 2
            val maxRadius = this.size.minDimension / 2
            
            // Draw three ripples
            drawRipple(ripple1, centerX, centerY, maxRadius, primaryColor)
            drawRipple(ripple2, centerX, centerY, maxRadius, primaryColor)
            drawRipple(ripple3, centerX, centerY, maxRadius, primaryColor)
        }
    }
}

/**
 * Helper function to draw a single ripple circle.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRipple(
    progress: Float,
    centerX: Float,
    centerY: Float,
    maxRadius: Float,
    color: Color
) {
    val radius = progress * maxRadius
    val alpha = (1f - progress) * 0.6f // Fade out as it expands
    
    if (alpha > 0) {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
