package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * 🌲 个人文化宇宙年轮图谱 (CulturalTreeRingsView)
 *
 * P15：以同心圆年轮呈现全年的文化沉浸足迹——
 * 每月一道环带，月度沉浸深度决定环带宽度与微光饱和度；
 * 中心奇点与环带微光随陀螺仪微倾角呼吸。
 */
class CulturalTreeRingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** 12 个月的沉浸分钟数（0~11 → 1月~12月），由外向内对应 12月→1月 */
    private var monthlyMinutes: IntArray = IntArray(12)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        textSize = 22f
    }

    private var revealProgress = 0f
    private var revealAnimator: android.animation.ValueAnimator? = null

    init {
        textPaint.textSize = 11f * context.resources.displayMetrics.density
    }

    fun setData(minutes: IntArray) {
        monthlyMinutes = IntArray(12) { minutes.getOrElse(it) { 0 } }
        revealProgress = 0f
        animateReveal()
    }

    private fun animateReveal() {
        revealAnimator?.cancel()
        revealAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animator ->
                revealProgress = (animator.animatedValue as? Float) ?: 0f
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        revealAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f
        val maxRadius = min(w, h) / 2f * 0.92f
        val ringGap = maxRadius / 12f

        // 中心奇点
        corePaint.shader = null
        corePaint.color = Color.parseColor("#FFE700")
        canvas.drawCircle(cx, cy, ringGap * 0.5f * revealProgress.coerceAtLeast(0.3f), corePaint)

        val maxMinutes = monthlyMinutes.maxOrNull() ?: 0

        // 由外向内：外环 = 12月，内环 = 1月
        for (monthIndex in 11 downTo 0) {
            val minutes = monthlyMinutes[monthIndex]
            val intensity = if (maxMinutes > 0) minutes.toFloat() / maxMinutes else 0f
            val radius = maxRadius - (11 - monthIndex) * ringGap
            if (radius <= ringGap * 0.4f) break

            // 环带宽度 1.5dp ~ 6dp，随沉浸深度增厚；空月为极细暗环
            val density = resources.displayMetrics.density
            ringPaint.strokeWidth = (1.5f + intensity * 4.5f) * density * revealProgress
            val alpha = if (minutes > 0) (70 + intensity * 160).toInt() else 34
            ringPaint.color = when (monthIndex % 4) {
                0 -> Color.argb(alpha, 77, 238, 234)   // 晶青
                1 -> Color.argb(alpha, 255, 231, 0)    // 流金
                2 -> Color.argb(alpha, 240, 0, 255)    // 星紫
                else -> Color.argb(alpha, 116, 238, 21) // 翠绿
            }
            canvas.drawCircle(cx, cy, radius * (0.4f + 0.6f * revealProgress), ringPaint)
        }

        // 年份角标
        canvas.drawText("M1→M12", cx - textPaint.measureText("M1→M12") / 2f, cy + maxRadius + textPaint.textSize * 0.4f, textPaint)
    }
}
