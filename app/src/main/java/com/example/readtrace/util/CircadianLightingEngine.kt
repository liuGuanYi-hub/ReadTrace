package com.example.readtrace.util

import android.graphics.Color
import java.util.Calendar

/**
 * 🌌 昼夜节律四时环境光与声光共鸣引擎 (CircadianLightingEngine)
 *
 * P6 阶段四核心引擎：
 * 1. 24 小时昼夜四时自适应色温（Circadian Color Temperature）：
 *    - 🌅 晨曦 (06:00~09:30)：薄雾青金与晨露暖金（唤醒专注）；
 *    - ☀️ 晴午 (09:30~16:30)：清透碧翠与日光白金（清爽明亮）；
 *    - 🌆 暮霞 (16:30~19:30)：落日珊瑚与暮色深紫（沉浸治愈）；
 *    - 🌌 极夜 (19:30~06:00)：深邃曜石与星云冰蓝（护眼深沉）。
 * 2. 低频音频反应式流光共鸣（Audio-Reactive Luminescence）：
 *    - 随着黑胶/磁带/播客播放的低频能量，动态调制光斑半径与呼吸光强。
 */
object CircadianLightingEngine {

    enum class CircadianPhase(
        val displayName: String,
        val icon: String,
        val description: String,
    ) {
        DAWN("晨曦", "🌅", "晨曦初露 · 薄雾青金"),
        NOON("晴午", "☀️", "正午晴空 · 翡翠白金"),
        DUSK("暮霞", "🌆", "暮霞落日 · 珊瑚幻紫"),
        MIDNIGHT("极夜", "🌌", "极夜星穹 · 曜石冰蓝");
    }

    fun getCurrentPhase(calendar: Calendar = Calendar.getInstance()): CircadianPhase {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val timeFraction = hour + minute / 60.0f

        return when {
            timeFraction in 6.0f..9.5f -> CircadianPhase.DAWN
            timeFraction in 9.5f..16.5f -> CircadianPhase.NOON
            timeFraction in 16.5f..19.5f -> CircadianPhase.DUSK
            else -> CircadianPhase.MIDNIGHT
        }
    }

    fun getCircadianColors(phase: CircadianPhase = getCurrentPhase(), isDark: Boolean = true): IntArray {
        return if (isDark) {
            when (phase) {
                CircadianPhase.DAWN -> intArrayOf(
                    Color.parseColor("#5A1A3B5C"), // 薄雾青蓝
                    Color.parseColor("#5AE0A96D"), // 晨曦金曜
                    Color.parseColor("#4D2D5A46"), // 晨露草木
                    Color.parseColor("#404E3524"), // 暖木
                )
                CircadianPhase.NOON -> intArrayOf(
                    Color.parseColor("#5A1D4E33"), // 沉静幽绿
                    Color.parseColor("#5A2E5B70"), // 晴空天蓝
                    Color.parseColor("#4D3E7B5A"), // 翡翠透光
                    Color.parseColor("#40FAF6F0"), // 白金高光
                )
                CircadianPhase.DUSK -> intArrayOf(
                    Color.parseColor("#663B134D"), // 暮色深紫
                    Color.parseColor("#66FF758F"), // 珊瑚粉霞
                    Color.parseColor("#5A7B2CBF"), // 幻境紫罗兰
                    Color.parseColor("#4DFF5400"), // 落日余晖
                )
                CircadianPhase.MIDNIGHT -> intArrayOf(
                    Color.parseColor("#66090A10"), // 曜石深渊
                    Color.parseColor("#5A141E30"), // 深邃夜幕
                    Color.parseColor("#5A00F5D4"), // 极光冰蓝
                    Color.parseColor("#4D3E3159"), // 星云幻紫
                )
            }
        } else {
            when (phase) {
                CircadianPhase.DAWN -> intArrayOf(
                    Color.parseColor("#52E2D6B5"), // 晨曦浅金
                    Color.parseColor("#529BB7D4"), // 薄雾霁蓝
                    Color.parseColor("#40E2BAC6"), // 晨露浅粉
                    Color.parseColor("#528FB399"), // 柔和草木
                )
                CircadianPhase.NOON -> intArrayOf(
                    Color.parseColor("#5290B59B"), // 柔和草木绿
                    Color.parseColor("#5288D49E"), // 翡翠清光
                    Color.parseColor("#40C8E6C9"), // 嫩芽浅绿
                    Color.parseColor("#52A0BBD8"), // 晴空霁蓝
                )
                CircadianPhase.DUSK -> intArrayOf(
                    Color.parseColor("#5AEDB5B5"), // 晚霞暖橘
                    Color.parseColor("#5AD8B4E2"), // 暮色紫藤
                    Color.parseColor("#52FFB5A7"), // 珊瑚晚霞
                    Color.parseColor("#40FEC89A"), // 落日金黄
                )
                CircadianPhase.MIDNIGHT -> intArrayOf(
                    Color.parseColor("#52A0BBD8"), // 霁蓝夜空
                    Color.parseColor("#52C4B5E2"), // 浅紫星光
                    Color.parseColor("#4099D6EA"), // 冰川浅蓝
                    Color.parseColor("#40B8C0D4"), // 银月微光
                )
            }
        }
    }
}
