package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * 🔍 真实物理折射率光学透镜覆盖层 (Glass Refraction Optical Overlay)
 * 对标 Apple visionOS 磨砂水晶玻璃与 Awwwards 顶奢数字展品：
 * - 在卡片四角与四周渲染光学透镜弯曲高光条与双重反射带；
 * - 结合倾角动态移动折射光斑，呈现极富厚实质感的水晶玻璃材质。
 */
class GlassRefractionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val cornerRadius = 18f * resources.displayMetrics.density

    private val refractionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cardPath = Path()
    private val rectF = RectF()

    var tiltFactorX: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
            invalidate()
        }

    var tiltFactorY: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
            invalidate()
        }

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rectF.set(2f, 2f, w - 2f, h - 2f)
        cardPath.reset()
        cardPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val lightX = w * (0.35f + tiltFactorY * 0.3f)
        val lightY = h * (0.30f - tiltFactorX * 0.3f)

        // 1. 绘制物理厚度折射边缘
        refractionPaint.shader = LinearGradient(
            lightX - w * 0.4f, lightY - h * 0.4f,
            lightX + w * 0.4f, lightY + h * 0.4f,
            intArrayOf(
                Color.argb(180, 255, 255, 255),
                Color.argb(40, 77, 238, 234),
                Color.argb(10, 255, 255, 255),
                Color.argb(90, 255, 255, 255),
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1.0f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(cardPath, refractionPaint)

        // 2. 绘制透镜内部微折射光晕
        glowPaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(
                Color.argb(25, 255, 255, 255),
                Color.TRANSPARENT,
                Color.argb(15, 77, 238, 234),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(cardPath, glowPaint)
    }
}
