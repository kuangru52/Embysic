package com.kuangru52.embysic

import android.animation.*
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import java.util.*

class HeartLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val random = Random()
    private val colors = intArrayOf(
        0xFFFF4081.toInt(), // Pink
        0xFFFF5252.toInt(), // Red
        0xFFFF4081.toInt(),
        0xFFE91E63.toInt(),
        0xFFFF1744.toInt()
    )

    fun addHeart(x: Float, y: Float) {
        val heart = ImageView(context)
        heart.setImageResource(R.drawable.ic_heart)
        
        // Random color
        val color = colors[random.nextInt(colors.size)]
        heart.setColorFilter(color)
        
        val size = (30 + random.nextInt(20)).dpToPx()
        val params = LayoutParams(size, size)
        heart.layoutParams = params
        
        heart.x = x - size / 2
        heart.y = y - size / 2
        
        addView(heart)
        
        animateHeart(heart)
    }

    fun shatterHeart(x: Float, y: Float) {
        val rows = 4
        val cols = 4
        val shatterColors = intArrayOf(
            0xFFFF3B30.toInt(),
            0xFFFF2D55.toInt()
        )
        
        val totalWidth = 32.dpToPx()
        val totalHeight = 32.dpToPx()
        val pieceW = totalWidth / cols
        val pieceH = totalHeight / rows

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val piece = View(context)
                val params = LayoutParams(pieceW, pieceH)
                piece.layoutParams = params
                
                val shape = android.graphics.drawable.GradientDrawable()
                shape.setColor(shatterColors[random.nextInt(shatterColors.size)])
                shape.cornerRadii = floatArrayOf(
                    random.nextInt(4).dpToPx().toFloat(), random.nextInt(4).dpToPx().toFloat(),
                    random.nextInt(4).dpToPx().toFloat(), random.nextInt(4).dpToPx().toFloat(),
                    random.nextInt(4).dpToPx().toFloat(), random.nextInt(4).dpToPx().toFloat(),
                    random.nextInt(4).dpToPx().toFloat(), random.nextInt(4).dpToPx().toFloat()
                )
                piece.background = shape
                
                val offsetX = (c - cols / 2f) * pieceW
                val offsetY = (r - rows / 2f) * pieceH
                piece.x = x + offsetX
                piece.y = y + offsetY
                
                addView(piece)
                animateGlassShatter(piece, x + offsetX, y + offsetY)
            }
        }
    }

    private fun animateGlassShatter(piece: View, startX: Float, startY: Float) {
        // Match animateHeart's timing and feel
        val crackDuration = 300L
        val waitDuration = 200L // Match flySet's startDelay
        val fallDuration = 800 + random.nextInt(400).toLong()
        
        // Match animateHeart's fly distance (downwards)
        val fallDistance = 300f + random.nextInt(200)
        val driftX = (random.nextFloat() - 0.5f) * 80f
        
        val interpolator = AccelerateDecelerateInterpolator()
        
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = crackDuration + waitDuration + fallDuration
        
        animator.addUpdateListener { animation ->
            val t = animation.animatedFraction
            val timeInMs = t * animator.duration
            
            when {
                timeInMs < crackDuration -> {
                    // Jitter phase (Simulates appear phase but as a break)
                    piece.translationX = (random.nextFloat() - 0.5f) * 8f
                    piece.translationY = (random.nextFloat() - 0.5f) * 8f
                }
                timeInMs < crackDuration + waitDuration -> {
                    // Pause phase (Matches flySet's startDelay)
                    piece.translationX = 0f
                    piece.translationY = 0f
                }
                else -> {
                    // Fall phase (Matches flySet's motion)
                    val fallT = (timeInMs - crackDuration - waitDuration) / fallDuration.toFloat()
                    val interpolatedT = interpolator.getInterpolation(fallT)
                    
                    piece.x = startX + driftX * interpolatedT
                    piece.y = startY + fallDistance * interpolatedT
                    
                    piece.rotation += (random.nextFloat() - 0.5f) * 10f
                    piece.alpha = 1f - fallT
                    // Match animateHeart's scaleOut (which goes 1.0 to 1.5)
                    val scale = 1f + (fallT * 0.5f)
                    piece.scaleX = scale
                    piece.scaleY = scale
                }
            }
        }
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                removeView(piece)
            }
        })
        animator.start()
    }

    private fun animateHeart(heart: View) {
        // 1. Appears with scale and rotation
        heart.scaleX = 0f
        heart.scaleY = 0f
        heart.rotation = random.nextInt(40) - 20f
        
        val appearScaleX = ObjectAnimator.ofFloat(heart, View.SCALE_X, 0f, 1.2f, 1.0f)
        val appearScaleY = ObjectAnimator.ofFloat(heart, View.SCALE_Y, 0f, 1.2f, 1.0f)
        val appearAlpha = ObjectAnimator.ofFloat(heart, View.ALPHA, 0f, 1f)
        
        val appearSet = AnimatorSet().apply {
            playTogether(appearScaleX, appearScaleY, appearAlpha)
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        
        // 2. Floats up and fades out
        val flyY = ObjectAnimator.ofFloat(heart, View.TRANSLATION_Y, heart.translationY, heart.translationY - 300 - random.nextInt(200))
        val flyX = ObjectAnimator.ofFloat(heart, View.TRANSLATION_X, heart.translationX, heart.translationX + (random.nextInt(200) - 100))
        val fadeOut = ObjectAnimator.ofFloat(heart, View.ALPHA, 1f, 0f)
        val scaleOutX = ObjectAnimator.ofFloat(heart, View.SCALE_X, 1.0f, 1.5f)
        val scaleOutY = ObjectAnimator.ofFloat(heart, View.SCALE_Y, 1.0f, 1.5f)
        
        val flySet = AnimatorSet().apply {
            playTogether(flyY, flyX, fadeOut, scaleOutX, scaleOutY)
            duration = 800 + random.nextInt(400).toLong()
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 200
        }
        
        AnimatorSet().apply {
            playSequentially(appearSet, flySet)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeView(heart)
                }
            })
            start()
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
