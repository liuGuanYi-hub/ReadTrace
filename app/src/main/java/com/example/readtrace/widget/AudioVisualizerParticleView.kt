package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * 🌌 音轨流光与金句氛围粒子视图 (AudioVisualizerParticleView)
 *
 * 核心特性：
 * 1. 随音乐节奏律动的多层环形声波光晕；
 * 2. 围绕唱机漂浮升腾的自发光星芒粒子系统；
 * 3. 自适应暗夜与极光深色莫兰迪光影。
 */
class AudioVisualizerParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var glowShader: Shader? = null
    private var cachedGlowRadius = -1f
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var isPlaying = false
    private var pulsePhase = 0f
    private val particles = mutableListOf<Particle>()

    private var animator: ValueAnimator? = null

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float,
        var alpha: Int,
        var maxAlpha: Int,
        var color: Int,
    )

    init {
        initParticles()
        setupAnimator()
    }

    private fun initParticles() {
        particles.clear()
        val colors = intArrayOf(
            Color.parseColor("#4DEEEA"), // 冰蓝
            Color.parseColor("#74EE15"), // 极光绿
            Color.parseColor("#FFE700"), // 琥珀金
            Color.parseColor("#F000FF"), // 霓虹紫
            Color.parseColor("#FFFFFF"),
        )
        for (i in 0 until 36) {
            particles.add(
                Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    vx = (Random.nextFloat() - 0.5f) * 0.002f,
                    vy = -Random.nextFloat() * 0.003f - 0.001f,
                    radius = Random.nextFloat() * 4f + 2f,
                    alpha = Random.nextInt(40, 180),
                    maxAlpha = Random.nextInt(120, 240),
                    color = colors[Random.nextInt(colors.size)],
                ),
            )
        }
    }

    private fun setupAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (isPlaying) {
                    pulsePhase = (pulsePhase + 0.04f) % (2f * Math.PI.toFloat())
                    updateParticles()
                    invalidate()
                }
            }
        }
    }

    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing
        if (isPlaying) {
            animator?.start()
        } else {
            animator?.cancel()
        }
        invalidate()
    }

    private fun updateParticles() {
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            if (p.y < 0f) {
                p.y = 1.0f
                p.x = Random.nextFloat()
            }
            if (p.x < 0f) p.x = 1.0f
            if (p.x > 1f) p.x = 0.0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * 0.5f
        val cy = h * 0.44f

        // 1. 绘制背景呼吸声波光晕：渐变按基准半径缓存，呼吸用 canvas 缩放实现，每帧零分配（P38-P4）
        if (isPlaying) {
            val pulseScale = 1.0f + 0.08f * kotlin.math.sin(pulsePhase)
            val baseRadius = w * 0.48f
            if (baseRadius != cachedGlowRadius) {
                cachedGlowRadius = baseRadius
                glowShader = RadialGradient(
                    cx, cy, baseRadius,
                    intArrayOf(
                        Color.argb(38, 116, 238, 21),
                        Color.argb(22, 77, 238, 234),
                        Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            glowPaint.shader = glowShader
            canvas.save()
            canvas.scale(pulseScale, pulseScale, cx, cy)
            canvas.drawCircle(cx, cy, baseRadius, glowPaint)
            canvas.restore()
        }

        // 2. 绘制漂浮粒子
        for (p in particles) {
            particlePaint.color = p.color
            particlePaint.alpha = if (isPlaying) p.maxAlpha else (p.alpha * 0.4f).toInt()
            canvas.drawCircle(p.x * w, p.y * h, p.radius, particlePaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
