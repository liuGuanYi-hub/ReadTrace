package com.example.readtrace.util

import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 🎞️ 全局动效令牌（Motion Tokens）
 *
 * ## 为什么需要这一层
 *
 * 收敛之前的全仓统计（按正则在 `*.kt` 里数硬编码时长与插值器，可复算）：
 *
 * - **83 处硬编码时长，38 个互不相关的取值**，横跨 16ms ~ 20000ms。
 *   其中 **60 处落在 ≤520ms 的交互手感区间**——本层要收敛的就是这 60 处（逐批迁移中）；
 *   另 **23 处 >520ms** 属于物理周期与常驻循环，不归本层管（见下文）。
 * - **16 处 Overshoot，用了 10 种张力**：1.1 / 1.15 / 1.2 / 1.3 / 1.4 / 1.6 / 1.7 / 2.0 / 2.2 / 2.4
 * - **17 处 Decelerate，用了 9 种张力**：0.8 / 1.1 / 1.2 / 1.3 / 1.4 / 1.5 / 1.6 / 1.8 / 2.0
 *
 * 这不是"数值多一点"，而是**没有词汇表**：每个调用点各自决定"多快算快、弹多少算弹"，
 * 于是同一个 App 里两个对话框的弹出节奏可能相差一倍，观感是"被累积出来的"
 * 而不是"被设计过的"。本对象把这件事收敛成一套有语义的档位。
 *
 * ## 怎么用
 *
 * ```kotlin
 * // 之前
 * view.animate().alpha(1f).setDuration(280L)
 *     .setInterpolator(DecelerateInterpolator(1.6f)).start()
 *
 * // 之后
 * view.animate().alpha(1f)
 *     .setDuration(MotionTokens.Duration.STANDARD)
 *     .setInterpolator(MotionTokens.Easing.ENTER).start()
 * ```
 *
 * 也提供 [apply] 这类薄封装，把"时长 + 曲线"这对常见组合一次设好。
 *
 * ## 档位怎么定的
 *
 * 每档都是**吸收现有值**得来（见各常量注释），不是凭空新造的数字。
 *
 * 但"归并"不等于"零位移"，这里如实记下代价：档位是 100/180/240/300/420，
 * 相邻间隔为 +80 / +60 / +60 / +120ms；绝大多数调用点被挪动 ≤50ms，
 * **已知唯一的超差项**是 `BackOrbView.playEnter()` 的入场：520→420ms（−100ms）
 * 且过冲张力 1.7→1.2。它同时改了两个维度，是全仓最需要真机回看的一处。
 *
 * ## 关于张力取整
 *
 * 把 1.15/1.3/1.7 这类中间值就近归入 1.2 或 2.2，是**有意的粗化**：
 * 宁可牺牲单点的手调手感，换"整个 App 只有两种过冲"的可预期性。
 * 因此迁移是"归并 + 少量收敛"，不要对外宣称"观感完全不变"。
 *
 * ## 关于过冲（Overshoot）
 *
 * 项目气质基线是「空灵 · 宁静 · 舒服」，[ViewAnimationHelper] 曾据此把通用按压缩放
 * 的回弹去掉（"过冲回弹属于「跳」的一类"）。但过冲并非一律禁止——
 * 它在**触感与惊喜**语境下是刻意的（撕票根、立体书、返回球的蓄力），
 * 被否掉的是"任何点击都弹一下"的滥用。
 *
 * 因此本层不封杀过冲，而是把它**限定在具名档位**里：
 * [Easing.EMPHASIS] 与 [Easing.PLAYFUL] 两档，张力有上界（≤2.2），
 * 且明确标注适用场景。想用过冲，就选这两档，不再各自手调 1.15/1.3/1.7/2.4。
 *
 * ## 不归本层管：物理周期与常驻循环
 *
 * 上面五档描述的是**交互反馈的快慢（手感）**。下面这些数字不是手感，
 * 而是**运动自身的物理参数或循环周期**，一律保持字面量。
 * 千万别为了"消灭魔法数字"把它们塞进档位——那会把 20 秒的极光压成 420ms，直接毁掉效果：
 *
 * - 逐帧推进 tick：`duration = 16L`（约 60fps 的一帧，粒子场、力导向布局用）
 * - 黑胶转速：3000ms/圈，由 33⅓ RPM 推得
 * - 极光流淌 / 胶片颗粒 / 星图漂移等常驻慢循环：1000~20000ms
 * - 音乐律动 450ms：对齐 120~140 BPM 的节拍周期
 * - `repeatCount = INFINITE` 的往复微光（如 700ms 扫光呼吸）
 *
 * 判据一句话：**改了这个数字，改变的是"物理量"还是"手感"？**
 * 是物理量就不属于这里。
 *
 * ## 与 QuietMode / 系统「移除动画」的关系
 *
 * 无需在调用点判断：`ViewPropertyAnimator` 与 `ValueAnimator` 底层的动画缩放
 * 由系统压制（设为 0 时时长自动归零）。本对象只负责"多快、什么曲线"，
 * 不负责"要不要动"——那是 [QuietMode] 的职责，两者边界不重叠。
 */object MotionTokens {

    /**
     * 时长档位。
     *
     * 命名按**意图**而非数值：写 `Duration.QUICK` 时读代码的人知道
     * "这是一次轻快反馈"，写 `180L` 则只能靠猜。
     */
    object Duration {
        /** 100ms —— 按下反馈、状态微调等"必须立刻"的动作。吸收 50/90/100ms */
        const val INSTANT = 100L

        /** 180ms —— 轻快反馈：淡入、列表项入场、按钮回弹。吸收 120/140/150/180ms */
        const val QUICK = 180L

        /** 240ms —— 默认档。绝大多数转场用它就对了。吸收 200/220/240/250ms */
        const val STANDARD = 240L

        /** 300ms —— 需要被"看见"的动作：指示器滑动、面板展开。吸收 260/280/300/320/340/350/360/380ms */
        const val EMPHASIS = 300L

        /**
         * 420ms —— 大块内容的仪式感入场：整页渐显、长卷揭幕。
         * 吸收 400/420/500/520ms。
         *
         * 注意这是**压得最狠的一档**：500/520ms 归到 420ms 是 −80~−100ms，
         * 全仓唯一的超差项（`BackOrbView.playEnter` 520→420）就在这里。
         * 若某个入场确实需要"更长"，说明它属于下面的物理周期范畴，不该塞进档位。
         */
        const val CINEMATIC = 420L
    }

    /**
     * 缓动曲线档位。
     *
     * 每个都是**单例**（object 持有），避免调用点每次新建对象——
     * 动画密集时可省下可观分配。
     */
    object Easing {
        /** 匀速。仅用于持续旋转（黑胶、磁带转轴）与扫描线等真正等速的运动 */
        val LINEAR: Interpolator = LinearInterpolator()

        /** 进场：快出慢入，用于"出现/展开"。默认张力 1.4，比裸默认更柔和 */
        val ENTER: Interpolator = DecelerateInterpolator(1.4f)

        /** 出场：慢出快离，用于"消失/收起" */
        val EXIT: Interpolator = DecelerateInterpolator(1.8f)

        /** 双向缓动：起步与收尾都平缓，适合位移类转场 */
        val STANDARD: Interpolator = AccelerateDecelerateInterpolator()

        /**
         * 克制过冲：轻微越界再回落（张力 1.2）。
         * 用于需要一点"活气"但不可轻浮的动作：指示胶囊吸附、卡片复位的收尾。
         */
        val EMPHASIS: Interpolator = OvershootInterpolator(1.2f)

        /**
         * 明显过冲（张力 2.2）。**仅在触感/惊喜语境使用**：
         * 撕票根、立体书弹出、返回球蓄力回弹。
         * 不要用在常规页面切换或普通点击上——那正是 [ViewAnimationHelper] 当年去掉回弹的原因。
         */
        val PLAYFUL: Interpolator = OvershootInterpolator(2.2f)
    }

    /**
     * 常用组合：把"时长 + 曲线"一次设好，避免调用点重复写两行。
     *
     * @param duration 取自 [Duration]
     * @param easing 取自 [Easing]
     */
    fun apply(animator: android.animation.ValueAnimator, duration: Long, easing: Interpolator) {
        animator.duration = duration
        animator.interpolator = easing
    }

    /** [android.view.ViewPropertyAnimator] 版本：[apply] 的链式等价封装 */
    fun apply(
        animator: android.view.ViewPropertyAnimator,
        duration: Long,
        easing: Interpolator,
    ): android.view.ViewPropertyAnimator = animator.setDuration(duration).setInterpolator(easing)
}
