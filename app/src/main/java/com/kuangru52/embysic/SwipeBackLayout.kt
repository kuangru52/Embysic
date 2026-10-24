@file:Suppress("RedundantQualifierName")

package com.kuangru52.embysic

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import androidx.customview.widget.ViewDragHelper

@androidx.media3.common.util.UnstableApi
class SwipeBackLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val dragHelper: ViewDragHelper
    private var swipeBackListener: (() -> Unit)? = null
    private var contentView: View? = null
    
    private var previousView: View? = null
    private val shadowWidth = 60f
    private var dragOffset = 0f
    private val ignoreViews = mutableListOf<View>()

    private var wasPreviousViewHidden = false

    fun addIgnoreView(view: View) {
        ignoreViews.add(view)
    }

    fun setPreviousView(view: View?) {
        this.previousView = view
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (contentView == null) {
            contentView = child
        }
    }

    init {
        dragHelper = ViewDragHelper.create(
            this,
            1.0f,
            object : ViewDragHelper.Callback() {
                override fun tryCaptureView(child: View, pointerId: Int): Boolean {
                    if (contentView == null && childCount > 0) {
                        contentView = getChildAt(0)
                    }
                    return (child == contentView || contentView == null) && !isTouchOnIgnoreViews()
                }

                override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
                    return left.coerceIn(0, width)
                }

                override fun getViewHorizontalDragRange(child: View): Int {
                    return width
                }

                override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
                    dragOffset = left.toFloat() / width.coerceAtLeast(1)
                    
                    if (dragOffset > 0f) {
                        if (previousView != null && previousView?.visibility != VISIBLE) {
                            wasPreviousViewHidden = true
                            previousView?.visibility = VISIBLE
                        }
                    }
                    
                    invalidate()
                }

                override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
                    val threshold = width / 3
                    if ((xvel > 1000f) || ((releasedChild.left > threshold) && (xvel >= 0f))) {
                        (context as? HomeActivity)?.isSwipingBack = true
                        dragHelper.settleCapturedViewAt(width, 0)
                    } else {
                        dragHelper.settleCapturedViewAt(0, 0)
                    }
                    invalidate()
                }
            },
        )
        dragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_LEFT)
    }

    private fun isTouchOnIgnoreViews(): Boolean {
        val location = IntArray(2)
        val evX = lastTouchX
        val evY = lastTouchY
        for (view in ignoreViews) {
            view.getLocationOnScreen(location)
            val rect = android.graphics.Rect(
                location[0],
                location[1],
                location[0] + view.width,
                location[1] + view.height,
            )
            if (rect.contains(evX.toInt(), evY.toInt())) {
                return true
            }
        }
        return false
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (swipeBackListener == null) return false
        if (ev.action == MotionEvent.ACTION_DOWN) {
            lastTouchX = ev.x
            lastTouchY = ev.y
        }
        return try {
            dragHelper.shouldInterceptTouchEvent(ev)
        } catch (_: Exception) {
            false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        if (swipeBackListener == null) return super.onTouchEvent(ev)
        return try {
            dragHelper.processTouchEvent(ev)
            true
        } catch (_: Exception) {
            super.onTouchEvent(ev)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            invalidate()
        } else {
            if (dragOffset >= 1.0f) {
                val listener = swipeBackListener
                swipeBackListener = null
                listener?.invoke()
            } else if (dragOffset <= 0f) {
                if (wasPreviousViewHidden && previousView != null) {
                    previousView?.visibility = GONE
                    wasPreviousViewHidden = false
                }
                (context as? HomeActivity)?.isSwipingBack = false
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (dragOffset > 0f && previousView != null) {
            canvas.withSave {
                previousView?.let { v ->
                    val scale = 0.95f + (0.05f * dragOffset)
                    canvas.withScale(scale, scale, (width / 2).toFloat(), (height / 2).toFloat()) {
                        canvas.translate(-width * 0.15f * (1f - dragOffset), 0f)
                        v.draw(canvas)
                    }

                    val paint = Paint().apply {
                        shader = LinearGradient(
                            0f, 0f, shadowWidth, 0f,
                            intArrayOf(
                                android.graphics.Color.argb((0.20f * (1f - dragOffset) * 255).toInt(), 0, 0, 0),
                                android.graphics.Color.TRANSPARENT,
                            ),
                            null,
                            Shader.TileMode.CLAMP,
                        )
                    }
                    canvas.drawRect(0f, 0f, shadowWidth, height.toFloat(), paint)
                }
            }
        }
        super.dispatchDraw(canvas)
    }

    fun setOnSwipeBackListener(listener: () -> Unit) {
        swipeBackListener = listener
    }
}
