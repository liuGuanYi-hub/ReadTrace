package com.example.readtrace.util

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.readtrace.R
import com.example.readtrace.VinylCassettePlayerActivity

/**
 * 「正在播放」全局悬浮胶囊：唱机在其他页面持续播放时，
 * 于当前页面 DecorView 底部居中悬浮一枚胶囊，点击随时跳回黑胶唱机页。
 *
 * 采用 DecorView 内浮层（同 [FloatingBack] 模式），无需系统悬浮窗权限；
 * 由 ReadTraceApplication 的 ActivityLifecycleCallbacks 在每个页面 Resume 时安装、
 * Pause 时移除；唱机页自身与暂停/停止态自动跳过。
 */
object VinylNowPlayingFloat {

    private const val TAG = "vinyl_now_playing_float"
    private const val BOTTOM_MARGIN_DP = 88f

    fun install(activity: Activity) {
        if (!VinylCassettePlayerActivity.isEnginePlaying) return
        if (activity is VinylCassettePlayerActivity) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewWithTag<View>(TAG) != null) return

        val dp = activity.resources.displayMetrics.density
        val capsule = TextView(activity).apply {
            tag = TAG
            text = "💿 正在播放 · 点我回到唱机"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = ContextCompat.getDrawable(activity, R.drawable.vinyl_now_playing_capsule)
            setPadding((16 * dp).toInt(), (9 * dp).toInt(), (16 * dp).toInt(), (9 * dp).toInt())
            elevation = 12 * dp
            alpha = 0f
            animate().alpha(1f).setStartDelay(200).setDuration(250).start()
        }
        capsule.setOnClickListener { view ->
            view.isEnabled = false
            view.animate().alpha(0f).setDuration(120).withEndAction {
                activity.startActivity(Intent(activity, VinylCassettePlayerActivity::class.java))
            }.start()
        }
        content.addView(
            capsule,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = (BOTTOM_MARGIN_DP * dp).toInt() },
        )
    }

    fun remove(activity: Activity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        content.findViewWithTag<View>(TAG)?.let {
            content.removeView(it)
            (it as? TextView)?.animate()?.cancel()
        }
    }
}
