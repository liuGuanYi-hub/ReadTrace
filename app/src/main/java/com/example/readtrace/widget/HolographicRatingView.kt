package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🌟 作品全息流光评分与星级解密控件 (HolographicRatingView)
 *
 * P5 阶段二核心组件：
 * 1. 五星全息逐级弹入（Staggered Star Pop & Bounce）：5 颗星带物理弹簧曲线逐次点亮；
 * 2. 物理阻尼数字滚轮解密（Score Rolling Animation）：从 0.0 滚动过渡至目标评分；
 * 3. 全息彩虹流光扫光（Holographic Chromatic Shimmer Shader）：极光青/金曜黄/霓虹粉漫射流光；
 * 4. 触控回弹与二次流光触发机制。
 */
class HolographicRatingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var targetScore: Double = 0.0
    private var currentDisplayScore: Double = 0.0
    private var isRecorded: Boolean = false

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 全息流光着色器
    private var shimmerTranslate: Float = 0f
    private var shimmerShader: LinearGradient? = null
    private val shaderMatrix = Matrix()
    private var shimmerAnimator: ValueAnimator? = null
    private var scoreAnimator: ValueAnimator? = null

    // 五星动画进度 [0..1]
    private val starProgresses = FloatArray(5) { 1.0f }

    private val holographicColors = intArrayOf(
        Color.parseColor("#FFE700"), // 金曜黄
        Color.parseColor("#4DEEEA"), // 极光青
        Color.parseColor("#FF2A85"), // 霓虹粉
        Color.parseColor("#FFE700"), // 金曜黄
    )

    init {
        val density = resources.displayMetrics.scaledDensity
        textPaint.textSize = 20f * density
        subTextPaint.textSize = 13f * density
        subTextPaint.color = Color.parseColor("#A89F91")
    }

    fun setRating(score: Double?, animate: Boolean = true) {
        if (score == null || score <= 0.0) {
            isRecorded = false
            targetScore = 0.0
            currentDisplayScore = 0.0
            invalidate()
            return
        }

        isRecorded = true
        // 外部传入 1~10 存储值，统一换算为 5 星制展示
        targetScore = (score / 2.0).coerceIn(0.0, 5.0)

        if (!animate) {
            currentDisplayScore = targetScore
            for (i in starProgresses.indices) {
                starProgresses[i] = 1.0f
            }
            invalidate()
            return
        }

        playRatingAnimation()
    }

    fun playRatingAnimation() {
        scoreAnimator?.cancel()
        shimmerAnimator?.cancel()

        // 1. 数值滚动动画
        currentDisplayScore = 0.0
        scoreAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = DecelerateInterpolator(1.3f)
            addUpdateListener { va ->
                val p = va.animatedValue as Float
                currentDisplayScore = targetScore * p

                // 计算 5 颗星分别的弹入进度
                val starFilledCount = (targetScore / 2.0).coerceIn(0.0, 5.0).toFloat()
                for (i in 0 until 5) {
                    val starThreshold = i / 5f
                    val starP = ((p - starThreshold) / 0.35f).coerceIn(0f, 1f)
                    starProgresses[i] = starP
                }

                invalidate()
            }
        }
        scoreAnimator?.start()

        // 2. 全息彩虹扫光
        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200L
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val p = va.animatedValue as Float
                val w = width.toFloat().coerceAtLeast(300f)
                shimmerTranslate = -w + p * w * 2.2f
                invalidate()
            }
        }
        shimmerAnimator?.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (isRecorded) {
                playRatingAnimation()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val desiredH = (38 * density).toInt()
        val desiredW = (240 * density).toInt()
        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            shimmerShader = LinearGradient(
                0f, 0f, w.toFloat() * 0.6f, 0f,
                holographicColors,
                null,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (!isRecorded) {
            textPaint.color = Color.parseColor("#A89F91")
            textPaint.shader = null
            canvas.drawText("暂无评分记录", 0f, h * 0.68f, textPaint)
            return
        }

        // 绘制全息五星
        val starSize = h * 0.48f
        val starGap = starSize * 0.30f
        var currentX = 0f
        val centerY = h * 0.50f

        val activeStars = (targetScore / 2.0).coerceIn(0.0, 5.0)

        for (i in 0 until 5) {
            val starAnimP = starProgresses[i]
            val isFull = i + 1 <= activeStars
            val isHalf = !isFull && (i < activeStars)

            canvas.save()
            // 逐星弹性缩放
            val scale = if (starAnimP < 0.6f) {
                starAnimP / 0.6f * 1.15f
            } else {
                1.15f - (starAnimP - 0.6f) / 0.4f * 0.15f
            }
            val starCenterX = currentX + starSize * 0.5f
            canvas.scale(scale, scale, starCenterX, centerY)

            drawSingleStar(canvas, starCenterX, centerY, starSize * 0.5f, isFull, isHalf)
            canvas.restore()

            currentX += starSize + starGap
        }

        // 绘制评分文本 (附带全息扫光)
        currentX += starGap * 1.5f
        if (shimmerShader != null && shimmerAnimator?.isRunning == true) {
            shaderMatrix.setTranslate(shimmerTranslate, 0f)
            shimmerShader?.setLocalMatrix(shaderMatrix)
            textPaint.shader = shimmerShader
        } else {
            textPaint.shader = null
            textPaint.color = Color.parseColor("#E0A96D") // 典雅烫金色
        }

        val scoreStr = String.format(Locale.getDefault(), "%.1f", currentDisplayScore)
        canvas.drawText(scoreStr, currentX, centerY + h * 0.18f, textPaint)

        val scoreTextWidth = textPaint.measureText(scoreStr)
        canvas.drawText("/ 5.0", currentX + scoreTextWidth + 8f, centerY + h * 0.16f, subTextPaint)
    }

    private fun drawSingleStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, isFull: Boolean, isHalf: Boolean) {
        val path = Path()
        val innerR = radius * 0.42f

        for (i in 0 until 10) {
            val r = if (i % 2 == 0) radius else innerR
            val angle = (i * 36 - 90) * Math.PI / 180.0
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        if (isFull) {
            starPaint.color = Color.parseColor("#FFE700") // 金曜黄
            starPaint.style = Paint.Style.FILL
            canvas.drawPath(path, starPaint)

            // 高光勾边
            starPaint.color = Color.parseColor("#FFFFFF")
            starPaint.style = Paint.Style.STROKE
            starPaint.strokeWidth = 1.2f
            starPaint.alpha = 140
            canvas.drawPath(path, starPaint)
        } else if (isHalf) {
            // 半星
            starPaint.color = Color.parseColor("#FFE700")
            starPaint.style = Paint.Style.FILL
            canvas.save()
            canvas.clipRect(cx - radius, cy - radius, cx, cy + radius)
            canvas.drawPath(path, starPaint)
            canvas.restore()

            starPaint.color = Color.parseColor("#44FFE700")
            canvas.save()
            canvas.clipRect(cx, cy - radius, cx + radius, cy + radius)
            canvas.drawPath(path, starPaint)
            canvas.restore()
        } else {
            starPaint.color = Color.parseColor("#33A89F91")
            starPaint.style = Paint.Style.FILL
            canvas.drawPath(path, starPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scoreAnimator?.cancel()
        shimmerAnimator?.cancel()
    }
}
