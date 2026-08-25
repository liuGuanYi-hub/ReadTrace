package com.example.readtrace.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 🎧 触觉马达振动引擎 (HapticFeedbackEngine)
 *
 * 核心特性：
 * 1. 深度适配 Android 12+ (API 31+ VibratorManager)、Android 10+ (API 29+ Predefined) 与低版本回退；
 * 2. 精准波形振幅控制：
 *    - 护照盖印：沉重印章打击 + 回弹余震；
 *    - 电影撕票：齿孔连续撕裂的机械顿挫感；
 *    - 拟真翻书：纸张滑过指尖的轻柔沙沙感；
 *    - 黑胶落针：针尖接触盘面的双重微颤；
 *    - 卡带插入：金属弹片卡扣清脆弹跳；
 *    - 星系引力：深沉平缓的引力波脉冲。
 */
object HapticFeedbackEngine {

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * 1. 🛂 护照盖印打击感 (重锤落印 + 细微回弹共振)
     */
    fun stampImpact(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 45, 25, 20)
            val amplitudes = intArrayOf(0, 255, 0, 90)
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(55, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(55L)
        }
    }

    /**
     * 2. 🎟️ 电影票撕孔齿轮感 (连续 4 次微型高频脉冲，模拟打孔线逐个断裂)
     */
    fun ticketTearRipped(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 12, 16, 14, 18, 16, 20, 22)
            val amplitudes = intArrayOf(0, 120, 0, 160, 0, 200, 0, 240)
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(35L)
        }
    }

    /**
     * 3. 📖 拟真纸张翻页摩擦感 (极轻柔微触感)
     */
    fun pageTurnRustle(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(18, 60))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(18L)
        }
    }

    /**
     * 4. 💽 黑胶唱臂落针微颤 (针尖触盘微震)
     */
    fun needleDropCrackle(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 20, 15, 12)
            val amplitudes = intArrayOf(0, 180, 0, 110)
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30L)
        }
    }

    /**
     * 5. 🕹️ 游戏卡带卡扣弹跳感 (双重咔哒卡入)
     */
    fun cartridgeSnap(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 22, 18, 30)
            val amplitudes = intArrayOf(0, 160, 0, 255)
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(45L)
        }
    }

    /**
     * 6. 🌌 心智星系引力波脉冲 (深沉低频连线涌动)
     */
    fun celestialResonancePulse(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 30, 20, 50, 30, 25)
            val amplitudes = intArrayOf(0, 90, 0, 190, 0, 80)
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(60L)
        }
    }

    /**
     * 7. 通用清脆微点击 (Light Click)
     */
    fun lightClick(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20L)
        }
    }
}
