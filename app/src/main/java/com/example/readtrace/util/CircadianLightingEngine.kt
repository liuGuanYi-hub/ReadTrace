package com.example.readtrace.util

import android.content.Context
import android.graphics.Color
import java.util.Calendar

/**
 * 🌅 24h 昼夜四时自适应自然光色温系统 (Circadian Rhythm Lighting Engine)
 * 对标 Stripe Press / Cosmos / Apple 自然光感哲学：
 * - 根据用户所在地的真实系统时间自适应平滑漫射四时环境光晕；
 * - 四大时段：
 *   1. 🌅 清晨 DAWN (06:00 ~ 09:00)：晨曦温金与淡水蓝漫射 (唤醒感)
 *   2. ☀️ 正午 SOLAR (09:00 ~ 17:00)：高透纯白与莫兰迪灰青 (通透理智)
 *   3. 🌆 暮色 TWILIGHT (17:00 ~ 20:00)：紫霞暮色与落日橙金 (沉浸浪漫)
 *   4. 🌌 子夜 MIDNIGHT (20:00 ~ 06:00)：深邃曜黑、夜鹿靛青与极光青 (暗夜漫想)
 */
object CircadianLightingEngine {

    enum class CircadianPhase(
        val displayName: String,
        val emoji: String,
        val primaryColorHex: String,
        val secondaryColorHex: String,
        val ambientGlowHex: String,
    ) {
        DAWN("晨曦薄雾", "🌅", "#FFE3A8", "#70C1B3", "#20FFE3A8"),
        SOLAR("白昼正午", "☀️", "#E8F0FE", "#4DEEEA", "#204DEEEA"),
        TWILIGHT("落日紫霞", "🌆", "#FF6F59", "#845EC2", "#25FF6F59"),
        MIDNIGHT("子夜星河", "🌌", "#4DEEEA", "#2A3A5E", "#154DEEEA"),
    }

    /**
     * 获取当前系统时间对应的四时时相
     */
    fun getCurrentPhase(): CircadianPhase {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..8 -> CircadianPhase.DAWN
            in 9..16 -> CircadianPhase.SOLAR
            in 17..19 -> CircadianPhase.TWILIGHT
            else -> CircadianPhase.MIDNIGHT
        }
    }

    /**
     * 获取当前四时的极光主色与辅色渐变
     */
    fun getCircadianGradientColors(phase: CircadianPhase = getCurrentPhase(), isDark: Boolean = false): IntArray =
        getCircadianColors(phase, isDark)

    fun getCircadianColors(phase: CircadianPhase = getCurrentPhase(), isDark: Boolean = false): IntArray {
        val c1 = Color.parseColor(phase.primaryColorHex)
        val c2 = Color.parseColor(phase.secondaryColorHex)
        return if (isDark) {
            intArrayOf(
                c1,
                c2,
                Color.parseColor("#152238"),
                Color.parseColor("#0A0F1A"),
            )
        } else {
            intArrayOf(
                c1,
                c2,
                Color.parseColor("#E8F0FE"),
                Color.parseColor("#F8F9FA"),
            )
        }
    }

    /**
     * 获取四时环境光晕描述文案
     */
    fun getCircadianSummary(): String {
        val phase = getCurrentPhase()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val minute = Calendar.getInstance().get(Calendar.MINUTE)
        val timeStr = String.format("%02d:%02d", hour, minute)
        return "${phase.emoji} ${phase.displayName} · $timeStr 四时自然光感校准"
    }
}
