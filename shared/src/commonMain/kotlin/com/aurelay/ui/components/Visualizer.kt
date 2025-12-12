package com.aurelay.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Audio visualizer component showing animated bars during streaming.
 * Matches the design mockup with dynamic red/pink gradient bars.
 */
@Composable
fun Visualizer(
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isStreaming) return
    
    val barCount = 60
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    
    // Animate bars with different phases for wave effect
    val bars = List(barCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 300 + (index % 10) * 50,
                    easing = EaseInOut
                ),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset((index * 30) % 300)
            ),
            label = "bar_$index"
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { animatedHeight ->
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight(animatedHeight.value * 0.8f)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.error,
                                    MaterialTheme.colorScheme.errorContainer
                                )
                            )
                        )
                )
            }
        }
    }
}

/**
 * Simpler waveform visualizer for compact spaces.
 */
@Composable
fun CompactVisualizer(
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isStreaming) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "compact_viz")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    val waveColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val amplitude = height * 0.3f
        
        val path = androidx.compose.ui.graphics.Path()
        path.moveTo(0f, centerY)
        
        for (x in 0..width.toInt() step 5) {
            val angle = (x / width * 360f + phase) * (Math.PI / 180f)
            val y = centerY + (sin(angle) * amplitude).toFloat()
            path.lineTo(x.toFloat(), y)
        }
        
        drawPath(
            path = path,
            color = waveColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )
    }
}
