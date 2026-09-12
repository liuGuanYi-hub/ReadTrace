package com.example.readtrace.util

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.addListener
import com.example.readtrace.R
import kotlin.math.hypot

/**
 * 🌗 主题模式圆形揭示过渡器 (Theme Circular Reveal Transition)
 *
 * 点击主题按钮时，目标主题的背景色从按钮位置以圆形波纹向外扩散铺满全屏，
 * 铺满后在遮罩之下静默完成 AppCompatDelegate 的界面重建。
 * 遮罩挂在 decorView 顶层并使用与目标界面一致的背景色——
 * 重建后旧视图树（连同遮罩）整体替换，新界面背景与遮罩无缝同色，
 * 观感即「新主题从按钮向外蔓延」，无整页跳变与白闪。
 */
object ThemeTransitionPlayer {

    private const val REVEAL_DURATION_MS = 380L
    private const val OVERLAY_CLEANUP_DELAY_MS = 450L

    /** 从 anchorView 位置向外圆形揭示切换主题 */
    fun toggleWithCircularReveal(activity: Activity, anchorView: View) {
        val goingDark = !ThemeHelper.isDarkMode(activity)
        val targetBackgroundColor = resolveBackgroundForMode(activity, goingDark)

        val decor = activity.window.decorView as? ViewGroup
        if (decor == null) {
            ThemeHelper.toggleDarkMode(activity)
            return
        }

        val overlay = View(activity).apply { setBackgroundColor(targetBackgroundColor) }
        // 遮罩挂载失败则退化为普通切换，保证功能不丢
        runCatching {
            decor.addView(
                overlay,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }.onFailure {
            ThemeHelper.toggleDarkMode(activity)
            return
        }

        val loc = IntArray(2)
        anchorView.getLocationInWindow(loc)
        val centerX = loc[0] + anchorView.width / 2f
        val centerY = loc[1] + anchorView.height / 2f

        overlay.post {
            val maxRadius = hypot(decor.width.toDouble(), decor.height.toDouble()).toFloat()
            val reveal = ViewAnimationUtils.createCircularReveal(
                overlay,
                centerX.toInt(),
                centerY.toInt(),
                0f,
                maxRadius,
            ).apply {
                duration = REVEAL_DURATION_MS
                interpolator = DecelerateInterpolator(1.6f)
                addListener(
                    onEnd = {
                        // 遮罩已铺满全屏：在遮罩之下完成重建。
                        // recreate 后旧 decorView（含遮罩）整体替换，无需手动揭幕。
                        ThemeHelper.toggleDarkMode(activity)
                        overlay.postDelayed({
                            runCatching { (overlay.parent as? ViewGroup)?.removeView(overlay) }
                        }, OVERLAY_CLEANUP_DELAY_MS)
                    },
                )
            }
            reveal.start()
        }
    }

    /** 以目标深浅模式解析 readtrace_background（当前 Activity resources 仍是旧模式，需按目标模式重定向） */
    private fun resolveBackgroundForMode(activity: Activity, goingDark: Boolean): Int {
        val config = Configuration(activity.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (goingDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        val targetContext = activity.createConfigurationContext(config)
        return targetContext.getColor(R.color.readtrace_background)
    }
}
