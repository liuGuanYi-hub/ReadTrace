package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.max

/**
 * 📜 策展级首字下沉排版视图 (DropCapTextView)
 *
 * P6 阶段一核心组件：
 * 1. 典藏书籍首字下沉（Editorial Drop Cap）：首字放大 2.8x 跨 2~3 行下沉，采用古典衬线字体（Serif）；
 * 2. 双字族混排支持（Dual-Typeface Typography）：首字衬线体 + 正文优雅行距 + 微缩等宽元标签；
 * 3. 装饰性衬底与金曜高光（Decorative Drop Cap Badge）：首字后方可选渲染微光金曜浮雕背景块；
 * 4. 自动处理中文引号、英文字母与多行自然环绕。
 */
class DropCapTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var rawContentText: String = ""
    private var dropCapLetter: String = ""
    private var bodyText: String = ""

    var dropCapColor: Int = Color.parseColor("#E0A96D") // 烫金/琥珀色
        set(value) {
            field = value
            dropCapPaint.color = value
            invalidate()
        }

    var dropCapBgColor: Int = Color.parseColor("#14E0A96D") // 浅金曜底色
        set(value) {
            field = value
            dropCapBgPaint.color = value
            invalidate()
        }

    var showDropCapBadge: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val dropCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        color = Color.parseColor("#E0A96D")
    }

    private val dropCapBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#14E0A96D")
    }

    private val dropCapBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#33E0A96D")
    }

    private val dropCapRect = RectF()
    private val dropCapBounds = Rect()

    init {
        typeface = Typeface.SERIF
        setLineSpacing(8f, 1.25f)
    }

    fun setEditorialText(content: CharSequence, dropCapOverride: String? = null) {
        val str = content.toString().trim()
        if (str.isEmpty()) {
            text = ""
            rawContentText = ""
            dropCapLetter = ""
            bodyText = ""
            return
        }

        rawContentText = str

        // 解析首字：如果首字符是引号“”，则连同首个字一起提取
        if (dropCapOverride != null && dropCapOverride.isNotEmpty()) {
            dropCapLetter = dropCapOverride
            bodyText = str
        } else if (str.startsWith("“") || str.startsWith("\"") || str.startsWith("「")) {
            val quoteChar = str.substring(0, 1)
            val nextChar = if (str.length > 1) str.substring(1, 2) else ""
            dropCapLetter = "$quoteChar$nextChar"
            bodyText = if (str.length > 2) str.substring(2) else ""
        } else {
            dropCapLetter = str.substring(0, 1)
            bodyText = if (str.length > 1) str.substring(1) else ""
        }

        updateFormattedSpannedText()
    }

    private fun updateFormattedSpannedText() {
        if (dropCapLetter.isEmpty()) {
            text = bodyText
            return
        }

        val density = resources.displayMetrics.scaledDensity
        val dropCapSize = textSize * 2.6f
        dropCapPaint.textSize = dropCapSize

        // 计算首字宽高
        dropCapPaint.getTextBounds(dropCapLetter, 0, dropCapLetter.length, dropCapBounds)
        val dropCapWidth = max(dropCapBounds.width().toFloat(), dropCapSize * 0.9f) + 16f * density
        val marginPixels = dropCapWidth.toInt() + (8 * density).toInt()

        val spanned = SpannableString(bodyText)
        val lineCountToIndent = 2

        spanned.setSpan(
            object : LeadingMarginSpan.LeadingMarginSpan2 {
                override fun getLeadingMargin(first: Boolean): Int {
                    return if (first) marginPixels else 0
                }

                override fun drawLeadingMargin(
                    c: Canvas?, p: Paint?, x: Int, dir: Int,
                    top: Int, baseline: Int, bottom: Int,
                    text: CharSequence?, start: Int, end: Int,
                    first: Boolean, layout: android.text.Layout?
                ) {}

                override fun getLeadingMarginLineCount(): Int {
                    return lineCountToIndent
                }
            },
            0,
            spanned.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        text = spanned
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dropCapLetter.isEmpty()) return

        val density = resources.displayMetrics.scaledDensity
        val padStart = paddingStart.toFloat()
        val padTop = paddingTop.toFloat()

        val dropCapSize = textSize * 2.6f
        dropCapPaint.textSize = dropCapSize
        dropCapPaint.getTextBounds(dropCapLetter, 0, dropCapLetter.length, dropCapBounds)

        val badgeW = max(dropCapBounds.width().toFloat(), dropCapSize * 0.95f) + 14f * density
        val badgeH = dropCapSize * 1.15f

        dropCapRect.set(padStart, padTop + 2f * density, padStart + badgeW, padTop + 2f * density + badgeH)

        if (showDropCapBadge) {
            // 绘制底色衬垫
            canvas.drawRoundRect(dropCapRect, 8f * density, 8f * density, dropCapBgPaint)
            canvas.drawRoundRect(dropCapRect, 8f * density, 8f * density, dropCapBorderPaint)
        }

        // 居中绘制下沉首字
        val textX = dropCapRect.centerX() - dropCapBounds.width() * 0.5f - dropCapBounds.left
        val textY = dropCapRect.centerY() + dropCapBounds.height() * 0.5f - dropCapBounds.bottom

        canvas.drawText(dropCapLetter, textX, textY, dropCapPaint)
    }
}
