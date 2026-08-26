package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 🌊 无缝平滑 60fps 跑马灯流视图 (InfiniteMarqueeView)
 *
 * 灵感来源：lapa.ninja / land-book.com 现代前沿无限流光跑马灯
 * 核心原理：
 * 1. 维护字符串列表与对应的宽度边界；
 * 2. 硬件加速 Canvas 双缓冲计算横向偏移，越界项无缝回环至末端；
 * 3. 触摸按住时减速暂停，松开时丝滑恢复流淌。
 */
class InfiniteMarqueeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var speedPxPerSec: Float = 65f
    var itemSpacing: Float = 48f

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var items = listOf(
        "✨ 夜鹿 ヨルシカ 2024 高光新曲《晴る》已入库",
        "🌌 心智大陆 1024 km² 3D 情绪等高线已生成",
        "🎟️ 观影印记《奥本海默》典藏票根已打印",
        "💽 拟真 33 RPM 黑胶唱臂落针声场已校准",
        "🕹️ 《艾尔登法环》白金实体卡带已入库",
    )

    private var currentOffset = 0f
    private var totalContentWidth = 0f
    private var isPaused = false
    private var animator: ValueAnimator? = null

    init {
        applyThemeColors()
        recalculateWidths()
    }

    fun applyThemeColors() {
        val isDark = com.example.readtrace.util.ThemeHelper.isDarkMode(context)
        if (isDark) {
            textPaint.color = Color.parseColor("#4DEEEA")
            badgePaint.color = Color.argb(40, 77, 238, 234)
            strokePaint.color = Color.argb(60, 77, 238, 234)
        } else {
            textPaint.color = Color.parseColor("#144A38")
            badgePaint.color = Color.parseColor("#E0F2E9")
            strokePaint.color = Color.parseColor("#BCE3D1")
        }
        invalidate()
    }

    fun setItems(newItems: List<String>) {
        if (newItems.isNotEmpty()) {
            items = newItems
            recalculateWidths()
            invalidate()
        }
    }

    fun setTextColor(color: Int) {
        textPaint.color = color
        invalidate()
    }

    fun setTextSizeSp(sp: Float) {
        textPaint.textSize = sp * resources.displayMetrics.scaledDensity
        recalculateWidths()
        requestLayout()
        invalidate()
    }

    private fun recalculateWidths() {
        var w = 0f
        val bounds = Rect()
        items.forEach { item ->
            textPaint.getTextBounds(item, 0, item.length, bounds)
            w += bounds.width() + itemSpacing * 2 + 32f
        }
        totalContentWidth = w.coerceAtLeast(100f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyThemeColors()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                if (!isPaused) {
                    currentOffset += (speedPxPerSec / 60f)
                    if (currentOffset >= totalContentWidth) {
                        currentOffset %= totalContentWidth
                    }
                    invalidate()
                }
            }
        }
        animator?.start()
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPaused = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPaused = false
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || items.isEmpty()) return

        val fontMetrics = textPaint.fontMetrics
        val baseline = height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        val bounds = Rect()

        var drawX = -currentOffset

        // 循环绘制足够填满屏幕并无缝回环的内容
        while (drawX < width + totalContentWidth) {
            items.forEach { item ->
                textPaint.getTextBounds(item, 0, item.length, bounds)
                val itemW = bounds.width().toFloat()

                // 绘制轻微圆角胶囊背景与高对比描边
                val rectLeft = drawX
                val rectRight = drawX + itemW + 24f
                if (rectRight > 0 && rectLeft < width) {
                    val rectTop = baseline + fontMetrics.ascent - 6
                    val rectBottom = baseline + fontMetrics.descent + 6
                    canvas.drawRoundRect(rectLeft, rectTop, rectRight, rectBottom, 16f, 16f, badgePaint)
                    canvas.drawRoundRect(rectLeft, rectTop, rectRight, rectBottom, 16f, 16f, strokePaint)
                    canvas.drawText(item, drawX + 12f, baseline, textPaint)
                }

                drawX += itemW + 24f + itemSpacing
            }
        }
    }
}
