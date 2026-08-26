package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * 🏷️ 策展级等宽防伪元标签控件 (EditorialBadgeView)
 *
 * P6 阶段一核心组件：
 * 1. 极客等宽字型（Monospace Typography）：采用等宽字体展示编号、时间戳、ISBN、策展版号；
 * 2. 宽字距呼吸感（Tracked Letter-Spacing）：字间距扩展至 +0.16em，营造奢侈品与典藏档案的精致感；
 * 3. 动态状态光点（Pulsing Status Indicator）：左侧点缀极光青/金曜黄自发光微缩信标；
 * 4. 极简描边与双重高光背板。
 */
class EditorialBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var badgeText: String = "ARCHIVE NO. 01 · EDITION 2026"
        set(value) {
            field = value.uppercase()
            requestLayout()
            invalidate()
        }

    var accentColor: Int = Color.parseColor("#4DEEEA") // 极光青
        set(value) {
            field = value
            dotPaint.color = value
            borderPaint.color = Color.argb(60, Color.red(value), Color.green(value), Color.blue(value))
            invalidate()
        }

    var showDot: Boolean = true
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.16f
        color = Color.parseColor("#E8E2D9")
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4DEEEA")
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1C1E24")
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#334DEEEA")
    }

    private val bgRect = RectF()

    init {
        val density = resources.displayMetrics.scaledDensity
        textPaint.textSize = 10f * density
    }

    fun setBadgeContent(code: String, meta: String = "READTRACE") {
        badgeText = "$meta // $code".uppercase()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val textW = textPaint.measureText(badgeText)
        val dotW = if (showDot) 16f * density else 0f
        val padH = 20f * density
        val padV = 10f * density

        val desiredW = (textW + dotW + padH).toInt()
        val desiredH = (textPaint.textSize + padV + 6f * density).toInt()

        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()

        bgRect.set(1f, 1f, w - 1f, h - 1f)
        val cornerRadius = h * 0.40f

        // 绘制背板与描边
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, borderPaint)

        val centerY = h * 0.5f
        var startX = 10f * density

        if (showDot) {
            // 绘制自发光光点
            val dotRadius = 3f * density
            canvas.drawCircle(startX + dotRadius, centerY, dotRadius, dotPaint)
            startX += dotRadius * 2 + 6f * density
        }

        // 绘制等宽文本
        val fontMetrics = textPaint.fontMetrics
        val textY = centerY - (fontMetrics.ascent + fontMetrics.descent) * 0.5f
        canvas.drawText(badgeText, startX, textY, textPaint)
    }
}
