package com.kuangru52.embysic

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Shader as AndroidShader

/**
 * 局部液态玻璃特效 (Selective Liquid Glass)
 * 核心逻辑：在 Shader 内部实现局部模糊，确保屏幕上方保持清晰。
 */
private const val LIQUID_GLASS_SHADER = """
    uniform shader content;
    uniform float2 size;
    uniform float refraction;
    uniform float bottomOffset;
    uniform float time;
    uniform float aberration;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;
        
        // 1. 计算影响强度 (Mask)
        // 只有在距离底部 bottomOffset 范围内才产生效果
        float distToBottom = (size.y - fragCoord.y) / bottomOffset;
        float strength = smoothstep(1.2, 0.0, distToBottom);
        
        // 【关键点】如果不在底栏区域，直接返回原始清晰图像
        if (strength <= 0.0) {
            return content.eval(fragCoord);
        }

        // 2. 计算折射坐标 (Distortion)
        float2 center = float2(size.x * 0.5, size.y);
        float2 relCoord = (fragCoord - center) / size.x;
        float d = length(relCoord);
        float distortion = pow(d, 1.5) * refraction * 45.0 * strength;
        float2 dir = normalize(relCoord + 0.0001);
        float2 samplePos = fragCoord - dir * distortion;

        // 3. 局部模糊与色散 (Selective Blur & Aberration)
        // 使用更密集的采样点和更大的半径来获得更平滑的效果
        float blurRadius = strength * 45.0; 
        float abAmount = aberration * strength;
        
        half4 acc = half4(0.0);
        
        // 增加采样点到 13 个，使用泊松圆盘分布或环形分布
        float2 offsets[13];
        offsets[0] = float2(0.0, 0.0);
        offsets[1] = float2(1.0, 0.0);
        offsets[2] = float2(-1.0, 0.0);
        offsets[3] = float2(0.0, 1.0);
        offsets[4] = float2(0.0, -1.0);
        offsets[5] = float2(0.7, 0.7);
        offsets[6] = float2(-0.7, 0.7);
        offsets[7] = float2(0.7, -0.7);
        offsets[8] = float2(-0.7, -0.7);
        // 增加外环采样
        offsets[9] = float2(0.0, 1.5);
        offsets[10] = float2(0.0, -1.5);
        offsets[11] = float2(1.5, 0.0);
        offsets[12] = float2(-1.5, 0.0);

        for(int i = 0; i < 13; i++) {
            float2 off = offsets[i] * (blurRadius / 1.5); // 归一化步长
            float2 basePos = clamp(samplePos + off, float2(1.0), size - 1.0);
            
            acc.r += content.eval(clamp(basePos + dir * abAmount, float2(1.0), size - 1.0)).r;
            acc.g += content.eval(basePos).g;
            acc.b += content.eval(clamp(basePos - dir * abAmount, float2(1.0), size - 1.0)).b;
            acc.a += 1.0;
        }
        half4 color = acc / 13.0;

        // 4. 菲涅尔提亮与噪点
        float fresnel = pow(clamp(1.0 - d * 2.5, 0.0, 1.0), 3.0) * 0.1 * strength;
        color.rgb += fresnel;
        color.rgb *= (1.0 + 0.05 * strength);
        
        return color;
    }
"""

object LiquidGlassFactory {
    fun createLiquidRenderEffect(
        width: Float,
        height: Float,
        refraction: Float,
        aberration: Float,
        bottomOffset: Float
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        
        val shader = RuntimeShader(LIQUID_GLASS_SHADER)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("refraction", refraction)
        shader.setFloatUniform("bottomOffset", bottomOffset)
        shader.setFloatUniform("aberration", aberration)
        shader.setFloatUniform("time", (System.currentTimeMillis() % 10000).toFloat())

        // 【关键修复】不再使用外部 BlurEffect 链接，直接返回 ShaderEffect
        // 模糊逻辑已经移到 Shader 内部的 strength 控制中了
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }
}

fun Modifier.liquidGlass(
    refraction: Float = 0.5f,
    aberration: Float = 3.0f,
    bottomOffset: Dp = 180.dp
) = this.composed {
    val density = LocalDensity.current
    val bottomOffsetPx = with(density) { bottomOffset.toPx() }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(LIQUID_GLASS_SHADER) }
        this.graphicsLayer {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("refraction", refraction)
            shader.setFloatUniform("bottomOffset", bottomOffsetPx)
            shader.setFloatUniform("aberration", aberration)
            shader.setFloatUniform("time", (System.currentTimeMillis() % 10000).toFloat())
            
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }
    } else {
        this.background(Color.Black.copy(alpha = 0.3f))
    }
}

fun Modifier.glassContainer(
    isDark: Boolean,
    cornerRadius: Dp = 32.dp
) = this.then(
    Modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(
            if (isDark) Color(0xFF1A1A1A).copy(alpha = 0.3f) 
            else Color.White.copy(alpha = 0.4f)
        )
        .border(
            width = 0.6.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isDark) 0.2f else 0.6f),
                    Color.White.copy(alpha = 0.05f)
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
)
