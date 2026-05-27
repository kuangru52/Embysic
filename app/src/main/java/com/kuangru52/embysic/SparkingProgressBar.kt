package com.kuangru52.embysic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun SparkingProgressBar(
    progress: Float,
    @Suppress("UNUSED_PARAMETER") isPlaying: Boolean,
    accentColor: Color,
    onProgressChange: (Float) -> Unit = {},
    onScrubbing: (Boolean) -> Unit = {},
    lineCenterOffset: Float = 0f, 
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    val displayProgress = if (isDragging) dragProgress else progress

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
            val strokeWidth = 1.dp.toPx()

            // 1. 未播放部分 (底色)
            drawRect(
                color = accentColor.copy(alpha = 0.2f),
                topLeft = Offset(0f, lineY),
                size = Size(width, strokeWidth)
            )

            // 2. 已播放部分
            drawRect(
                color = accentColor.copy(alpha = 0.8f),
                topLeft = Offset(0f, lineY),
                size = Size(progressX, strokeWidth)
            )
        }
    }
}
