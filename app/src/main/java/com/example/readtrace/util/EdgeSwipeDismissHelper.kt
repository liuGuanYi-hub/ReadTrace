package com.example.readtrace.util

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * 📱 全屏边缘侧滑返回 (EdgeSwipeDismissHelper)
 *
 * P14 极客单手心流：详情页、工坊页、年鉴页支持从屏幕左边缘向右拖拽返回，
 * 页面视图跟手平移 + 边缘微光提示，松手越过阈值即关闭，否则弹性回位。
 */
object EdgeSwipeDismissHelper {

    private const val EDGE_WIDTH_DP = 24f
    private const val FINISH_THRESHOLD_RATIO = 0.28f
    private const val MAX_TRANSLATION_DP = 220f

    /** 在 Activity decorView 上叠加一条左边缘透明触摸条（不遮挡正常内容交互） */
    fun install(activity: Activity) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val root = activity.window.decorView as? ViewGroup ?: return
        if (root.findViewWithTag<View>("edge_swipe_strip") != null) return

        val strip = View(activity).apply {
            tag = "edge_swipe_strip"
            setBackgroundColor(Color.TRANSPARENT)
        }
        root.addView(
            strip,
            FrameLayout.LayoutParams(dp(EDGE_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START),
        )

        var downY = 0f
        var tracking = false

        strip.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 避让左上角悬浮返回键 / 顶部工具栏热区，避免遮挡点击
                    val orb = activity.findViewById<ViewGroup>(android.R.id.content)?.findViewWithTag<View>("floating_back_orb")
                    if (orb != null && orb.isShown) {
                        val orbRect = android.graphics.Rect()
                        orb.getGlobalVisibleRect(orbRect)
                        orbRect.inset(-dp(8f), -dp(8f)) // 8dp 触控保护
                        if (orbRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                            return@setOnTouchListener false
                        }
                    } else if (event.y < dp(64f)) {
                        return@setOnTouchListener false
                    }

                    tracking = true
                    downY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (tracking) {
                        val dx = event.x.coerceAtLeast(0f)
                        val translation = (dx * 0.9f).coerceAtMost(dp(MAX_TRANSLATION_DP).toFloat())
                        root.translationX = translation
                        root.alpha = 1f - (translation / (dp(MAX_TRANSLATION_DP) * 1.6f)).coerceIn(0f, 0.35f)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (tracking) {
                        tracking = false
                        val dx = event.x
                        val passed = dx > dp(EDGE_WIDTH_DP) * FINISH_THRESHOLD_RATIO * 6f
                        if (passed && event.actionMasked == MotionEvent.ACTION_UP) {
                            activity.finish()
                            activity.overridePendingTransition(
                                android.R.anim.slide_in_left,
                                android.R.anim.slide_out_right,
                            )
                        } else {
                            // 弹性回位
                            root.animate().translationX(0f).alpha(1f).setDuration(180L)
                                .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
                                .start()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }
}
