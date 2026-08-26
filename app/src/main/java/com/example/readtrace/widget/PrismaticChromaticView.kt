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
import android.widget.FrameLayout

/**
 * 💎 1px 极细内倒角高光与全息微棱镜色散 (Prismatic Chromatic Inset & Aberration)
 * 对标 Apple Pro Display 与 Awwwards 顶奢数字展厅：
 * - 在卡片左上边缘绘制 1px 极细白金内倒角反射光（Top-left Inset Light）；
 * - 在边缘极其微弱地分离出 0.6px 的 RGB 棱镜色散光晕（Red/Cyan 分离），呈现纯水晶与高定光学镜头的奢华折射感。
 */
class PrismaticChromaticView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cornerRadius = 18f * resources.displayMetrics.density

    private val insetLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val redAberrationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.argb(45, 255, 60, 60) // 0.6px 红光微散
    }

    private val cyanAberrationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.argb(45, 0, 240, 255) // 0.6px 青光微散
    }

    private val cardPath = Path()
    private val redPath = Path()
    private val cyanPath = Path()
    private val bounds = RectF()

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(1f, 1f, w - 1f, h - 1f)

        cardPath.reset()
        cardPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)

        // 构造微偏移色散路径
        val redBounds = RectF(0.4f, 0.4f, w - 1.6f, h - 1.6f)
        redPath.reset()
        redPath.addRoundRect(redBounds, cornerRadius, cornerRadius, Path.Direction.CW)

        val cyanBounds = RectF(1.6f, 1.6f, w - 0.4f, h - 0.4f)
        cyanPath.reset()
        cyanPath.addRoundRect(cyanBounds, cornerRadius, cornerRadius, Path.Direction.CW)

        // 1px 极细顶角渐变白光
        insetLightPaint.shader = LinearGradient(
            0f, 0f, w * 0.7f, h * 0.7f,
            intArrayOf(
                Color.argb(160, 255, 255, 255),
                Color.argb(50, 77, 238, 234),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        // 1. 绘制 0.6px RGB 棱镜微色散
        canvas.drawPath(redPath, redAberrationPaint)
        canvas.drawPath(cyanPath, cyanAberrationPaint)

        // 2. 绘制 1px 白金内倒角高光
        canvas.drawPath(cardPath, insetLightPaint)
    }
}
