package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * 🏷️ 高密极客等宽防伪元标签 (Editorial Monospaced Archive Badge)
 * 对标 Raycast / Linear / Stripe 极客与博物馆档案美学：
 * - 10sp 极客等宽字体 (Typeface.MONOSPACE)；
 * - 宽字距 (Letter Spacing 0.12f)；
 * - 1px 极细微光虚线边框与半透明墨黑底色；
 * - 格式示例：[ARCHIVE_ID: #RT-0924 // ELV: 8848M // CLASS: S+]
 */
class EditorialBadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var badgeContent: String = "[ARCHIVE_ID: #RT-0924 // STATUS: CURATED]"
    private var accentColor: Int = Color.parseColor("#4DEEEA") // 极光青

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1A0A0F1A")
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(8f, 5f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.10f
        textSize = 10.5f * resources.displayMetrics.scaledDensity
    }

    private val rectF = RectF()

    init {
        updateColors()
    }

    fun setBadgeContent(content: String, colorHex: String? = null) {
        if (colorHex != null) {
            try {
                accentColor = Color.parseColor(colorHex)
            } catch (_: Exception) {}
        }
        badgeContent = if (content.startsWith("[") && content.endsWith("]")) content else "[$content]"
        updateColors()
        requestLayout()
        invalidate()
    }

    fun setBadgeData(archiveId: String, metrics: String? = null, tag: String? = null, colorHex: String? = null) {
        if (colorHex != null) {
            try {
                accentColor = Color.parseColor(colorHex)
            } catch (_: Exception) {}
        }
        val builder = StringBuilder("[ARCHIVE: #$archiveId")
        if (!metrics.isNullOrBlank()) {
            builder.append(" // $metrics")
        }
        if (!tag.isNullOrBlank()) {
            builder.append(" // $tag")
        }
        builder.append("]")
        badgeContent = builder.toString()
        updateColors()
        requestLayout()
        invalidate()
    }

    private fun updateColors() {
        borderPaint.color = Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        textPaint.color = accentColor
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val textWidth = textPaint.measureText(badgeContent)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val padH = 20 * resources.displayMetrics.density
        val padV = 8 * resources.displayMetrics.density

        val desiredWidth = (textWidth + padH).toInt()
        val desiredHeight = (textHeight + padV).toInt()

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 6f * resources.displayMetrics.density

        rectF.set(2f, 2f, w - 2f, h - 2f)

        // 1. 绘制半透明胶囊底色
        canvas.drawRoundRect(rectF, r, r, bgPaint)

        // 2. 绘制 1px 极客微光虚线边框
        canvas.drawRoundRect(rectF, r, r, borderPaint)

        // 3. 居中绘制等宽防伪文本
        val fontMetrics = textPaint.fontMetrics
        val textY = (h - fontMetrics.descent - fontMetrics.ascent) / 2f
        val textX = (w - textPaint.measureText(badgeContent)) / 2f

        canvas.drawText(badgeContent, textX, textY, textPaint)
    }
}
