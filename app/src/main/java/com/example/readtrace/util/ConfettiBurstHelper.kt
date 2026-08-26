package com.example.readtrace.util

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🎉 真实物理重力微粒礼花炸裂引擎 (ConfettiBurstHelper)
 *
 * 灵感来源：onepagelove.com / Stripe 庆典物理彩屑微粒动效
 * 核心原理：
 * 1. 模拟 48 颗微粒彩屑的物理抛射（初速度、发射仰角、空气阻力、重力加速度 g=1600px/s²、3D 翻转角速度）；
 * 2. 动态注入当前 Activity 的根 DecorView，1200ms 后自动彻底释放内存；
 * 3. 5 种先锋霓虹色彩与方块、丝带、菱形混合形态。
 */
object ConfettiBurstHelper {

    private val colors = intArrayOf(
        Color.parseColor("#4DEEEA"), // 极光青
        Color.parseColor("#FFE700"), // 金曜黄
        Color.parseColor("#FF2A85"), // 霓虹粉
        Color.parseColor("#74EE15"), // 荧光绿
        Color.parseColor("#F000FF"), // 幻彩紫
        Color.parseColor("#FFFFFF")  // 纯白
    )

    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        val size: Float,
        val shape: Int, // 0: 方块, 1: 圆形, 2: 丝带
        var rotation: Float,
        var rotSpeed: Float,
    ) {
        fun update(dt: Float) {
            x += vx * dt
            y += vy * dt
            vy += 1600f * dt // 物理重力加速度
            vx *= 0.985f     // 空气阻力
            rotation += rotSpeed * dt
        }
    }

    private class ConfettiOverlayView(context: Context, originX: Float, originY: Float) : View(context) {
        private val particles = mutableListOf<Particle>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var animator: ValueAnimator? = null
        private var alphaProgress = 1.0f

        init {
            val rand = Random(System.currentTimeMillis())
            for (i in 0 until 52) {
                val angle = rand.nextDouble(Math.PI * 1.1, Math.PI * 1.9).toFloat() // 向上锥形炸裂
                val speed = rand.nextFloat() * 750f + 400f
                val vx = cos(angle) * speed
                val vy = sin(angle) * speed
                val col = colors[rand.nextInt(colors.size)]
                val sz = rand.nextFloat() * 12f + 8f
                val shape = rand.nextInt(3)
                val rot = rand.nextFloat() * 360f
                val rotSpd = (rand.nextFloat() - 0.5f) * 720f

                particles.add(Particle(originX, originY, vx, vy, col, sz, shape, rot, rotSpd))
            }

            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200L
                interpolator = DecelerateInterpolator(1.2f)
                addUpdateListener {
                    val p = it.animatedValue as Float
                    alphaProgress = (1f - p).coerceIn(0f, 1f)
                    particles.forEach { pt -> pt.update(0.016f) }
                    invalidate()
                }
            }
        }

        fun start(onComplete: () -> Unit) {
            animator?.start()
            postDelayed({
                animator?.cancel()
                onComplete()
            }, 1250L)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            particles.forEach { p ->
                paint.color = p.color
                paint.alpha = (255 * alphaProgress).toInt()

                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.rotation)

                when (p.shape) {
                    0 -> canvas.drawRect(-p.size / 2, -p.size / 2, p.size / 2, p.size / 2, paint)
                    1 -> canvas.drawCircle(0f, 0f, p.size / 2, paint)
                    2 -> canvas.drawRoundRect(-p.size, -p.size / 4, p.size, p.size / 4, 4f, 4f, paint)
                }
                canvas.restore()
            }
        }
    }

    /**
     * 在屏幕指定坐标触发物理彩屑微粒礼花
     */
    fun burst(activity: Activity?, originX: Float, originY: Float) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        val decorView = activity.window.decorView as? ViewGroup ?: return

        val overlay = ConfettiOverlayView(activity, originX, originY).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        decorView.addView(overlay)
        overlay.start {
            decorView.removeView(overlay)
        }
    }

    /**
     * 在屏幕中央触发礼花
     */
    fun burstCenter(activity: Activity?) {
        if (activity == null) return
        val dm = activity.resources.displayMetrics
        burst(activity, dm.widthPixels * 0.5f, dm.heightPixels * 0.45f)
    }
}
