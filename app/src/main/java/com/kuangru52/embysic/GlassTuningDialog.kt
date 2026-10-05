package com.kuangru52.embysic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DockGlassTuningDialog(
    cornerRadius: Float,
    blurRadius: Float,
    refractionAmount: Float,
    refractionHeight: Float,
    bleedAmount: Float,
    bleedOpacity: Float,
    chromaMultiplier: Float,
    contrast: Float,
    whitePoint: Float,
    onParamChange: (Float, Float, Float, Float, Float, Float, Float, Float, Float) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    GlassTuningDialog(
        title = "液态玻璃 3D 调参 — 【底栏】",
        cornerRadius = cornerRadius,
        blurRadius = blurRadius,
        refractionAmount = refractionAmount,
        refractionHeight = refractionHeight,
        bleedAmount = bleedAmount,
        bleedOpacity = bleedOpacity,
        chromaMultiplier = chromaMultiplier,
        contrast = contrast,
        whitePoint = whitePoint,
        onParamChange = onParamChange,
        onReset = onReset,
        onSave = onSave,
        onClose = onClose,
    )
}

@Composable
fun SearchGlassTuningDialog(
    cornerRadius: Float,
    blurRadius: Float,
    refractionAmount: Float,
    refractionHeight: Float,
    bleedAmount: Float,
    bleedOpacity: Float,
    chromaMultiplier: Float,
    contrast: Float,
    whitePoint: Float,
    onParamChange: (Float, Float, Float, Float, Float, Float, Float, Float, Float) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    GlassTuningDialog(
        title = "液态玻璃 3D 调参 — 【搜索框】",
        cornerRadius = cornerRadius,
        blurRadius = blurRadius,
        refractionAmount = refractionAmount,
        refractionHeight = refractionHeight,
        bleedAmount = bleedAmount,
        bleedOpacity = bleedOpacity,
        chromaMultiplier = chromaMultiplier,
        contrast = contrast,
        whitePoint = whitePoint,
        onParamChange = onParamChange,
        onReset = onReset,
        onSave = onSave,
        onClose = onClose,
    )
}

