package com.kuangru52.embysic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExquisiteGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    displayValueText: String? = null,
) {
    val rangeLength = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val fraction = ((value - valueRange.start) / rangeLength).coerceIn(0f, 1f)

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier.height(36.dp),
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFF2196F3),
            activeTrackColor = Color(0xFF2196F3),
            inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.15f),
        ),
        thumb = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2196F3),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .width(48.dp)
                    .height(24.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = displayValueText ?: "${value.toInt()}",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        track = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(CircleShape)
                        .background(Color(0xFF2196F3)),
                )
            }
        },
    )
}
