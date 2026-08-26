package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * 🔮 4 层 2.5D 空间立体标本盒 (Spatial Diorama Box Container)
 * 对标 Apple visionOS 空间 UI 与 Awwwards 年度 3D 展馆：
 * - 将子 View 按层级（Layer 0 ~ Layer 3）赋予不同的景深系数（Depth Factors）；
 * - 结合触控拖拽与陀螺仪物理姿态，实现微型标本盒（Diorama Box）多层裸眼 3D 深度视差悬浮；
 * - 松手时具备物理弹簧阻尼回弹（Spring Physics）。
 */
class DioramaBoxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val camera3D = Camera()
    private val matrix3D = Matrix()

    var maxTiltAngle: Float = 14f
    var currentTiltX: Float = 0f
    var currentTiltY: Float = 0f

    private var resetAnimator: ValueAnimator? = null

    init {
        clipChildren = false
        clipToPadding = false
    }

    /**
     * 外部陀螺仪数据注入
     */
    fun setGyroscopeTilt(pitch: Float, roll: Float) {
        if (resetAnimator?.isRunning == true) return
        currentTiltX = (pitch * maxTiltAngle).coerceIn(-maxTiltAngle, maxTiltAngle)
        currentTiltY = (roll * maxTiltAngle).coerceIn(-maxTiltAngle, maxTiltAngle)
        applyLayerParallax()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                resetAnimator?.cancel()
                val normX = ((event.x / w) - 0.5f) * 2f // -1f ~ 1f
                val normY = ((event.y / h) - 0.5f) * 2f // -1f ~ 1f

                currentTiltY = normX * maxTiltAngle
                currentTiltX = -normY * maxTiltAngle

                applyLayerParallax()
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                animateReset()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun applyLayerParallax() {
        val childCount = childCount
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            // 根据子 View 索引赋予不同的深度位移因子
            val depthFactor = when (i) {
                0 -> -0.4f // Layer 0: 背景底层（反向下沉）
                1 -> 0.3f  // Layer 1: 正文与元数据层
                2 -> 0.85f // Layer 2: 3D 浮雕封面主体
                else -> 1.4f // Layer 3+: 全息高光与印章（极高悬浮）
            }

            val maxTranslation = 24f * resources.displayMetrics.density
            val tx = (currentTiltY / maxTiltAngle) * maxTranslation * depthFactor
            val ty = (-currentTiltX / maxTiltAngle) * maxTranslation * depthFactor

            child.translationX = tx
            child.translationY = ty
            child.rotationX = currentTiltX * (0.6f + depthFactor * 0.4f)
            child.rotationY = currentTiltY * (0.6f + depthFactor * 0.4f)
        }
    }

    private fun animateReset() {
        resetAnimator?.cancel()
        val startX = currentTiltX
        val startY = currentTiltY

        resetAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 500L
            interpolator = OvershootInterpolator(1.3f)
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                currentTiltX = startX * f
                currentTiltY = startY * f
                applyLayerParallax()
                invalidate()
            }
            start()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        canvas.save()
        camera3D.save()
        camera3D.rotateX(currentTiltX)
        camera3D.rotateY(currentTiltY)
        camera3D.getMatrix(matrix3D)
        camera3D.restore()

        matrix3D.preTranslate(-cx, -cy)
        matrix3D.postTranslate(cx, cy)

        canvas.concat(matrix3D)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