@Composable
fun GlassTuningDialog(
    title: String = "Kyant0 液态玻璃 3D 调参",
    cornerRadius: Float,
    blurRadius: Float,
    refractionAmount: Float,
    refractionHeight: Float,
    bleedAmount: Float,
    bleedOpacity: Float,
    chromaMultiplier: Float,
    contrast: Float,
    whitePoint: Float,
    onParamChange: (
        cornerRadius: Float,
        blurRadius: Float,
        refractionAmount: Float,
        refractionHeight: Float,
        bleedAmount: Float,
        bleedOpacity: Float,
        chromaMultiplier: Float,
        contrast: Float,
        whitePoint: Float,
    ) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Surface(
        color = Color(0xEB1C2536),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.25f)),
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(360.dp)
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            // 顶层标题栏：仅在此手势区域才允许拖拽窗口位置，避免干扰下方参数滑动条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Row {
                    IconButton(onClick = onReset, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "重置", tint = Color.White)
                    }
                    IconButton(onClick = onSave, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Save, contentDescription = "保存", tint = Color(0xFF4CAF50))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 超精简极细滑动条列表（仅在此处滑动调参，禁止拖拽窗口）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 1. Corner radius
                SlimTuningSliderRow(
                    label = "Corner radius 圆角",
                    value = cornerRadius.coerceIn(10f, 60f),
                    valueText = "${cornerRadius.toInt()} dp",
                    valueRange = 10f..60f,
                ) {
                    onParamChange(it, blurRadius, refractionAmount, refractionHeight, bleedAmount, bleedOpacity, chromaMultiplier, contrast, whitePoint)
                }

                // 2. Blur radius
                SlimTuningSliderRow(
                    label = "Blur radius 模糊",
                    value = blurRadius.coerceIn(0f, 24f),
                    valueText = "${blurRadius.toInt()} dp",
                    valueRange = 0f..24f,
                ) {
                    onParamChange(cornerRadius, it, refractionAmount, refractionHeight, bleedAmount, bleedOpacity, chromaMultiplier, contrast, whitePoint)
                }

                // 3. Refraction amount
                SlimTuningSliderRow(
                    label = "Refraction amount 折射",
                    value = refractionAmount.coerceIn(-300f, 0f),
                    valueText = "${refractionAmount.toInt()} dp",
                    valueRange = -300f..0f,
                ) {
                    onParamChange(cornerRadius, blurRadius, it, refractionHeight, bleedAmount, bleedOpacity, chromaMultiplier, contrast, whitePoint)
                }

                // 4. Refraction height
                SlimTuningSliderRow(
                    label = "Refraction height 高度",
                    value = refractionHeight.coerceIn(0f, 30f),
                    valueText = String.format(Locale.getDefault(), "%.1f dp", refractionHeight),
                    valueRange = 0f..30f,
                ) {
                    onParamChange(cornerRadius, blurRadius, refractionAmount, it, bleedAmount, bleedOpacity, chromaMultiplier, contrast, whitePoint)
                }

                // 5. Bleed amount
                SlimTuningSliderRow(
                    label = "Bleed amount 渗色",
                    value = bleedAmount.coerceIn(0f, 40f),
                    valueText = String.format(Locale.getDefault(), "%.1f dp", bleedAmount),
                    valueRange = 0f..40f,
                ) {
                    onParamChange(cornerRadius, blurRadius, refractionAmount, refractionHeight, it, bleedOpacity, chromaMultiplier, contrast, whitePoint)
                }

                // 6. Bleed opacity
                SlimTuningSliderRow(
                    label = "Bleed opacity 渗色不透明",
                    value = bleedOpacity.coerceIn(0f, 1f),
                    valueText = String.format(Locale.getDefault(), "%.2f", bleedOpacity),
                    valueRange = 0f..1f,
                ) {
                    onParamChange(cornerRadius, blurRadius, refractionAmount, refractionHeight, bleedAmount, it, chromaMultiplier, contrast, whitePoint)
                }

                // 7. Chroma multiplier
                SlimTuningSliderRow(
                    label = "Chroma 彩度增强",
                    value = chromaMultiplier.coerceIn(0.50f, 2.00f),
                    valueText = String.format(Locale.getDefault(), "%.2f", chromaMultiplier),
                    valueRange = 0.50f..2.00f,
                ) {
                    onParamChange(cornerRadius, blurRadius, refractionAmount, refractionHeight, bleedAmount, bleedOpacity, it, contrast, whitePoint)
                }

                // 8. Contrast
                SlimTuningSliderRow(
                    label = "Contrast 对比度",
                    value = contrast.coerceIn(-0.50f, 0.50f),
                    valueText = String.format(Locale.getDefault(), "%.2f", contrast),
                    valueRange = -0.50f..0.50f,
                ) {
                    onParamChange(cornerRadius, blurRadius, refractionAmount, refractionHeight, bleedAmount, bleedOpacity, chromaMultiplier, it, whitePoint)
                }

                // 9. White point
                SlimTuningSliderRow(
                    label = "White point 白点高光",
                    value = whitePoint.coerceIn(-1.00f, 1.00f),
                    valueText = String.format(Locale.getDefault(), "%.2f", whitePoint),
                    valueRange = -1.00f..1.00f,
                ) {
                    onParamChange(cornerRadius, blurRadius, refractionAmount, refractionHeight, bleedAmount, bleedOpacity, chromaMultiplier, contrast, it)
                }
            }
        }
    }
}

@Composable
fun SlimTuningSliderRow(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.95f),
            )
            Text(
                text = valueText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        ExquisiteGlassSlider(
            value = value,
            onValueChange = onValueChange,
            isDark = true,
            valueRange = valueRange,
            displayValueText = valueText,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
