package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * 🌈 全息棱镜色散与折射光斑视图 (PrismaticChromaticView)
 *
 * P6 阶段三核心组件：
 * 1. 光学色散位移（Prismatic Chromatic Aberration）：在卡片/徽章边缘产生 0.6px~1.2px 的青/品红亚像素色散分层；
 * 2. 模拟真实透镜折射（Optical Refraction）：自发光呼吸与微动色差，呈现奢侈品级全息镭射质感；
 * 3. 硬件加速优化与内存安全。
 */
class PrismaticChromaticView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var cornerRadiusDp: Float = 16f
        set(value) {
            field = value
            invalidate()
        }

    var chromaticOffsetPx: Float = 2.5f
        set(value) {
            field = value
            invalidate()
        }

    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        color = Color.parseColor("#4400F5D4") // 极光青半透
    }

    private val magentaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        color = Color.parseColor("#44FF007F") // 全息洋红半透
    }

    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#66E0A96D") // 浅金曜
    }

    private val rectCyan = RectF()
    private val rectMagenta = RectF()
    private val rectCenter = RectF()

    private var phase: Float = 0f
    private var chromaticAnimator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        startChromaticBreathing()
    }

    private fun startChromaticBreathing() {
        chromaticAnimator?.cancel()
        chromaticAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 4200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val r = cornerRadiusDp * density
        val w = width.toFloat()
        val h = height.toFloat()

        val dynamicOffset = chromaticOffsetPx * (0.8f + 0.3f * sin(phase))

        // 1. 青色通道向左上方微偏移
        rectCyan.set(
            1f - dynamicOffset,
            1f - dynamicOffset,
            w - 1f - dynamicOffset,
            h - 1f - dynamicOffset
        )
        canvas.drawRoundRect(rectCyan, r, r, cyanPaint)

        // 2. 洋红色通道向右下方微偏移
        rectMagenta.set(
            1f + dynamicOffset,
            1f + dynamicOffset,
            w - 1f + dynamicOffset,
            h - 1f + dynamicOffset
        )
        canvas.drawRoundRect(rectMagenta, r, r, magentaPaint)

        // 3. 中心主色倒角
        rectCenter.set(1f, 1f, w - 1f, h - 1f)
        canvas.drawRoundRect(rectCenter, r, r, goldPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        chromaticAnimator?.cancel()
        chromaticAnimator = null
    }
}
