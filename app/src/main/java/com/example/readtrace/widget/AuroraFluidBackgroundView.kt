package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class AuroraFluidBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null
    private var time = 0f

    // 默认日间高雅流体极光调色盘 (文艺草木绿、晨曦金曜、薄雾浅蓝、暖粉霞光)
    private var colors = intArrayOf(
        Color.parseColor("#4D8FB399"), // 柔和草木绿
        Color.parseColor("#4DE0D3AF"), // 晨曦浅金
        Color.parseColor("#4D9BB7D4"), // 薄雾霁蓝
        Color.parseColor("#3DE0B4C0"), // 浅粉霞光
    )

    private val isDarkTheme: Boolean
        get() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private var audioScale: Float = 1.0f

    // 预热单元着色器，避免每帧 4 次 RadialGradient 与 SkShader 堆内存分配
    private val orbShaders = arrayOfNulls<RadialGradient>(4)

    init {
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 原生高斯深度模糊着色器，将多色球融合为超平滑液体流动网格
            try {
                setRenderEffect(RenderEffect.createBlurEffect(130f, 130f, Shader.TileMode.CLAMP))
            } catch (_: Exception) {}
        }
        updateThemePalette()
    }

    private fun rebuildShaders() {
        for (i in 0 until 4) {
            val color = colors[i % colors.size]
            val transparentColor = color and 0x00FFFFFF
            orbShaders[i] = RadialGradient(
                0f, 0f, 1f,
                color, transparentColor,
                Shader.TileMode.CLAMP,
            )
        }
    }

    fun updateThemePalette(darkMode: Boolean = isDarkTheme) {
        colors = com.example.readtrace.util.CircadianLightingEngine.getCircadianColors(isDark = darkMode)
        rebuildShaders()
        invalidate()
    }

    fun setCircadianPhase(phase: com.example.readtrace.util.CircadianLightingEngine.CircadianPhase, darkMode: Boolean = isDarkTheme) {
        colors = com.example.readtrace.util.CircadianLightingEngine.getCircadianColors(phase, isDark = darkMode)
        rebuildShaders()
        invalidate()
    }

    /**
     * 🎵 音频低频反应式光斑脉冲 (Audio-Reactive Pulse)
     */
    fun applyAudioPulse(bassLevel: Float) {
        audioScale = (1.0f + bassLevel.coerceIn(0f, 0.6f)).coerceIn(1.0f, 1.6f)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }

    fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 20000L // 20 秒缓慢周期流淌，极致舒缓优雅
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                time = it.animatedFraction * 2 * Math.PI.toFloat()
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 绘制 4 个按多维正弦流体轨迹运动的交融光斑，受音频脉冲缩放驱动
        val scale = audioScale
        drawOrb(canvas, 0, w * 0.3f + sin(time) * w * 0.25f, h * 0.25f + cos(time * 0.8f) * h * 0.15f, w * 0.55f * scale)
        drawOrb(canvas, 1, w * 0.7f + cos(time * 1.1f) * w * 0.22f, h * 0.40f + sin(time * 0.9f) * h * 0.20f, w * 0.60f * scale)
        drawOrb(canvas, 2, w * 0.4f + cos(time * 0.7f) * w * 0.28f, h * 0.70f + sin(time * 1.2f) * h * 0.18f, w * 0.58f * scale)
        drawOrb(canvas, 3, w * 0.8f + sin(time * 1.3f) * w * 0.20f, h * 0.85f + cos(time) * h * 0.15f, w * 0.50f * scale)
    }

    private fun drawOrb(canvas: Canvas, colorIndex: Int, cx: Float, cy: Float, radius: Float) {
        if (radius <= 0f) return
        val shader = orbShaders[colorIndex % orbShaders.size] ?: return
        orbPaint.shader = shader
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(radius, radius)
        canvas.drawCircle(0f, 0f, 1f, orbPaint)
        canvas.restore()
    }
}
