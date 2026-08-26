package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * 🔦 3D 磁吸聚光灯微倾角卡片布局 (SpotlightTiltCardView)
 *
 * 灵感来源：21st.dev / Linear / Stripe 3D Spotlight Tilt Card
 * 核心原理：
 * 1. 拦截手指在卡片上的微滑动，计算相对中心点的偏移量 (dx, dy)；
 * 2. 施加真实的 3D 空间倾角 (rotationX, rotationY)；
 * 3. 在前景层以手指触控点为中心实时渲染柔和的径向聚光灯光斑 (Radial Spotlight)；
 * 4. 手指抬起时通过 OvershootInterpolator 弹簧阻尼丝滑复位。
 */
class SpotlightTiltCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var maxTiltDegree: Float = 6.0f
    var spotlightRadius: Float = 360f
    var spotlightColor: Int = Color.argb(40, 77, 238, 234)

    private var touchX = -1f
    private var touchY = -1f
    private var isTouching = false
    private var spotlightAlpha = 0f

    private val spotlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var resetAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        // 开启 3D 透视深度
        cameraDistance = 8000f * resources.displayMetrics.density
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false // 让子视图正常处理点击，同时父容器感知 Touch
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
                resetAnimator?.cancel()
                updateTouchPosition(ev.x, ev.y)
                animateSpotlightAlpha(1.0f)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTouching) {
                    updateTouchPosition(ev.x, ev.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                animateReset()
                animateSpotlightAlpha(0.0f)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun updateTouchPosition(x: Float, y: Float) {
        touchX = x
        touchY = y

        val w = width.toFloat()
        val h = height.toFloat()
        if (w > 0 && h > 0) {
            val relX = (x / w - 0.5f) * 2f
            val relY = (y / h - 0.5f) * 2f

            rotationY = relX * maxTiltDegree
            rotationX = -relY * maxTiltDegree
        }
        invalidate()
    }

    private fun animateReset() {
        resetAnimator?.cancel()
        val startRotX = rotationX
        val startRotY = rotationY

        resetAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 320L
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener {
                val factor = it.animatedValue as Float
                rotationX = startRotX * factor
                rotationY = startRotY * factor
            }
        }
        resetAnimator?.start()
    }

    private fun animateSpotlightAlpha(target: Float) {
        ValueAnimator.ofFloat(spotlightAlpha, target).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                spotlightAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        // 在内容上层叠加绘制柔和聚光灯光晕
        if (spotlightAlpha > 0.01f && touchX >= 0 && touchY >= 0) {
            val alphaColor = Color.argb(
                (Color.alpha(spotlightColor) * spotlightAlpha).toInt(),
                Color.red(spotlightColor),
                Color.green(spotlightColor),
                Color.blue(spotlightColor)
            )

            spotlightPaint.shader = RadialGradient(
                touchX,
                touchY,
                spotlightRadius,
                alphaColor,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), spotlightPaint)
        }
    }
}
