package com.kuangru52.embysic

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import java.util.Random

class HeartLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val random = Random()
    private val colors = intArrayOf(
        0xFFFF4081.toInt(),
        0xFFFF5252.toInt(),
        0xFFFF4081.toInt(),
        0xFFE91E63.toInt(),
        0xFFFF1744.toInt(),
    )

    fun addHeart(x: Float, y: Float) {
        val heart = ImageView(context)
        heart.setImageResource(R.drawable.ic_heart)

        val color = colors[random.nextInt(colors.size)]
        heart.setColorFilter(color)

        val size = (30 + random.nextInt(20)).dpToPx()
        val params = LayoutParams(size, size)
        heart.layoutParams = params

        heart.x = x - (size / 2)
        heart.y = y - (size / 2)

        addView(heart)
        animateHeart(heart)
    }

    fun shatterHeart(x: Float, y: Float) {
        val rows = 4
        val cols = 4
        val shatterColors = intArrayOf(
            0xFFFF3B30.toInt(),
            0xFFFF2D55.toInt(),
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
                    random.nextInt(4).dpToPx().toFloat(), random.nextInt(4).dpToPx().toFloat(),
                )
                piece.background = shape

                val offsetX = (c - (cols / 2f)) * pieceW
                val offsetY = (r - (rows / 2f)) * pieceH
                piece.x = x + offsetX
                piece.y = y + offsetY

                addView(piece)
                animateGlassShatter(piece, x + offsetX, y + offsetY)
            }
        }
    }

    private fun animateGlassShatter(piece: View, startX: Float, startY: Float) {
        val crackDuration = 300L
        val waitDuration = 200L
        val fallDuration = 800 + random.nextInt(400).toLong()

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
                    piece.translationX = (random.nextFloat() - 0.5f) * 8f
                    piece.translationY = (random.nextFloat() - 0.5f) * 8f
                }
                timeInMs < (crackDuration + waitDuration) -> {
                    piece.translationX = 0f
                    piece.translationY = 0f
                }
                else -> {
                    val fallT = (timeInMs - (crackDuration + waitDuration)) / fallDuration.toFloat()
                    val interpolatedT = interpolator.getInterpolation(fallT)

                    piece.x = startX + (driftX * interpolatedT)
                    piece.y = startY + (fallDistance * interpolatedT)

                    piece.rotation += (random.nextFloat() - 0.5f) * 10f
                    piece.alpha = 1f - fallT
                    val scale = 1f + (fallT * 0.5f)
                    piece.scaleX = scale
                    piece.scaleY = scale
                }
            }
        }

        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeView(piece)
                }
            },
        )
        animator.start()
    }

    private fun animateHeart(heart: View) {
        heart.scaleX = 0f
        heart.scaleY = 0f
        heart.rotation = random.nextInt(40) - 20f

        val appearScaleX = ObjectAnimator.ofFloat(heart, SCALE_X, 0f, 1.2f, 1.0f)
        val appearScaleY = ObjectAnimator.ofFloat(heart, SCALE_Y, 0f, 1.2f, 1.0f)
        val appearAlpha = ObjectAnimator.ofFloat(heart, ALPHA, 0f, 1f)

        val appearSet = AnimatorSet().apply {
            playTogether(appearScaleX, appearScaleY, appearAlpha)
            duration = 300
            interpolator = DecelerateInterpolator()
        }

        val flyY = ObjectAnimator.ofFloat(heart, TRANSLATION_Y, heart.translationY, heart.translationY - 300 - random.nextInt(200))
        val flyX = ObjectAnimator.ofFloat(heart, TRANSLATION_X, heart.translationX, heart.translationX + (random.nextInt(200) - 100))
        val fadeOut = ObjectAnimator.ofFloat(heart, ALPHA, 1f, 0f)
        val scaleOutX = ObjectAnimator.ofFloat(heart, SCALE_X, 1.0f, 1.5f)
        val scaleOutY = ObjectAnimator.ofFloat(heart, SCALE_Y, 1.0f, 1.5f)

        val flySet = AnimatorSet().apply {
            playTogether(flyY, flyX, fadeOut, scaleOutX, scaleOutY)
            duration = 800 + random.nextInt(400).toLong()
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 200
        }

        AnimatorSet().apply {
            playSequentially(appearSet, flySet)
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        removeView(heart)
                    }
                },
            )
            start()
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
