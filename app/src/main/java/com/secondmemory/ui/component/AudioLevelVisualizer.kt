package com.secondmemory.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
    barColor: Color = Color(0xFF2E7D32),
) {
    val transition = rememberInfiniteTransition(label = "audio-visualizer")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        val bars = 7
        val spacing = 10.dp.toPx()
        val availableWidth = size.width - spacing * (bars - 1)
        val barWidth = availableWidth / bars

        for (index in 0 until bars) {
            val t = index.toFloat() / bars
            val oscillation = ((sin(phase.value + (t * PI).toFloat()) + 1f) / 2f)
            val liveLevel = if (isListening) {
                (normalizedLevel * 0.75f) + (oscillation * 0.25f)
            } else {
                0.08f
            }
            drawBar(
                index = index,
                bars = bars,
                barWidth = barWidth,
                spacing = spacing,
                normalizedLevel = liveLevel,
                color = barColor,
            )
        }
    }
}

/**
 * Draws a single rounded bar with baseline padding so silent bars are still visible.
 */
private fun DrawScope.drawBar(
    index: Int,
    bars: Int,
    barWidth: Float,
    spacing: Float,
    normalizedLevel: Float,
    color: Color,
) {
    val minHeightRatio = 0.15f
    val heightRatio = minHeightRatio + (normalizedLevel.coerceIn(0f, 1f) * (1f - minHeightRatio))
    val barHeight = size.height * heightRatio
    val x = index * (barWidth + spacing)
    val y = (size.height - barHeight) / 2f

    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(x, y),
        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
    )
}
