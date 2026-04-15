package com.secondmemory.ui.component

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draws a lightweight animated audio visualizer that reacts to microphone loudness levels.
 */
@Composable
fun AudioLevelVisualizer(
    normalizedLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
) {
    // Smooth the normalizedLevel input for fluid animation
    val animatedLevel by animateFloatAsState(
        targetValue = normalizedLevel,
        animationSpec = tween(durationMillis = if (isListening) 350 else 700, easing = EaseInOutCubic),
        label = "audio-level-smooth"
    )
    val transition = rememberInfiniteTransition(label = "audio-visualizer")
    // Breathing effect: slow, smooth, ease-in-out
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Restart,
        ),
        label = "breathing-phase",
    )
    // Use Material 3 color scheme for visualizer
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val primaryFixed = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val primaryFixedDim = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val outline = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)
    val shadow = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)
    val animatedAlpha = 0.7f + 0.3f * ((sin(phase.value) + 1f) / 2f)
    val animatedAlpha2 = 0.5f + 0.5f * ((sin(phase.value + 1f) + 1f) / 2f)
    val animatedAlpha3 = 0.4f + 0.6f * ((sin(phase.value + 2f) + 1f) / 2f)

    // Elegant breathing circle
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 48.dp), // shift upwards visually
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val minRadius = size.minDimension * 0.28f
            val maxRadius = size.minDimension * 0.60f
            val breathing = ((sin(phase.value) + 1f) / 2f) // 0..1
            val radius = minRadius + (maxRadius - minRadius) * (0.05f + 0.90f * animatedLevel + 0.05f * breathing)

            // Layer 1: Shadow for depth
            drawCircle(
                color = shadow,
                center = center,
                radius = radius * 1.08f
            )
            // Layer 2: Primary fixed dim
            drawCircle(
                color = primaryFixedDim.copy(alpha = animatedAlpha3 * 0.45f),
                center = center,
                radius = radius * 1.00f
            )
            // Layer 3: Primary container
            drawCircle(
                color = primaryContainer.copy(alpha = animatedAlpha2 * 0.55f),
                center = center,
                radius = radius * 0.88f
            )
            // Layer 4: Primary fixed
            drawCircle(
                color = primaryFixed.copy(alpha = animatedAlpha * 0.70f),
                center = center,
                radius = radius * 0.76f
            )
            // Layer 5: Primary (main solid)
            drawCircle(
                color = primary,
                center = center,
                radius = radius * 0.62f
            )
            // Border (outline color)
            drawCircle(
                color = outline,
                center = center,
                radius = radius * 0.62f,
                style = Stroke(width = size.minDimension * 0.04f)
            )
        }
    }
}


