package com.example.readtrace.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.addListener
import androidx.core.view.drawToBitmap
import kotlin.math.hypot

/**
 * 🌗 主题模式中心扩散过渡器 (Theme Center-Out Reveal Transition)
 *
 * 动效序列：
 * 1. 点击切换时截取当前界面为「旧主题快照」，全屏冻结（像素一致，用户无感知）；
 * 2. 在快照冻结之下静默完成 AppCompatDelegate 界面重建；
 * 3. 新界面就绪后，快照中心被挖出不断扩大的圆洞——新主题画面从屏幕中心
 *    向外逐渐显现，旧主题内容在洞外保持可见，直到圆洞吞没全屏。
 *
 * 全程新旧界面内容均持续可见，无纯色遮罩、无黑屏、无整页跳变。
 * 快照通过 ActivityLifecycleCallbacks 跨 Activity 重建接力。
 */
object ThemeTransitionPlayer {

    private const val HOLE_EXPAND_DURATION_MS = 700L
    private const val HOLE_EDGE_FEATHER_PX = 28f
    private const val PENDING_TIMEOUT_MS = 2500L

    @Volatile
    private var pendingSnapshot: Bitmap? = null

    @Volatile
    private var transitionBusy = false

    /** 从屏幕中心向外圆形揭示切换主题 */
    fun toggleFromScreenCenter(activity: Activity) {
        if (transitionBusy) return

        val snapshot = runCatching { activity.window.decorView.drawToBitmap() }.getOrNull()
        if (snapshot == null) {
            ThemeHelper.toggleDarkMode(activity)
            return
        }

        transitionBusy = true
        pendingSnapshot = snapshot

        // 冻结层：盖住当前 decor，画面像素与实时界面完全一致，遮护 recreate 全程
        val freeze = HoleRevealView(activity, snapshot, holeRadius = 0f)
        runCatching {
            (activity.window.decorView as? ViewGroup)?.addView(
                freeze,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }.onFailure {
            pendingSnapshot = null
            transitionBusy = false
            ThemeHelper.toggleDarkMode(activity)
            return
        }

        // 新 Activity 就绪后接续扩散动画；超时兜底防状态卡死
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(resumed: android.app.Activity) {
                // 旧实例在冻结瞬间的 resume 不处理；等待 recreate 产出的新实例
                if (resumed === activity) return
                activity.application.unregisterActivityLifecycleCallbacks(this)
                val bitmap = pendingSnapshot ?: return
                startCenterOutReveal(resumed, bitmap)
            }

            override fun onActivityCreated(p0: android.app.Activity, p1: Bundle?) = Unit
            override fun onActivityStarted(p0: android.app.Activity) = Unit
            override fun onActivityPaused(p0: android.app.Activity) = Unit
            override fun onActivityStopped(p0: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(p0: android.app.Activity, p1: Bundle) = Unit
            override fun onActivityDestroyed(p0: android.app.Activity) = Unit
        }
        activity.application.registerActivityLifecycleCallbacks(callback)

        ThemeHelper.toggleDarkMode(activity)
    }

    /** 在新界面上执行快照挖洞扩散：洞半径 0 → 全屏，新主题从中心显现 */
    private fun startCenterOutReveal(activity: Activity, snapshot: Bitmap) {
        val decor = activity.window.decorView as? ViewGroup
        if (decor == null) {
            releaseTransition()
            return
        }

        val reveal = HoleRevealView(activity, snapshot, holeRadius = 0f)
        runCatching {
            decor.addView(
                reveal,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }.onFailure {
            releaseTransition()
            return
        }

        reveal.post {
            val maxRadius = hypot(reveal.width.toDouble(), reveal.height.toDouble()).toFloat() * 0.72f
            val animator = android.animation.ValueAnimator.ofFloat(0f, maxRadius).apply {
                duration = HOLE_EXPAND_DURATION_MS
                interpolator = DecelerateInterpolator(0.8f)
                addUpdateListener { anim ->
                    reveal.holeRadius = anim.animatedValue as Float
                    reveal.invalidate()
                }
                addListener(
                    onEnd = {
                        releaseTransition()
                        (reveal.parent as? ViewGroup)?.removeView(reveal)
                    },
                )
            }
            animator.start()
        }

        // 若界面重建链路异常导致新界面迟迟未接管，超时强制释放冻结态
        reveal.postDelayed({
            if (reveal.parent != null && transitionBusy) {
                val current = reveal.holeRadius
                if (current <= 0f) {
                    releaseTransition()
                    (reveal.parent as? ViewGroup)?.removeView(reveal)
                }
            }
        }, PENDING_TIMEOUT_MS + HOLE_EXPAND_DURATION_MS)
    }

    private fun releaseTransition() {
        pendingSnapshot = null
        transitionBusy = false
    }

    /**
     * 中心挖洞揭示层：绘制旧主题快照，再用 CLEAR 模式在中心抠出
     * 带柔边（径向渐变羽化）的圆洞，洞内透出下层的新主题实时界面。
     */
    private class HoleRevealView(
        context: Context,
        private val snapshot: Bitmap,
        var holeRadius: Float,
    ) : View(context) {

        private val centerXPx = resources.displayMetrics.widthPixels / 2f
        private val centerYPx = resources.displayMetrics.heightPixels / 2f
        private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        override fun onDraw(canvas: Canvas) {
            val sc = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            canvas.drawBitmap(snapshot, 0f, 0f, null)
            if (holeRadius > 0f) {
                // 洞缘羽化：清除强度由不透明渐变至透明，圆边柔和过渡
                holePaint.shader = RadialGradient(
                    centerXPx,
                    centerYPx,
                    holeRadius,
                    intArrayOf(
                        android.graphics.Color.WHITE,
                        android.graphics.Color.WHITE,
                        android.graphics.Color.TRANSPARENT,
                    ),
                    floatArrayOf(0f, 1f - HOLE_EDGE_FEATHER_PX / holeRadius, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(centerXPx, centerYPx, holeRadius, holePaint)
            }
            canvas.restoreToCount(sc)
        }
    }
}
