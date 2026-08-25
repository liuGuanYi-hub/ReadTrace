package com.example.readtrace.util

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object ViewAnimationHelper {

    private val pressInterpolator = DecelerateInterpolator(2.0f)
    private val releaseInterpolator = OvershootInterpolator(1.6f)

    /**
     * 为 View 注入类似 iOS / macOS 的流畅 Q 弹物理触觉动效
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
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(240L)
                        .setInterpolator(releaseInterpolator)
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
     * 卡片轻微弹跳反馈 (Micro Bounce)
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
