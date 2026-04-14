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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    // Animate color for more visual interest
    val animatedColor = transition.animateColor(
        initialValue = Color(0xFF42A5F5),
        targetValue = Color(0xFFAB47BC),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathing-color",
    )

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
            // Let audio level dominate, breathing is a subtle base
            // Increase sensitivity: audio level is the dominant factor
            val radius = minRadius + (maxRadius - minRadius) * (0.05f + 0.90f * animatedLevel + 0.05f * breathing)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedColor.value, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                center = center,
                radius = radius
            )
        }
    }
}


