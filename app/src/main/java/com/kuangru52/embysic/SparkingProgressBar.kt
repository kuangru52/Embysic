package com.kuangru52.embysic

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun SparkingProgressBar(
    progress: Float,
    isPlaying: Boolean,
    accentColor: Color,
    onProgressChange: (Float) -> Unit = {},
    onScrubbing: (Boolean) -> Unit = {},
    lineCenterOffset: Float = 0f, // 新增：控制引线在高度方向上的偏移
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    val displayProgress = if (isDragging) dragProgress else progress

    val infiniteTransition = rememberInfiniteTransition(label = "sparks")
    val sparkAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkAnim"
    )

    Box(modifier = modifier.height(40.dp).fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            onScrubbing(true)
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onProgressChange(dragProgress)
                            isDragging = false
                            onScrubbing(false)
                        },
                        onDragCancel = {
                            isDragging = false
                            onScrubbing(false)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onProgressChange(newProgress)
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val lineY = height / 2 + lineCenterOffset.dp.toPx()
            val progressX = width * displayProgress

            // 1. 底色引线 - 恢复，但不使用全长，而是作为玻璃背景的一部分
            drawRect(
                color = accentColor.copy(alpha = 0.1f),
                topLeft = Offset(0f, lineY - 0.75.dp.toPx()),
                size = Size(width, 1.5.dp.toPx())
            )

            // 2. 已燃烧引线 - 恢复原始设计
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.2f), accentColor.copy(alpha = 0.9f)),
                    startX = 0f, endX = progressX
                ),
                topLeft = Offset(0f, lineY - 0.75.dp.toPx()),
                size = Size(progressX, 1.5.dp.toPx())
            )

            if (isPlaying && progress > 0f) {
                // 3. 燃烧火头 (极简发光点)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, accentColor, Color.Transparent),
                        center = Offset(progressX, lineY),
                        radius = 6.dp.toPx()
                    ),
                    radius = 6.dp.toPx(),
                    center = Offset(progressX, lineY),
                    blendMode = BlendMode.Screen
                )

                // 4. 简约火星 (Simple sparks)
                // 减少数量，简化路径，回归纯粹的燃烧感
                for (i in 0 until 10) {
                    val individualProgress = (sparkAnim + i * 0.1f) % 1f
                    val angle = (i * 36f) // 固定角度均匀分布
                    val rad = (angle * Math.PI / 180f).toFloat()
                    
                    // 较小的散射范围
                    val distance = individualProgress * 12.dp.toPx()
                    
                    val offX = Math.cos(rad.toDouble()).toFloat() * distance
                    val offY = Math.sin(rad.toDouble()).toFloat() * distance
                    
                    val sparkColor = if (i % 2 == 0) Color(0xFFFFD700) else Color(0xFFFF4500)
                    
                    drawCircle(
                        color = sparkColor.copy(alpha = 1f - individualProgress),
                        radius = 1.2.dp.toPx() * (1f - individualProgress),
                        center = Offset(progressX + offX, lineY + offY)
                    )
                }
            }
        }
    }
}
