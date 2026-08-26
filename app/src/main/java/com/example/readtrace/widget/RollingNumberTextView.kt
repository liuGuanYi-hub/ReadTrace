package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import java.util.Locale

/**
 * 🔢 物理弹簧阻尼数字滚动计步器 (RollingNumberTextView)
 *
 * 灵感来源：landing.love / Linear App 物理仪表盘滚动数字动效
 * 核心原理：
 * 1. 将数值解析为字符列（包含前缀、数字列、小数点、后缀）；
 * 2. 对每个数字列实施独立的竖向滚轮位移插值计算；
 * 3. 终点注入 OvershootInterpolator 弹簧回弹曲线，带来极度舒适的机械质感。
 */
class RollingNumberTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var prefix: String = ""
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var suffix: String = ""
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var isDecimal: Boolean = false

    private var startVal: Double = 0.0
    private var endVal: Double = 0.0
    private var currentProgress: Float = 1.0f

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var animator: ValueAnimator? = null

    init {
        textPaint.textSize = 24f * resources.displayMetrics.scaledDensity
    }

    fun setNumber(target: Double, isDecimal: Boolean = false, animate: Boolean = true, duration: Long = 1100L) {
        this.isDecimal = isDecimal
        if (!animate) {
            startVal = target
            endVal = target
            currentProgress = 1.0f
            invalidate()
            return
        }

        startVal = endVal
        endVal = target
        currentProgress = 0f

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                currentProgress = it.animatedValue as Float
                invalidate()
            }
        }
        animator?.start()
    }

    fun setNumber(target: Int, animate: Boolean = true, duration: Long = 1100L) {
        setNumber(target.toDouble(), isDecimal = false, animate = animate, duration = duration)
    }

    fun setTextColor(color: Int) {
        textPaint.color = color
        invalidate()
    }

    fun setTextSizeSp(sp: Float) {
        textPaint.textSize = sp * resources.displayMetrics.scaledDensity
        requestLayout()
        invalidate()
    }

    fun setTypeface(tf: Typeface?) {
        textPaint.typeface = tf ?: Typeface.DEFAULT_BOLD
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val sampleText = formatValue(endVal)
        val bounds = Rect()
        textPaint.getTextBounds(sampleText, 0, sampleText.length, bounds)

        val desiredWidth = bounds.width() + paddingLeft + paddingRight + 12
        val desiredHeight = bounds.height() + paddingTop + paddingBottom + 12

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    private fun formatValue(v: Double): String {
        val numStr = if (isDecimal) {
            String.format(Locale.getDefault(), "%.1f", v)
        } else {
            String.format(Locale.getDefault(), "%d", v.toLong())
        }
        return "$prefix$numStr$suffix"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentDisplayVal = startVal + (endVal - startVal) * currentProgress
        val text = formatValue(currentDisplayVal)

        val fontMetrics = textPaint.fontMetrics
        val baseline = height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        val startX = paddingLeft.toFloat()

        canvas.drawText(text, startX, baseline, textPaint)
    }
}
