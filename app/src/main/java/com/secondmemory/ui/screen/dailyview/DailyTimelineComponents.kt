package com.secondmemory.ui.screen.dailyview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondmemory.util.todayDayKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * UI model for one day's summary and activity status.
 */
data class DaySummaryItem(
    val dayKey: String,
    val hasSummary: Boolean,
    val thoughtCount: Int,
    val thoughtTimestamps: List<Long>,
    val summaryLastUpdatedMillis: Long?,
    val lastThoughtUpdatedMillis: Long?,
    val fileName: String,
) {
    /**
     * True if thoughts have been modified after the summary was last generated.
     */
    val needsRefresh: Boolean
        get() = hasSummary && 
                summaryLastUpdatedMillis != null &&
                lastThoughtUpdatedMillis != null &&
                lastThoughtUpdatedMillis > (summaryLastUpdatedMillis + 1000)

    /**
     * Returns a user-friendly status message based on the current state of the day's activity.
     */
    fun getStatusText(): String? {
        if (needsRefresh) {
            val newCount = if (summaryLastUpdatedMillis != null) {
                thoughtTimestamps.count { it > (summaryLastUpdatedMillis + 1000) }
            } else 0
            return if (newCount > 0) {
                "$newCount captures awaiting synthesis"
            } else {
                "Synthesis out of date"
            }
        }
        
        if (!hasSummary) {
            return if (thoughtCount > 0) {
                "$thoughtCount captures awaiting synthesis"
            } else {
                "No activity recorded"
            }
        }

        return null // Synchronized - hide label
    }
}

/**
 * A creative vertical timeline entry for one day.
 */
@Composable
fun TimelineItem(
    item: DaySummaryItem,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onSummarize: () -> Unit,
) {
    val date = LocalDate.parse(item.dayKey, DateTimeFormatter.BASIC_ISO_DATE)
    val isToday = item.dayKey == todayDayKey()
    val hasThoughts = item.thoughtCount > 0
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val nodeSize = if (isToday) 14.dp else 10.dp
    val bloomMultiplier = 1f + (item.thoughtCount.toFloat() / 20f).coerceAtMost(1f)
    val finalNodeSize = nodeSize * bloomMultiplier

    val baseColor = when {
        isBusy -> MaterialTheme.colorScheme.primary
        item.needsRefresh -> MaterialTheme.colorScheme.error
        item.hasSummary -> MaterialTheme.colorScheme.primary
        hasThoughts -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .clickable(enabled = item.hasSummary) { onOpen() }
            .padding(horizontal = 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline Column
        Box(
            modifier = Modifier.width(48.dp).fillMaxHeight()
        ) {
            // Continuous dashed line - Drawn BEFORE (behind) the circle to ensure the dot is clear
            val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = lineColor,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // Node with Bloom Effect - Positioned in a fixed area for stable centering
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp), // Aligns with the date stamp height
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(finalNodeSize)
                        .scale(if (item.needsRefresh) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(baseColor)
                        .then(
                            if (item.hasSummary && !item.needsRefresh) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            } else Modifier
                        )
                )
            }
        }

        // Content Column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 20.dp, top = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Journal Stamp Look
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEEE")).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("MMM dd")),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                if (isBusy) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (hasThoughts) {
                    IconButton(
                        onClick = onSummarize,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (item.needsRefresh) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Summarize",
                            tint = if (item.needsRefresh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Activity Distribution Sparkline
            if (hasThoughts) {
                ActivitySparkline(timestamps = item.thoughtTimestamps)
                Spacer(modifier = Modifier.height(8.dp))
            }

            val statusText = item.getStatusText()

            if (statusText != null) {
                if (item.needsRefresh || (hasThoughts && !item.hasSummary)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (item.needsRefresh) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f) 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, 
                            if (item.needsRefresh) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val iconColor = if (item.needsRefresh) MaterialTheme.colorScheme.error 
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            
                            Icon(
                                Icons.Default.Refresh, 
                                null, 
                                modifier = Modifier.size(16.dp), 
                                tint = iconColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (item.needsRefresh) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * A minimalist horizontal sparkline showing the distribution of thoughts over 24 hours.
 * Supports scrubbing (drag after long-press) to view specific capture times.
 */
@Composable
private fun ActivitySparkline(timestamps: List<Long>) {
    val haptic = LocalHapticFeedback.current
    var scrubTime by remember { mutableStateOf<Long?>(null) }
    var scrubX by remember { mutableFloatStateOf(0f) }

    val activityColor = MaterialTheme.colorScheme.primary
    val onActivityColor = MaterialTheme.colorScheme.onPrimaryContainer

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        // Scrubbing Info Overlay
        AnimatedVisibility(
            visible = scrubTime != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            scrubTime?.let { ts ->
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = onActivityColor,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            text = com.secondmemory.util.formatTime(ts),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(timestamps) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            scrubX = offset.x.coerceIn(0f, size.width.toFloat())
                            scrubTime = findClosestTimestamp(scrubX, size.width.toFloat(), timestamps)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, _ ->
                            val newX = change.position.x.coerceIn(0f, size.width.toFloat())
                            scrubX = newX // Update position smoothly
                            val newTime = findClosestTimestamp(newX, size.width.toFloat(), timestamps)
                            if (newTime != scrubTime) {
                                scrubTime = newTime
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = { scrubTime = null },
                        onDragCancel = { scrubTime = null }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            
            // Background track
            drawLine(
                color = Color.Gray.copy(alpha = 0.1f),
                start = Offset(0f, height / 2),
                end = Offset(width, height / 2),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Thought Pips
            timestamps.forEach { ts ->
                val localDateTime = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalTime()
                val totalMinutes = localDateTime.hour * 60 + localDateTime.minute
                val dayFraction = totalMinutes / 1440f
                val x = dayFraction * width
                
                val isScrubbed = scrubTime == ts
                
                // Outer glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isScrubbed) activityColor.copy(alpha = 0.6f) else activityColor.copy(alpha = 0.4f), 
                            Color.Transparent
                        ),
                        center = Offset(x, height / 2),
                        radius = if (isScrubbed) 14.dp.toPx() else 10.dp.toPx()
                    ),
                    radius = if (isScrubbed) 14.dp.toPx() else 10.dp.toPx(),
                    center = Offset(x, height / 2)
                )
                
                // Inner core
                drawCircle(
                    color = if (isScrubbed) onActivityColor else activityColor,
                    radius = if (isScrubbed) 4.dp.toPx() else 3.dp.toPx(),
                    center = Offset(x, height / 2)
                )
            }

            // Scrub indicator line
            if (scrubTime != null) {
                drawLine(
                    color = activityColor.copy(alpha = 0.5f),
                    start = Offset(scrubX, 0f),
                    end = Offset(scrubX, height),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().alpha(0.4f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
            Text("12:00", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
            Text("23:59", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
        }
    }
}

/**
 * Finds the timestamp closest to the horizontal scrub position.
 */
private fun findClosestTimestamp(x: Float, totalWidth: Float, timestamps: List<Long>): Long? {
    if (timestamps.isEmpty()) return null
    
    return timestamps.minByOrNull { ts ->
        val localDateTime = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalTime()
        val totalMinutes = localDateTime.hour * 60 + localDateTime.minute
        val dayFraction = totalMinutes / 1440f
        val pipX = dayFraction * totalWidth
        kotlin.math.abs(x - pipX)
    }
}
