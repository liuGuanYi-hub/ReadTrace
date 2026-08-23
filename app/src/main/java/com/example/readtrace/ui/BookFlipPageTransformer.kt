package com.example.readtrace.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

class BookFlipPageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        val density = page.resources.displayMetrics.density
        page.cameraDistance = 10000f * density

        when {
            position < -1f -> {
                // 完全翻到左侧视野之外
                page.alpha = 0f
                page.visibility = View.INVISIBLE
            }
            position <= 0f -> {
                // [-1, 0]：当前页面向左翻开
                page.visibility = View.VISIBLE
                page.alpha = 1f + position * 0.35f
                page.pivotX = page.width.toFloat()
                page.pivotY = page.height * 0.5f
                page.rotationY = 90f * position

                // 轻微下沉与缩放感
                val scale = 0.94f + (1f - abs(position)) * 0.06f
                page.scaleX = scale
                page.scaleY = scale
            }
            position <= 1f -> {
                // (0, 1]：右侧下一页准备翻入
                page.visibility = View.VISIBLE
                page.alpha = 1f - position * 0.35f
                page.pivotX = 0f
                page.pivotY = page.height * 0.5f
                page.rotationY = 90f * position

                val scale = 0.94f + (1f - abs(position)) * 0.06f
                page.scaleX = scale
                page.scaleY = scale
            }
            else -> {
                // 完全在右侧视野之外
                page.alpha = 0f
                page.visibility = View.INVISIBLE
            }
        }
    }
}
