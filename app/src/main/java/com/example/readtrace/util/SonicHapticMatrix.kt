package com.example.readtrace.util

import android.content.Context

/**
 * 🎧 全场景 ASMR 拟音与触觉联觉矩阵 (Sonic-Haptic Synesthesia Matrix)
 * 对标 Landing.love 声音动效与 Teenage Engineering 拟物声场设计：
 * - 将视觉微动效、空间音频（Spatial Audio）与线性马达触觉（Haptics）毫秒级同步绑定；
 * - 覆盖场景：
 *   1. 📜 羊皮纸翻页与便签摩擦 (Parchment Rustle ASMR)；
 *   2. 🎴 文化护照盖印与火漆封蜡沉击 (Wax Seal Thud ASMR)；
 *   3. 🎟️ 电影票打孔线脆裂撕开 (Ticket Perforation Rip ASMR)；
 *   4. 🕹️ 白金游戏实体卡带卡扣插槽 (Cartridge Snap ASMR)。
 */
object SonicHapticMatrix {

    /**
     * 📜 羊皮纸翻折与便签沙沙摩擦拟音
     */
    fun playParchmentRustle(context: Context) {
        HapticFeedbackEngine.lightClick(context)
        SpatialAudioEngine.playPageTurn()
    }

    /**
     * 🎴 文化护照盖印与火漆封蜡重沉击音
     */
    fun playWaxSealThud(context: Context) {
        HapticFeedbackEngine.stampImpact(context)
        SpatialAudioEngine.playStampThud()
        ConfettiBurstHelper.burstCenter(context as? android.app.Activity ?: return)
    }

    /**
     * 🎟️ 电影票打孔线物理撕裂脆裂音
     */
    fun playTicketPerforationRip(context: Context) {
        HapticFeedbackEngine.ticketTearRipped(context)
        SpatialAudioEngine.playTicketTear()
        ConfettiBurstHelper.burstCenter(context as? android.app.Activity ?: return)
    }

    /**
     * 🕹️ 白金游戏卡带插槽清脆机械回响
     */
    fun playCartridgeSnap(context: Context) {
        HapticFeedbackEngine.cartridgeSnap(context)
        SpatialAudioEngine.playCartridgeSnap()
        ConfettiBurstHelper.burstCenter(context as? android.app.Activity ?: return)
    }
}
