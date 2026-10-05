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
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Suppress("Unused")
@Composable
fun SparkingProgressBar(
    progress: Float,
    @Suppress("UNUSED_PARAMETER") isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onProgressChange: (Float) -> Unit = {},
    onScrubbing: (Boolean) -> Unit = {},
    lineCenterOffset: Float = 0f,
) {
    var isDragging by remember { mutableStateOf(value = false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

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
                            if (size.width > 0) {
                                dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            val targetProgress = dragProgress
                            onProgressChange(targetProgress)
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(500.milliseconds)
                                isDragging = false
                                onScrubbing(false)
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            onScrubbing(false)
                        },
                    ) { change, _ ->
                        change.consume()
                        if (size.width > 0) {
                            dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (size.width > 0) {
                            val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            dragProgress = newProgress
                            isDragging = true
                            onProgressChange(newProgress)
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(500.milliseconds)
                                isDragging = false
                            }
                        }
                    }
                },
        ) {
            val width = size.width
            val height = size.height
            if ((width <= 0f) || (height <= 0f)) return@Canvas

            val safeProgress = if (displayProgress.isNaN()) 0f else displayProgress.coerceIn(0f, 1f)
            val lineY = (height / 2f) + lineCenterOffset.dp.toPx()
            val progressX = width * safeProgress
            val strokeWidth = 1.dp.toPx()

            // 1. 未播放部分 (底色)
            drawRect(
                color = accentColor.copy(alpha = 0.2f),
                topLeft = Offset(0f, lineY),
                size = Size(width, strokeWidth),
            )

            // 2. 已播放部分
            if (progressX > 0f) {
                drawRect(
                    color = accentColor.copy(alpha = 0.8f),
                    topLeft = Offset(0f, lineY),
                    size = Size(progressX, strokeWidth),
                )
            }
        }
    }
}
