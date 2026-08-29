package com.example.readtrace.util

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView

/**
 * 滚动进场动效：目标 View 进入 ScrollView 视口时渐入上浮（每个 View 仅播放一次，同屏依次错峰）
 */
object ScrollReveal {

    fun attach(scrollView: ScrollView, targets: List<View>) {
        if (targets.isEmpty()) return
        val revealed = mutableSetOf<View>()
        val density = targets[0].resources.displayMetrics.density

        targets.forEach {
            it.alpha = 0f
            it.translationY = 36f * density
        }

        fun contentRoot(): View = scrollView.getChildAt(0)

        fun View.offsetToRoot(): Int {
            var offset = 0
            var current: View? = this
            while (current != null && current !== contentRoot()) {
                offset += current.top
                current = current.parent as? View
            }
            return offset
        }

        fun check() {
            val threshold = scrollView.scrollY + scrollView.height - 60
            var stagger = 0
            targets.filter { it !in revealed }.forEach { v ->
                if (v.offsetToRoot() <= threshold) {
                    revealed += v
                    v.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(stagger * 50L)
                        .setDuration(420L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    stagger++
                }
            }
        }

        scrollView.viewTreeObserver.addOnScrollChangedListener { check() }
        scrollView.post { check() }
    }
}
