package com.kuangru52.embysic

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import androidx.fragment.app.Fragment

class SwipeBackLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val dragHelper: ViewDragHelper
    private var swipeBackListener: (() -> Unit)? = null
    private var contentView: View? = null
    
    private var previousView: View? = null
    private val shadowWidth = 60f
    private var dragOffset = 0f
    private val ignoreViews = mutableListOf<View>()
    private var interceptView: View? = null

    fun addIgnoreView(view: View) {
        if (!ignoreViews.contains(view)) {
            ignoreViews.add(view)
        }
    }

    /**
     * 设置一个特定的 View，只有触摸在该 View 范围内时才允许触发“非边缘”滑动返回
     */
    fun setSwipeInterceptView(view: View) {
        this.interceptView = view
    }

    init {
        dragHelper = ViewDragHelper.create(this, 1.0f, object : ViewDragHelper.Callback() {
            override fun tryCaptureView(child: View, pointerId: Int): Boolean {
                // 只有设置了监听器（非根页面）才允许捕捉视图进行滑动
                return child == contentView && swipeBackListener != null
            }

            override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
                return left.coerceIn(0, width)
            }

            override fun getViewHorizontalDragRange(child: View): Int {
                return width
            }

            override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
                dragOffset = left.toFloat() / width
                // 核心修复：底层页面位置完全不动，移除 translationX
                invalidate()
            }

            override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
                val threshold = width / 3
                if (xvel > 1000 || (releasedChild.left > threshold && xvel >= 0)) {
                    // 1. 禁用 Fragment 退出动画，防止产生双重动画
                    val activity = context as? AppCompatActivity
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.setCustomAnimations(0, 0, 0, 0)
                        ?.commitAllowingStateLoss()

                    dragHelper.settleCapturedViewAt(width, 0)
                    invalidate()
                    
                    // 2. 动画完成后直接移除
                    postDelayed({
                        swipeBackListener?.invoke()
                    }, 200)
                } else {
                    dragHelper.settleCapturedViewAt(0, 0)
                    invalidate()
                }
            }
        })
    }

    fun setOnSwipeBackListener(listener: () -> Unit) {
        swipeBackListener = listener
    }
    
    fun setPreviousView(view: View?) {
        this.previousView = view
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)
        if (contentView == null) {
            contentView = child
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (swipeBackListener == null) return false

        if (ev.action == MotionEvent.ACTION_DOWN) {
            val x = ev.x
            val edgeSize = 50 // 边缘 50 像素内强制允许滑动

            // 1. 如果在忽略视图内（如进度条），绝对不拦截
            for (view in ignoreViews) {
                if (isTouchInView(ev, view)) return false
            }

            // 2. 如果点击在屏幕右侧 30% 区域，不拦截
            if (x > width * 0.7f) return false

            // 3. 核心逻辑：如果是“屏幕中间”的滑动（非边缘）
            if (x > edgeSize) {
                // 如果设置了特定的拦截区域（如唱片区），则判断点是否在该区域内
                interceptView?.let {
                    if (!isTouchInView(ev, it)) {
                        return false // 不在唱片/歌词区，不拦截（交给底部的进度条等）
                    }
                }
            }
        }
        return try {
            dragHelper.shouldInterceptTouchEvent(ev)
        } catch (e: Exception) {
            false
        }
    }

    private fun isTouchInView(ev: MotionEvent, view: View): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val rect = android.graphics.Rect()
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        rect.set(location[0], location[1], location[0] + view.width, location[1] + view.height)
        return rect.contains(ev.rawX.toInt(), ev.rawY.toInt())
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        dragHelper.processTouchEvent(ev)
        return true
    }
    
    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 1. 如果正在滑动且有上一个视图，先绘制底层视图
        if (dragOffset > 0 && previousView != null) {
            canvas.save()
            // 让底层视图稍微有一点位移（Parallax 效果），或者保持不动
            // 这里我们保持不动，像一叠纸一样
            previousView?.draw(canvas)
            
            // 绘制一层遮罩，让底层视图看起来在下面
            val alpha = (1.0f - dragOffset) * 0.4f
            canvas.drawColor(android.graphics.Color.argb((alpha * 255).toInt(), 0, 0, 0))
            canvas.restore()
        }

        super.dispatchDraw(canvas)

        // 2. 绘制当前视图（上层卡片）左侧的阴影
        contentView?.let {
            val left = it.left.toFloat()
            if (left > 0) {
                val paint = Paint().apply {
                    shader = LinearGradient(
                        left - shadowWidth, 0f, left, 0f,
                        intArrayOf(0x00000000, 0x55000000), // 加深阴影增强重叠感
                        null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(left - shadowWidth, 0f, left, height.toFloat(), paint)
            }
        }
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }
}
