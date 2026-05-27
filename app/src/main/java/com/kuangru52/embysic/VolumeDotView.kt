package com.kuangru52.embysic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class VolumeDotView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var touchX = -1f
    private var isPressed = false
    private var screenWidth = 0f
    
    private var bloomProgress = 0f
    private var bloomAnimator: ValueAnimator? = null

    private val dotRadiusDp = 1.6f
    private val spacingDp = 11f
    private val shadowColor = Color.parseColor("#40000000")

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w.toFloat()
    }

    fun setTouchPosition(x: Float, y: Float, pressed: Boolean) {
        if (pressed && !isPressed) {
            startBloomAnimation()
        }
        
        touchX = x
        isPressed = pressed
        
        if (!pressed) {
            bloomAnimator?.cancel()
            bloomProgress = 0f
        }
        invalidate()
    }

    private fun startBloomAnimation() {
        bloomAnimator?.cancel()
        bloomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                bloomProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (screenWidth <= 0 || (!isPressed && bloomProgress <= 0f)) return

        val density = resources.displayMetrics.density
        val r = dotRadiusDp * density
        val s = spacingDp * density
        val time = System.currentTimeMillis()

        val gridCenterX = screenWidth / 2f
        val colCount = (screenWidth / s).toInt()
        val halfCol = colCount / 2

        for (row in -2..2) {
            for (col in -halfCol..halfCol) {
                val absRow = abs(row)
                val absCol = abs(col)
                
                if (absRow == 2 && absCol > halfCol - 2) continue
                if (absRow == 1 && absCol > halfCol - 1) continue
                
                val baseDotX = gridCenterX + col * s
                val baseDotY = height / 2f + row * s
                
                var alphaFactor = 0.15f
                var focusScale = 1.0f
                var currentDotProgress = 1.0f
                var magneticOffsetX = 0f
                var magneticOffsetY = 0f

                if (isPressed) {
                    val dx = baseDotX - touchX
                    val dy = baseDotY - (height / 2f) // 简化 Y 轴响应
                    val distToFinger = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    val distInUnits = distToFinger / s
                    
                    // 1. 磁吸效果：点向手指微调
                    if (distToFinger < s * 6) {
                        val force = (1f - (distToFinger / (s * 6))).coerceIn(0f, 1f)
                        magneticOffsetX = -dx * 0.15f * force
                        magneticOffsetY = -dy * 0.15f * force
                    }

                    // 2. 动态波浪 & 高亮
                    alphaFactor = 0.72.pow(distInUnits.toDouble()).toFloat().coerceIn(0.12f, 1.0f)
                    
                    // 增加呼吸感的高亮波纹
                    val wave = sin(time / 200.0 - distInUnits * 0.8).toFloat() * 0.1f
                    focusScale = (1.3f - (distInUnits / 6f) + wave).coerceIn(1.0f, 1.5f)
                    
                    val gridDistCenter = sqrt((row * row + col * col).toDouble()).toFloat()
                    val dotDelay = gridDistCenter * 0.08f
                    currentDotProgress = (bloomProgress - dotDelay).coerceIn(0f, 1f)
                }

                if (currentDotProgress <= 0f) continue

                // 3. 基础漂浮动效 (更加丝滑的低频随机感)
                val angle = (time / 1500.0) + (col * 0.5) + (row * 0.8)
                val floatX = sin(angle).toFloat() * 0.8f * density
                val floatY = cos(angle * 0.8).toFloat() * 0.8f * density
                
                dotPaint.alpha = (255 * (alphaFactor + (if(isPressed) 0.05f else 0f)) * currentDotProgress).toInt().coerceIn(0, 255)
                val dynamicRadius = r * focusScale * (0.85f + 0.35f * currentDotProgress)
                
                if (isPressed) {
                    dotPaint.setShadowLayer(dynamicRadius * 1.5f, 0f, 0f, shadowColor)
                } else {
                    dotPaint.clearShadowLayer()
                }
                
                canvas.drawCircle(
                    baseDotX + floatX + magneticOffsetX, 
                    baseDotY + floatY + magneticOffsetY, 
                    dynamicRadius, 
                    dotPaint
                )
            }
        }
        
        if (isPressed || bloomProgress > 0f) {
            postInvalidateOnAnimation()
        }
    }
}
