package com.example.readtrace.util

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

object ViewAnimationHelper {

    private val pressInterpolator = DecelerateInterpolator(2.0f)

    /**
     * 松手复位曲线。
     *
     * 原为 `OvershootInterpolator(1.6f)`——松手时先冲过头再弹回，形成「Q 弹」观感。
     * 按「空灵 · 宁静 · 舒服」的气质基线，过冲回弹属于「跳」的一类，
     * 故改为纯减速曲线：按下沉下去，松手平静回到原位，无过冲。
     */
    private val releaseInterpolator = DecelerateInterpolator(1.6f)

    /**
     * 为 View 注入「轻按下沉 → 平静复位」的触感反馈。
     *
     * 全仓 73 个调用点共用这一处实现，因此改版只需改这里。
     * 时长保持不变（按下 120ms / 复位 240ms），仅去掉回弹曲线。
     *
     * 注：系统「移除动画」开启时，`ViewPropertyAnimator` 底层的 `ValueAnimator`
     * 会自行把时长压到 0，无需在这里额外判 `QuietMode`。
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachSpringTouch(view: View, targetScale: Float = 0.96f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(120L)
                        .setInterpolator(pressInterpolator)
                        // T2.3：动画期间渲染到离屏硬件层，缩放由 GPU 合成，不触发整棵视图树重绘
                        .withLayer()
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(240L)
                        .setInterpolator(releaseInterpolator)
                        .withLayer()
                        .start()
                }
            }
            false
        }
    }

    /**
     * 阶梯式自然渐入上升动效 (Apple Staggered Entry)
     */
    fun staggerFadeIn(view: View, index: Int, baseDelay: Long = 40L, duration: Long = 420L) {
        view.alpha = 0f
        view.translationY = 28f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(index.coerceAtMost(8) * baseDelay)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
    }

    /**
     * 卡片轻微按压反馈 (Micro Press)
     *
     * 方法名保留 `Bounce` 以维持既有 10 个调用点零改动；实现已随改版去掉回弹，
     * 现为「缩到 0.97 → 平静复位」，不再有过冲。
     */
    fun playCardBounce(view: View) {
        view.animate()
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(100L)
            .setInterpolator(pressInterpolator)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220L)
                    .setInterpolator(releaseInterpolator)
                    .start()
            }
            .start()
    }
}
