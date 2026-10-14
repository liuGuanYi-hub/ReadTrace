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
 * 收敛之前，全仓有 **47 处 `setDuration`，用了 17 个互不相关的时长值**
 * （50/90/100/120/150/180/200/220/240/250/260/280/300/350/400/420/520ms），
 * 缓动曲线散落 **10 种 Overshoot 张力 + 9 种 Decelerate 张力 + 13 处默认无参**。
 *
 * 这不是"数值多一点"，而是**没有词汇表**：每个调用点各自决定"多快算快"，
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
 * 每档都是**吸收现有值**得来（见各常量注释），不是凭空新造的数字，
 * 因此迁移是"归并"而非"改观感"：相邻档位差不超过 ~60ms，
 * 单点变化幅度小到肉眼难以分辨，但整体节奏变得一致。
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

        /** 180ms —— 轻快反馈：淡入、列表项入场、按钮回弹。吸收 120/150/180ms */
        const val QUICK = 180L

        /** 240ms —— 默认档。绝大多数转场用它就对了。吸收 200/220/240/250ms */
        const val STANDARD = 240L

        /** 300ms —— 需要被"看见"的动作：指示器滑动、面板展开。吸收 260/280/300/350ms */
        const val EMPHASIS = 300L

        /** 420ms —— 大块内容的仪式感入场：整页渐显、长卷揭幕。吸收 400/420/520ms */
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
