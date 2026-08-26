package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout

/**
 * 🌈 极光流光边框环绕脉冲布局 (BorderBeamFrameLayout)
 *
 * 灵感来源：21st.dev / Landing.love 标志性 Border Beam 动效
 * 核心原理：
 * 1. 在圆角矩形边界上应用动态自旋的极光扫描渐变 (SweepGradient)；
 * 2. 通过 ValueAnimator 驱动 Shader Matrix 匀速 360° 旋转；
 * 3. 硬件加速 Canvas 裁剪只保留边框光弧，赋予焦点卡片极高的先锋科技感。
 */
class BorderBeamFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var beamColor: Int = Color.parseColor("#4DEEEA")
        set(value) {
            field = value
            updateShader()
            invalidate()
        }

    var beamSecondaryColor: Int = Color.parseColor("#FFE700")
        set(value) {
            field = value
            updateShader()
            invalidate()
        }

    var beamWidth: Float = 6f
        set(value) {
            field = value
            borderPaint.strokeWidth = value
            invalidate()
        }

    var cornerRadius: Float = 48f
        set(value) {
            field = value
            invalidate()
        }

    var beamDuration: Long = 3200L
        set(value) {
            field = value
            animator?.duration = value
        }

    var isBeamEnabled: Boolean = true
        set(value) {
            field = value
            if (value) startAnimation() else stopAnimation()
            invalidate()
        }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = beamWidth
    }

    private val baseBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(40, 255, 255, 255)
    }

    private val borderPath = Path()
    private val borderRect = RectF()
    private val shaderMatrix = Matrix()
    private var rotateDegree = 0f
    private var animator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        borderPaint.strokeWidth = beamWidth
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val halfStroke = beamWidth / 2f
        borderRect.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
        borderPath.reset()
        borderPath.addRoundRect(borderRect, cornerRadius, cornerRadius, Path.Direction.CW)
        updateShader()
    }

    private fun updateShader() {
        if (width <= 0 || height <= 0) return
        val cx = width / 2f
        val cy = height / 2f

        // 构建流光扫描渐变：仅在 0°~60° 区间产生强烈高光光束，其余全透明
        val colors = intArrayOf(
            Color.TRANSPARENT,
            beamColor,
            beamSecondaryColor,
            Color.TRANSPARENT,
            Color.TRANSPARENT
        )
        val positions = floatArrayOf(0.0f, 0.10f, 0.22f, 0.35f, 1.0f)

        val sweepShader = SweepGradient(cx, cy, colors, positions)
        borderPaint.shader = sweepShader
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isBeamEnabled) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = beamDuration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotateDegree = it.animatedValue as Float
                invalidate()
            }
        }
        animator?.start()
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        // 1. 绘制基底微弱边框
        canvas.drawPath(borderPath, baseBorderPaint)

        // 2. 绘制自旋流光光束
        if (isBeamEnabled && borderPaint.shader != null) {
            val cx = width / 2f
            val cy = height / 2f
            shaderMatrix.setRotate(rotateDegree, cx, cy)
            borderPaint.shader.setLocalMatrix(shaderMatrix)
            canvas.drawPath(borderPath, borderPaint)
        }
    }
}
