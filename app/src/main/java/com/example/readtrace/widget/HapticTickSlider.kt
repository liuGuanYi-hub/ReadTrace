package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.example.readtrace.util.HapticFeedbackEngine
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 🎚️ 磁吸刻度感物理阻尼推杆 (HapticTickSlider)
 *
 * P6 阶段五核心交互控件：
 * 1. 磁吸刻度微震（Magnetic Haptic Tick）：拖拽跨越分段刻度（如每 10% / 整数分）时，触发线性马达轻巧段落感震颤；
 * 2. 物理弹性回弹（Elastic Overshoot Release）：松手时拇指指示器带有阻尼弹性回弹动画与磁吸吸附；
 * 3. 全息光晕指示器与等宽数字气泡（Tracked Monospace Bubble）；
 * 4. 完美支持阅读进度调节、评分滑动、音频快进与 3D 切片推杆。
 */
class HapticTickSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var progress: Float = 0.5f // 0.0f ~ 1.0f
        set(value) {
            val clamped = value.coerceIn(0.0f, 1.0f)
            if (field != clamped) {
                field = clamped
                onProgressChanged?.invoke(field)
                invalidate()
            }
        }

    var stepCount: Int = 10 // 10 个刻度段 (0%, 10%, 20%...100%)
    var isSnapEnabled: Boolean = true
    var accentColor: Int = Color.parseColor("#4DEEEA") // 极光青 / 浅金
        set(value) {
            field = value
            thumbGlowPaint.color = value
            invalidate()
        }

    var onProgressChanged: ((Float) -> Unit)? = null

    private var lastHapticStep: Int = -1
    private var isDragging: Boolean = false
    private var thumbScale: Float = 1.0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1C1E26")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#44FFFFFF")
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val thumbGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4DEEEA")
        alpha = 90
    }

    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.10f
        color = Color.parseColor("#E0A96D")
    }

    private val trackRect = RectF()
    private val progressRect = RectF()

    init {
        val density = resources.displayMetrics.scaledDensity
        bubbleTextPaint.textSize = 10f * density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val desiredH = (52f * density).toInt()
        setMeasuredDimension(
            resolveSize((240f * density).toInt(), widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val density = resources.displayMetrics.density
        val padH = 24f * density
        val trackW = width - padH * 2

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                animateThumbScale(1.35f)
                updateProgressFromX(event.x, padH, trackW)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateProgressFromX(event.x, padH, trackW)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                animateThumbScale(1.0f)
                if (isSnapEnabled && stepCount > 0) {
                    val stepFraction = 1.0f / stepCount
                    val nearestStep = (progress / stepFraction).roundToInt()
                    val targetProgress = (nearestStep * stepFraction).coerceIn(0.0f, 1.0f)
                    animateSnapTo(targetProgress)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateProgressFromX(x: Float, padH: Float, trackW: Float) {
        if (trackW <= 0) return
        val raw = ((x - padH) / trackW).coerceIn(0.0f, 1.0f)
        progress = raw

        // 检测是否跨越段落刻度触发触觉反馈
        if (stepCount > 0) {
            val currentStep = (raw * stepCount).toInt()
            if (currentStep != lastHapticStep) {
                lastHapticStep = currentStep
                HapticFeedbackEngine.pageTurnRustle(context)
            }
        }
    }

    private fun animateThumbScale(target: Float) {
        ValueAnimator.ofFloat(thumbScale, target).apply {
            duration = 180
            addUpdateListener {
                thumbScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateSnapTo(target: Float) {
        ValueAnimator.ofFloat(progress, target).apply {
            duration = 240
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener {
                progress = it.animatedValue as Float
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val padH = 24f * density
        val trackW = w - padH * 2
        val trackH = 8f * density
        val centerY = h * 0.65f

        // 1. 绘制底轨
        trackRect.set(padH, centerY - trackH * 0.5f, w - padH, centerY + trackH * 0.5f)
        canvas.drawRoundRect(trackRect, trackH * 0.5f, trackH * 0.5f, trackPaint)

        // 2. 绘制刻度点
        if (stepCount > 0) {
            for (i in 0..stepCount) {
                val tickX = padH + trackW * (i.toFloat() / stepCount)
                canvas.drawCircle(tickX, centerY, 2.5f * density, tickPaint)
            }
        }

        // 3. 绘制激活进度轨
        val thumbX = padH + trackW * progress
        progressRect.set(padH, centerY - trackH * 0.5f, thumbX, centerY + trackH * 0.5f)
        progressPaint.shader = LinearGradient(
            padH, centerY, thumbX, centerY,
            intArrayOf(Color.parseColor("#4DEEEA"), Color.parseColor("#E0A96D")),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(progressRect, trackH * 0.5f, trackH * 0.5f, progressPaint)

        // 4. 绘制拇指发光光晕与滑块实体
        val thumbRadius = 10f * density * thumbScale
        canvas.drawCircle(thumbX, centerY, thumbRadius * 1.8f, thumbGlowPaint)
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint)

        // 5. 绘制浮动数字气泡
        val percentText = "${(progress * 100).roundToInt()}%"
        val textW = bubbleTextPaint.measureText(percentText)
        val textX = (thumbX - textW * 0.5f).coerceIn(padH * 0.5f, w - padH * 0.5f - textW)
        val textY = centerY - thumbRadius - 10f * density
        canvas.drawText(percentText, textX, textY, bubbleTextPaint)
    }
}
