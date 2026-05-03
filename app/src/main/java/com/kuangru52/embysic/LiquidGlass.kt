package com.kuangru52.embysic

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 容器逻辑 - Kyant 风格液态玻璃底座
 */
fun Modifier.liquidGlassDock(
    isDark: Boolean, 
    cornerRadius: Dp = 30.dp,
    refractionAmount: Float = -60f,
    refractionHeight: Float = 20f
): Modifier = composed {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val refAmountPx = with(density) { refractionAmount.dp.toPx() }
    val refHeightPx = with(density) { refractionHeight.dp.toPx() }
    
    this.graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = RuntimeShader("""
                uniform shader content;
                uniform float2 size;
                uniform float radius;
                uniform float refractionAmount;
                uniform float refractionHeight;
                uniform float isDark;

                float sdRoundedRect(float2 p, float2 b, float r) {
                    float2 q = abs(p) - b + r;
                    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
                }

                half4 main(float2 fragCoord) {
                    float2 center = size * 0.5;
                    float d = sdRoundedRect(fragCoord - center, center, radius);
                    
                    if (d > 0.0) return content.eval(fragCoord);

                    // 物理折射模拟 (Kyant 参数驱动)
                    float2 eps = float2(0.8, 0.0);
                    float dX = sdRoundedRect(fragCoord + eps.xy - center, center, radius) - 
                               sdRoundedRect(fragCoord - eps.xy - center, center, radius);
                    float dY = sdRoundedRect(fragCoord + eps.yx - center, center, radius) - 
                               sdRoundedRect(fragCoord - eps.yx - center, center, radius);
                    
                    float2 normal = normalize(float2(dX, dY) + 0.0001);
                    
                    // 应用折射高度和量
                    float factor = smoothstep(-refractionHeight, 0.0, d);
                    float2 distortion = normal * factor * refractionAmount;
                    
                    half4 col = content.eval(fragCoord + distortion);
                    
                    // 材质增益 (渗透度 0.0，仅保留基础的光泽感)
                    if (isDark > 0.5) {
                        col.rgb = mix(col.rgb, half3(0.0), 0.05);
                        col.rgb += (normal.y * 0.05 + 0.02);
                    } else {
                        col.rgb = mix(col.rgb, half3(1.0), 0.1);
                        col.rgb += (normal.y * 0.08 + 0.05);
                    }
                    
                    return col;
                }
            """.trimIndent())
            
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("radius", radiusPx)
            shader.setFloatUniform("refractionAmount", refAmountPx)
            shader.setFloatUniform("refractionHeight", refHeightPx)
            shader.setFloatUniform("isDark", if (isDark) 1f else 0f)
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
        clip = true
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
    }
}

/**
 * 液态指示器 (Kyant 风格 Metaball)
 */
