package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun L99OrbCore(
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbAnimation")
    
    // Pulse animation
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbPulse"
    )

    // Spin animation for Thinking
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbRotation"
    )

    // Wave/Speaking animation
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveOffset"
    )

    Box(
        modifier = modifier
            .size(220.dp)
            .testTag("l99_orb_core"),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow layer
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(scale)
                .alpha(0.15f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            when {
                                isListening -> Color.Green
                                isThinking -> ElectricViolet
                                isSpeaking -> CosmicCyan
                                else -> CosmicCyan
                            },
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Interactive dynamic visualizer canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 3f

            when {
                isListening -> {
                    // Draw vibrating green/cyan radar spikes
                    val numSpikes = 60
                    for (i in 0 until numSpikes) {
                        val angle = (i * 360f / numSpikes) * (Math.PI / 180f)
                        val noise = (cos(angle * 6 + waveOffset) * sin(angle * 4) * 15f).toFloat()
                        val startRad = maxRadius - 10f
                        val endRad = maxRadius + 25f + noise
                        
                        val startX = (center.x + cos(angle) * startRad).toFloat()
                        val startY = (center.y + sin(angle) * startRad).toFloat()
                        val endX = (center.x + cos(angle) * endRad).toFloat()
                        val endY = (center.y + sin(angle) * endRad).toFloat()

                        drawLine(
                            color = Color.Green.copy(alpha = 0.8f),
                            start = androidx.compose.ui.geometry.Offset(startX, startY),
                            end = androidx.compose.ui.geometry.Offset(endX, endY),
                            strokeWidth = 3f
                        )
                    }

                    // Inner circle
                    drawCircle(
                        color = Color.Green.copy(alpha = 0.2f),
                        radius = maxRadius - 15f
                    )
                    drawCircle(
                        color = Color.Cyan,
                        radius = maxRadius - 20f,
                        style = Stroke(width = 2f)
                    )
                }

                isThinking -> {
                    // Spinning vortex / portal
                    val numRings = 4
                    for (i in 0 until numRings) {
                        val currentRotation = rotation + (i * 45f)
                        val r = maxRadius * (0.4f + 0.2f * i)
                        drawCircle(
                            color = ElectricViolet.copy(alpha = 0.3f * (numRings - i)),
                            radius = r,
                            style = Stroke(
                                width = 4f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(30f, 20f),
                                    currentRotation
                                )
                            )
                        )
                    }
                    // Pulsating core pink star
                    drawCircle(
                        color = NeonPink,
                        radius = (maxRadius * 0.3f) * scale
                    )
                }

                isSpeaking -> {
                    // Sine waves matching vocal output
                    val path = Path()
                    val numPoints = 100
                    val waveHeight = 35f
                    
                    path.moveTo(0f, center.y)
                    for (i in 0..numPoints) {
                        val x = i * (size.width / numPoints)
                        val angle = (i * 0.1f) + waveOffset
                        val y = center.y + sin(angle) * waveHeight * scale
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    
                    drawPath(
                        path = path,
                        color = CosmicCyan,
                        style = Stroke(width = 5f)
                    )

                    // Secondary out-of-phase wave
                    val path2 = Path()
                    path2.moveTo(0f, center.y)
                    for (i in 0..numPoints) {
                        val x = i * (size.width / numPoints)
                        val angle = (i * 0.15f) - waveOffset
                        val y = center.y + cos(angle) * (waveHeight * 0.6f) * scale
                        if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
                    }
                    drawPath(
                        path = path2,
                        color = ElectricViolet.copy(alpha = 0.6f),
                        style = Stroke(width = 3f)
                    )

                    // Glowing core
                    drawCircle(
                        color = CosmicCyan.copy(alpha = 0.7f),
                        radius = maxRadius * 0.5f * scale
                    )
                }

                else -> {
                    // Idle Breathing Ring
                    drawCircle(
                        color = CosmicCyan.copy(alpha = 0.1f),
                        radius = maxRadius * scale
                    )
                    drawCircle(
                        color = CosmicCyan.copy(alpha = 0.4f),
                        radius = maxRadius,
                        style = Stroke(
                            width = 4f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(20f, 15f),
                                rotation * 0.2f
                            )
                        )
                    )
                    drawCircle(
                        color = CosmicCyan,
                        radius = maxRadius * 0.7f,
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = ElectricViolet,
                        radius = 12f * scale
                    )
                }
            }
        }
    }
}

@Composable
fun TerminalView(
    logs: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .border(1.dp, CosmicCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
        
        // Auto-scroll to end when logs update
        LaunchedEffect(logs.size) {
            if (logs.isNotEmpty()) {
                lazyListState.animateScrollToItem(logs.size - 1)
            }
        }

        Box(modifier = Modifier.padding(8.dp)) {
            androidx.compose.foundation.lazy.LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs.size) { index ->
                    Text(
                        text = logs[index],
                        color = if (logs[index].contains("ERROR")) NeonPink else if (logs[index].contains("SYSTEM")) CyberAccentAmber else CosmicCyan.copy(alpha = 0.9f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SuggestionPills(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(suggestions.size) { index ->
            val text = suggestions[index]
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(CyberSurfaceVariant)
                    .border(1.dp, CosmicCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { onSuggestionClick(text) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = text,
                    color = CosmicCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}
