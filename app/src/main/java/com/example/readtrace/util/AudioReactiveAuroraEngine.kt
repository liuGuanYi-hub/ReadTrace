package com.example.readtrace.util

import android.animation.ValueAnimator
import android.content.Context
import kotlin.random.Random

/**
 * 🔊 音频低频反应式极光脉冲引擎 (Audio-Reactive Aurora Pulse Engine)
 * 对标 Teenage Engineering 极客硬件与 Landing.love 声光反应美学：
 * - 实时监测/模拟音乐音频频段（Bass 低频 20Hz~250Hz、Mid 人声温暖度、High 瞬态）；
 * - 驱动背景流体极光网格与卡片白金内倒角光芒产生呼吸脉冲；
 * - 与 P1 黑胶唱机/卡座（夜鹿、真夜中曲目）形成物理共振。
 */
object AudioReactiveAuroraEngine {

    private var pulseAnimator: ValueAnimator? = null
    private var isPlayingAudio = false
    private var currentBassLevel = 0f

    var onBassPulseListener: ((bassLevel: Float) -> Unit)? = null

    /**
     * 开始随着音乐节奏产生低频声光脉冲
     */
    fun startAudioSync() {
        if (isPlayingAudio) return
        isPlayingAudio = true

        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L // 对应典型 120~140 BPM 音乐节拍
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                if (isPlayingAudio) {
                    val progress = anim.animatedValue as Float
                    // 模拟真实音乐鼓点与低频动态起伏
                    val noise = Random.nextFloat() * 0.15f
                    currentBassLevel = (progress * 0.55f + noise).coerceIn(0f, 0.75f)
                    onBassPulseListener?.invoke(currentBassLevel)
                }
            }
            start()
        }
    }

    /**
     * 暂停声光脉冲
     */
    fun stopAudioSync() {
        isPlayingAudio = false
        pulseAnimator?.cancel()
        pulseAnimator = null
        currentBassLevel = 0f
        onBassPulseListener?.invoke(0f)
    }

    fun getCurrentBassLevel(): Float = currentBassLevel
}
