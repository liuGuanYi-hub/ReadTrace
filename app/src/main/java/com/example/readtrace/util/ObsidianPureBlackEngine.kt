package com.example.readtrace.util

import android.content.Context
import com.example.readtrace.data.UserPreferencesManager

/**
 * 🖤 OLED 曜石真黑引擎 (ObsidianPureBlackEngine)
 *
 * P14：针对 OLED / AMOLED 屏幕的极致黑夜模式——
 * 暗色主题下将全局极光背景熄灭为绝对纯黑 #000000（像素零自发光，省电），
 * 仅保留 1px 微流光线框与封面彩色弥散反光，作品如悬浮于深邃虚空。
 */
object ObsidianPureBlackEngine {

    private const val KEY_OLED_PURE_BLACK = "oled_pure_black"

    /** 是否开启 OLED 纯黑模式（仅在暗色主题下生效） */
    fun isEnabled(context: Context): Boolean =
        UserPreferencesManager.isThemeFlag(context, KEY_OLED_PURE_BLACK)

    /** 暗色主题 + OLED 开关同时满足时返回 true */
    fun shouldDrawPureBlack(context: Context): Boolean =
        isEnabled(context) && ThemeHelper.isDarkMode(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        UserPreferencesManager.setThemeFlag(context, KEY_OLED_PURE_BLACK, enabled)
    }
}
