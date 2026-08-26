package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * 📜 典藏手稿首字下沉排版组件 (Editorial Drop-Cap Typography)
 * 对标 Siteinspire / Land-book / 中世纪古籍手稿排版：
 * - 提取正文首字符放大 3.2 倍并以典雅宋体/衬线体 (Serif Bold) 绘制；
 * - 后续正文前 2~3 行自动为首字预留空白边距并环绕流动；
 * - 首字底层伴随 5% 透明度的巨幅水印光晕，赋予手稿与典藏杂志级质感。
 */
class DropCapTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val dropCapPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        color = Color.parseColor("#4DEEEA") // 默认极光青色，或主强调色
    }

    private val dropCapShadowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        color = Color.parseColor("#154DEEEA") // 微弱水印阴影
    }

    private var dropChar: String = ""
    private var dropCapSize: Float = 0f
    private var dropCapMargin: Int = 0
    private var dropCapLines: Int = 2
    private val textBounds = Rect()

    init {
        val density = resources.displayMetrics.scaledDensity
        dropCapSize = textSize * 2.8f
        dropCapPaint.textSize = dropCapSize
        dropCapShadowPaint.textSize = dropCapSize * 1.35f
        dropCapMargin = (dropCapSize * 0.92f).toInt()
        letterSpacing = 0.03f
        setLineSpacing(4f * resources.displayMetrics.density, 1.15f)
    }

    fun setEditorialText(fullText: String, dropCapColor: Int = Color.parseColor("#4DEEEA"), linesSpan: Int = 2) {
        if (fullText.isBlank()) {
            text = ""
            return
        }

        this.dropCapLines = linesSpan
        dropCapPaint.color = dropCapColor
        dropCapShadowPaint.color = Color.argb(30, Color.red(dropCapColor), Color.green(dropCapColor), Color.blue(dropCapColor))

        val trimmed = fullText.trim()
        val firstChar = trimmed.substring(0, 1)
        val remainingText = if (trimmed.length > 1) trimmed.substring(1) else ""

        this.dropChar = firstChar
        dropCapPaint.getTextBounds(firstChar, 0, 1, textBounds)
        dropCapMargin = (textBounds.width() + 14 * resources.displayMetrics.density).toInt()

        val spannable = SpannableString(remainingText)
        spannable.setSpan(
            DropCapMarginSpan(dropCapLines, dropCapMargin),
            0,
            remainingText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        setText(spannable, BufferType.SPANNABLE)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dropChar.isNotEmpty()) {
            val padLeft = paddingLeft.toFloat()
            val padTop = paddingTop.toFloat()

            // 1. 绘制底层巨幅微弱水印
            canvas.drawText(
                dropChar,
                padLeft - 4f,
                padTop + textBounds.height() * 1.15f,
                dropCapShadowPaint,
            )

            // 2. 绘制主体典雅下沉首字
            canvas.drawText(
                dropChar,
                padLeft + 2f,
                padTop + textBounds.height() * 1.02f,
                dropCapPaint,
            )
        }
    }

    private class DropCapMarginSpan(
        private val lines: Int,
        private val margin: Int,
    ) : LeadingMarginSpan.LeadingMarginSpan2 {
        override fun getLeadingMargin(first: Boolean): Int = if (first) margin else 0
        override fun getLeadingMarginLineCount(): Int = lines
        override fun drawLeadingMargin(
            c: Canvas?,
            p: Paint?,
            x: Int,
            dir: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence?,
            start: Int,
            end: Int,
            first: Boolean,
            layout: Layout?,
        ) {}
    }
}
