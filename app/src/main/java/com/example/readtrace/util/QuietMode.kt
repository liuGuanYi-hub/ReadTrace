package com.example.readtrace.util

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.example.readtrace.data.UserPreferencesManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 🤫 安静模式总闸
 *
 * 气质基线是「空灵 · 宁静 · 舒服」，所以**默认安静**：不震动、不发声、背景不常驻流动。
 *
 * 做成单例 + volatile 缓存的原因：`SpatialAudioEngine` 这类入口不带 Context，
 * 而触觉调用点有 147 处——在引擎层拦一次，胜过在调用点各写一个 if。
 */
object QuietMode {

    private const val TAG = "QuietMode"

    @Volatile
    private var userPrefersQuiet = true

    @Volatile
    private var systemMotionDisabled = false

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** 冷启动时读一次偏好与系统动画缩放，之后由 [isQuiet] 零成本判定 */
    fun init(context: Context) {
        userPrefersQuiet = UserPreferencesManager.isQuietMode(context)
        systemMotionDisabled = isSystemMotionDisabled(context)
    }

    fun isQuiet(): Boolean = userPrefersQuiet || systemMotionDisabled

    fun setQuiet(context: Context, quiet: Boolean) {
        UserPreferencesManager.setQuietMode(context, quiet)
        if (userPrefersQuiet == quiet) return
        userPrefersQuiet = quiet
        // 通知已创建的自绘 View 立刻重判，否则要退出页面再进来才生效
        listeners.forEach { listener ->
            runCatching { listener() }
                .onFailure { Log.w(TAG, "安静模式监听器执行失败: ${it.message}") }
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** 系统「移除动画」开启时（动画缩放被设为 0）视同安静，尊重无障碍偏好 */
    private fun isSystemMotionDisabled(context: Context): Boolean = try {
        val resolver = context.contentResolver
        val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        val window = Settings.Global.getFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
        transition == 0f || window == 0f
    } catch (e: Throwable) {
        Log.w(TAG, "读取系统动画缩放失败，按未禁用处理: ${e.message}")
        false
    }
}
