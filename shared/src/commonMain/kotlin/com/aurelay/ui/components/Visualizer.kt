package com.aurelay.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * The Audio Visualizer.
 *
 * - A row of 30-50 vertical bars.
 * - Animates height based on input.
 * - Gradient color (Orange -> Red -> Dark Purple).
 * - Generates random noise if no data.
 */
@Composable
fun Visualizer(
    modifier: Modifier = Modifier,
    audioData: List<Float> = emptyList(),
    isStreaming: Boolean = false,
    barCount: Int = 40
) {
    // Gradient colors
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFF453A), // Orange/Red
            Color(0xFFFF3B30),
            Color(0xFF5E5CE6)  // Dark Purple
        )
    )

    // Simulation of audio data (Mock)
    val infiniteTransition = rememberInfiniteTransition()
    
    // Create specs once to avoid recreating them on every recomposition
    val animationSpecs = remember(barCount) {
        (0 until barCount).map {
            infiniteRepeatable<Float>(
                animation = tween(
                    durationMillis = Random.nextInt(300, 800),
                    delayMillis = Random.nextInt(0, 300),
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            )
        }
    }

    val randomAnimations = (0 until barCount).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 1f,
            animationSpec = animationSpecs[index]
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(barCount) { index ->
            val heightFraction = when {
                audioData.isNotEmpty() -> audioData.getOrElse(index) { 0.05f }.coerceIn(0.05f, 1f)
                isStreaming -> randomAnimations[index].value
                else -> 0.05f
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction)
                    .padding(horizontal = 1.dp)
                    .background(
                        brush = gradientBrush,
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                    )
            )
        }
    }
}
