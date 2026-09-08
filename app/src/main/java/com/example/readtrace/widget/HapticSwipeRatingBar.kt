package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.example.readtrace.util.DimensionalScoringEngine
import com.example.readtrace.util.HapticFeedbackEngine
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 🌟 触觉滑动手势打分组件 (HapticSwipeRatingBar)
 *
 * P39 Phase 2 核心交互组件：
 * 1. 5 星 / 10.0 分制高精手势横向滑动：支持 0.1 连续平滑填充与 0.5 刻度档位；
 * 2. 机械棘轮微震触觉（Ratchet Haptic Tick）：手指滑越 0.5 刻度时触发细腻马达微震；
 * 3. 动态光感五角星渲染：未点亮炭墨底色、点亮金曜渐变高光（#FFE700 ~ #F4A261）与微米级裁剪；
 * 4. 实时数字与品阶标签联动（如：8.8 分 · 破圈神作）；
 * 5. 防误触与滑动拦截：精确接管横滑手势，松手即持久化定格。
 */
class HapticSwipeRatingBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // 评分数值 (0.0 ~ 10.0)
    var rating: Double = 0.0
        set(value) {
            val clamped = (value * 10.0).roundToInt() / 10.0
            val normalized = clamped.coerceIn(0.0, 10.0)
            if (abs(field - normalized) > 0.001) {
                field = normalized
                lastHapticStep = (normalized * 2.0).toInt()
                invalidate()
            }
        }

    // 触觉微震开关（默认开启）
    var hapticEnabled: Boolean = true

    // 是否仅用于展示（只读）
    var isIndicator: Boolean = false

    // 是否在右侧绘制实时分值与品阶
    var showScoreText: Boolean = true
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    // 监听器
    var onRatingChangeListener: ((score: Double, fromUser: Boolean) -> Unit)? = null
    var onRatingFinalizedListener: ((score: Double) -> Unit)? = null

    // 绘制画笔
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    private val tierTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tierBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 几何规格
    private val density = resources.displayMetrics.density
    private var starRadius = 14f * density
    private var starSpacing = 7f * density
    private val tierPillRect = RectF()

    // 预计算五角星 Path 缓存
    private var cachedStarRadius = -1f
    private val cachedStarPath = Path()

    // 渐变着色器
    private var activeStarShader: LinearGradient? = null
    private val activeColorStart = Color.parseColor("#FFE700") // 金曜黄
    private val activeColorEnd = Color.parseColor("#F4A261")   // 暖曜铜
    private val emptyStarColor = Color.parseColor("#2D2924")   // 暖暗炭墨
    private val emptyBorderColor = Color.parseColor("#5A5248") // 幽暗轮廓
    private val tierBgColor = Color.parseColor("#22FFE700")    // 品阶微光胶囊底色

    // 手势与滑动跟踪
    private var startTouchX = 0f
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var lastHapticStep = 0

    init {
        @Suppress("DEPRECATION")
        val scaledDensity = resources.displayMetrics.scaledDensity
        scoreTextPaint.textSize = 15f * scaledDensity
        scoreTextPaint.color = Color.parseColor("#FFE700")

        tierTextPaint.textSize = 10.5f * scaledDensity
        tierTextPaint.color = Color.parseColor("#E0A96D")
        tierTextPaint.typeface = Typeface.DEFAULT_BOLD

        tierBgPaint.color = tierBgColor
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val starsWidth = starRadius * 2 * 5 + starSpacing * 4
        val starsHeight = starRadius * 2

        var extraWidth = 0f
        if (showScoreText) {
            extraWidth = 110f * density // 预留分数 + 品阶胶囊空间
        }

        val desiredWidth = (paddingLeft + starsWidth + extraWidth + paddingRight).toInt()
        val desiredHeight = (paddingTop + starsHeight + paddingBottom).toInt()

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val starsWidth = starRadius * 2 * 5 + starSpacing * 4
        activeStarShader = LinearGradient(
            paddingLeft.toFloat(), 0f,
            paddingLeft.toFloat() + starsWidth, 0f,
            activeColorStart, activeColorEnd,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = (paddingTop + (height - paddingTop - paddingBottom) / 2f)
        var startX = paddingLeft.toFloat() + starRadius

        ensureStarPath(starRadius)

        // 1. 绘制 5 颗五角星
        for (i in 0 until 5) {
            val starValueMin = i * 2.0
            val starValueMax = (i + 1) * 2.0
            val fillRatio = when {
                rating >= starValueMax -> 1.0f
                rating <= starValueMin -> 0.0f
                else -> ((rating - starValueMin) / 2.0).toFloat().coerceIn(0f, 1f)
            }

            drawStarItem(canvas, startX, centerY, starRadius, fillRatio)
            startX += starRadius * 2 + starSpacing
        }

        // 2. 绘制评分与品阶文本
        if (showScoreText) {
            val textStartX = startX + 4f * density
            val scoreStr = if (rating > 0) String.format(Locale.US, "%.1f", rating) else "--"
            val textY = centerY + (scoreTextPaint.textSize * 0.35f)
            canvas.drawText(scoreStr, textStartX, textY, scoreTextPaint)

            if (rating > 0) {
                val scoreWidth = scoreTextPaint.measureText(scoreStr)
                val tierLabel = DimensionalScoringEngine.getShortTierLabel(rating)
                val tierPillX = textStartX + scoreWidth + 7f * density
                val tierTextWidth = tierTextPaint.measureText(tierLabel)

                val pillPaddingH = 5f * density
                val pillHeight = 16f * density
                val pillTop = centerY - pillHeight / 2f
                tierPillRect.set(
                    tierPillX,
                    pillTop,
                    tierPillX + tierTextWidth + pillPaddingH * 2,
                    pillTop + pillHeight
                )
                canvas.drawRoundRect(tierPillRect, 4f * density, 4f * density, tierBgPaint)

                val tierTextY = centerY + (tierTextPaint.textSize * 0.35f)
                canvas.drawText(tierLabel, tierPillX + pillPaddingH, tierTextY, tierTextPaint)
            }
        }
    }

    private fun drawStarItem(canvas: Canvas, cx: Float, cy: Float, radius: Float, fillRatio: Float) {
        canvas.save()
        canvas.translate(cx, cy)

        // 底色未点亮星形
        starPaint.shader = null
        starPaint.color = emptyStarColor
        starPaint.style = Paint.Style.FILL
        canvas.drawPath(cachedStarPath, starPaint)

        starBorderPaint.color = emptyBorderColor
        canvas.drawPath(cachedStarPath, starBorderPaint)

        // 点亮部分按比例裁剪绘制
        if (fillRatio > 0f) {
            val clipRight = -radius + (radius * 2 * fillRatio)
            canvas.save()
            canvas.clipRect(-radius, -radius, clipRight, radius)

            starPaint.shader = activeStarShader
            starPaint.style = Paint.Style.FILL
            canvas.drawPath(cachedStarPath, starPaint)

            starBorderPaint.color = Color.WHITE
            starBorderPaint.alpha = 180
            canvas.drawPath(cachedStarPath, starBorderPaint)

            canvas.restore()
        }

        canvas.restore()
    }

    private fun ensureStarPath(radius: Float) {
        if (abs(cachedStarRadius - radius) < 0.1f) return
        cachedStarRadius = radius
        cachedStarPath.reset()
        val innerR = radius * 0.42f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) radius else innerR
            val angle = (i * 36 - 90) * Math.PI / 180.0
            val x = (r * cos(angle)).toFloat()
            val y = (r * sin(angle)).toFloat()
            if (i == 0) cachedStarPath.moveTo(x, y) else cachedStarPath.lineTo(x, y)
        }
        cachedStarPath.close()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isIndicator || !isEnabled) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startTouchX = event.x
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                updateScoreFromTouch(event.x, isFinal = false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging && abs(event.x - startTouchX) > touchSlop) {
                    isDragging = true
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                updateScoreFromTouch(event.x, isFinal = false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                updateScoreFromTouch(event.x, isFinal = true)
                parent?.requestDisallowInterceptTouchEvent(false)
                onRatingFinalizedListener?.invoke(rating)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateScoreFromTouch(touchX: Float, isFinal: Boolean) {
        val starsTotalWidth = starRadius * 2 * 5 + starSpacing * 4
        val left = paddingLeft.toFloat()
        val right = left + starsTotalWidth

        val progress = ((touchX - left) / (right - left)).coerceIn(0f, 1f)
        val rawScore = progress * 10.0
        // 以 0.1 分为最小步进平滑过渡
        val rounded = (rawScore * 10.0).roundToInt() / 10.0
        val finalScore = rounded.coerceIn(0.0, 10.0)

        // 检查是否跨越 0.5 刻度档位触发机械棘轮微震
        val currentStep = (finalScore * 2.0).toInt()
        if (currentStep != lastHapticStep) {
            lastHapticStep = currentStep
            if (hapticEnabled) {
                HapticFeedbackEngine.dockBrushRatchetTick(context)
            }
        }

        if (abs(rating - finalScore) > 0.001) {
            rating = finalScore
            onRatingChangeListener?.invoke(rating, true)
            invalidate()
        }
    }
}