@Composable
fun Modifier.metaballIndicator(isDark: Boolean, targetX: Float, targetY: Float, radius: Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    
    val animX by animateFloatAsState(targetValue = targetX, animationSpec = spring(0.8f, 200f), label = "mbX")
    val animY by animateFloatAsState(targetValue = targetY, animationSpec = spring(0.8f, 200f), label = "mbY")
    
    val shader = remember { RuntimeShader("""
        uniform shader content;
        uniform float2 p;
        uniform float radius;
        uniform float isDark;

        half4 main(float2 fragCoord) {
            float d = length(fragCoord - p);
            // Kyant 风格的平滑边缘
            float v = smoothstep(radius, radius * 0.3, d);
            
            if (v <= 0.0) return content.eval(fragCoord);
            
            half4 col = content.eval(fragCoord);
            // 简单的反射高光
            float spec = pow(v, 3.0) * 0.15;
            
            if (isDark > 0.5) {
                return mix(col, half4(1, 1, 1, 0.15 + spec), v);
            } else {
                return mix(col, half4(1, 1, 1, 0.45 + spec), v);
            }
        }
    """.trimIndent()) }

    return this.graphicsLayer {
        shader.setFloatUniform("p", animX, animY)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("isDark", if (isDark) 1f else 0f)
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

fun Modifier.glassNavItem(isSelected: Boolean, onClick: () -> Unit): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f, 
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)
    )
    this.scale(scale).clickable(
        interactionSource = remember { MutableInteractionSource() }, 
        indication = null, 
        onClick = onClick
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object LiquidGlassFactory {
    fun createLiquidRenderEffect(
        width: Float,
        height: Float,
        rect: RectF,
        radius: Float,
        refractionAmount: Float,
        refractionHeight: Float,
        blurRadius: Float,
        isDark: Boolean,
        rect2: RectF? = null // 新增支持第二个区域（如弹窗）
    ): RenderEffect {
        val shader = RuntimeShader("""
            uniform shader content;
            uniform float4 glassRect;
            uniform float4 glassRect2;
            uniform float hasRect2;
            uniform float radius;
            uniform float refractionAmount;
            uniform float refractionHeight;
            uniform float blurRadius;
            uniform float isDark;

            float sdRoundedRect(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + r;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
            }

            // 平滑最小值函数，用于融合两个矩形
            float smin(float a, float b, float k) {
                float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
                return mix(b, a, h) - k * h * (1.0 - h);
            }

            half4 main(float2 fragCoord) {
                float2 s1 = (glassRect.zw - glassRect.xy) * 0.5;
                float2 c1 = glassRect.xy + s1;
                float d1 = sdRoundedRect(fragCoord - c1, s1, radius);

                float d = d1;
                if (hasRect2 > 0.5) {
                    float2 s2 = (glassRect2.zw - glassRect2.xy) * 0.5;
                    float2 c2 = glassRect2.xy + s2;
                    float d2 = sdRoundedRect(fragCoord - c2, s2, radius);
                    // 使用 smin 融合两个区域，消除相交处的硬线条
                    d = smin(d1, d2, 12.0);
                }
                
                if (d > 0.0) return content.eval(fragCoord);

                // 物理折射：仅在边缘极小范围内产生 (收窄至 refractionHeight)
                float2 eps = float2(0.5, 0.0);
                float d_up = sdRoundedRect(fragCoord + eps.yx - c1, s1, radius);
                float d_dn = sdRoundedRect(fragCoord - eps.yx - c1, s1, radius);
                float d_rt = sdRoundedRect(fragCoord + eps.xy - c1, s1, radius);
                float d_lf = sdRoundedRect(fragCoord - eps.xy - c1, s1, radius);
                
                if (hasRect2 > 0.5) {
                    float2 s2 = (glassRect2.zw - glassRect2.xy) * 0.5;
                    float2 c2 = glassRect2.xy + s2;
                    d_up = smin(d_up, sdRoundedRect(fragCoord + eps.yx - c2, s2, radius), 12.0);
                    d_dn = smin(d_dn, sdRoundedRect(fragCoord - eps.yx - c2, s2, radius), 12.0);
                    d_rt = smin(d_rt, sdRoundedRect(fragCoord + eps.xy - c2, s2, radius), 12.0);
                    d_lf = smin(d_lf, sdRoundedRect(fragCoord - eps.xy - c2, s2, radius), 12.0);
                }

                float2 normal = normalize(float2(d_rt - d_lf, d_up - d_dn) + 0.0001);
                
                // 核心修复：factor 使用更陡峭的曲线，确保中心快速归零
                // 我们将折射高度锁定在边缘的一小圈
                float factor = smoothstep(-refractionHeight, 0.0, d);
                float2 distortion = normal * factor * refractionAmount;
                float2 uv = fragCoord + distortion;
                
                half4 col = half4(0.0);
                for (int i = 0; i < 32; i++) {
                    float r = blurRadius * sqrt(float(i) / 32.0);
                    float theta = float(i) * 2.39996;
                    col += content.eval(uv + float2(cos(theta), sin(theta)) * r);
                }
                col /= 32.0;

                // 材质增益：factor 为 0 的地方（即中心平整区）不应用任何 normal 相关偏移
                if (isDark > 0.5) {
                    col.rgb = mix(col.rgb, half3(0.0), 0.06);
                    col.rgb += (normal.y * 0.04 * factor + 0.02);
                } else {
                    col.rgb = mix(col.rgb, half3(1.0), 0.22);
                    col.rgb += (normal.y * 0.03 * factor + 0.01);
                }
                return col;
            }
        """.trimIndent())

        shader.setFloatUniform("glassRect", rect.left, rect.top, rect.right, rect.bottom)
        if (rect2 != null) {
            shader.setFloatUniform("glassRect2", rect2.left, rect2.top, rect2.right, rect2.bottom)
            shader.setFloatUniform("hasRect2", 1f)
        } else {
            shader.setFloatUniform("glassRect2", 0f, 0f, 0f, 0f)
            shader.setFloatUniform("hasRect2", 0f)
        }
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("refractionAmount", refractionAmount)
        shader.setFloatUniform("refractionHeight", refractionHeight)
        shader.setFloatUniform("blurRadius", blurRadius)
        shader.setFloatUniform("isDark", if (isDark) 1f else 0f)

        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}
