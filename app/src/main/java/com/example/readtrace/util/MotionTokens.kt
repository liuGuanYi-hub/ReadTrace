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
 *   其中 **60 处落在 ≤520ms 的交互手感区间**——本层收敛的就是这 60 处，现已全部归入档位；
 *   迁移后仓库里剩余的硬编码时长只有 16 处，且**全部**属于下文的物理周期与等速运镜类。
 *   >520ms 里那些一次性揭幕（550/600/650/850/900）由 [Duration.SEQUENCE]
 *   与 [Duration.CELEBRATION] 承接，并未当作物理量放过。
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
 * 归档规则只有一条：**就近取档，平局取更快的一档**。
 * 因此 140→INSTANT(100)、360→EMPHASIS(300) 这两处平局都落在了快的一侧。
 *
 * "归并"不等于"零位移"，这里如实记下实测代价（逐对比较迁移前后同一调用点的数字）：
 * 60 个可配对调用点中 **20 个原值本就等于档位值（零变化）**，57 个位移 ≤60ms，
 * 中位数 30ms，**最大 80ms**；超出 60ms 的 3 处全部落在 420↔600 这条缝上
 * （返回球入场 520→600、立体书复位 500→420、撕票根 520→600）。
 * 这不是巧合：420 与 600 之间隔着 180ms，缝里的任何数怎么取都至少偏 80ms。
 * 若这个区间观感确实不对，正解是**加一档**（而不是回到散装数字）。
 *
 * 复算方式：对 `git diff 0a04c68^` 逐配对提取"删掉的字面量 / 换上的档位"即可，
 * 别把上面的数字当感觉——它们是可验证的。
 *
 * ## 关于张力取整
 *
 * 把 1.15/1.3/1.7 这类中间值就近归入 1.2 或 2.2，是**有意的粗化**：
 * 宁可牺牲单点的手调手感，换"整个 App 只有两种过冲"的可预期性。
 * 因此迁移是"归并 + 少量收敛"，不要对外宣称"观感完全不变"。
 *
 * 迁移后的实测残留：**全仓仅剩 1 处手调张力**，即
 * `ThemeTransitionPlayer` 的 `DecelerateInterpolator(0.8f)`。它比裸默认（1.0）还平缓，
 * 用在圆形开洞揭幕上；套 [Easing.ENTER](1.4) 会让洞"冲到一半就刹住"，
 * 所以作为已核实的例外保留，而不是遗漏。
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
 * 上面七档描述的是**交互反馈的快慢（手感）**。下面这些数字不是手感，
 * 而是**运动自身的物理参数或循环周期**，一律保持字面量。
 * 千万别为了"消灭魔法数字"把它们塞进档位——那会把 20 秒的极光压成 420ms，直接毁掉效果：
 *
 * - 逐帧推进 tick：`duration = 16L`（约 60fps 的一帧，粒子场、力导向布局用）
 * - 黑胶转速：3000ms/圈，由 33⅓ RPM 推得
 * - 边框流光绕行一周：3200ms（`BorderBeamFrameLayout.beamDuration`，INFINITE 循环的周期）
 * - 主题开洞揭幕：700ms（`ThemeTransitionPlayer.HOLE_EXPAND_DURATION_MS`，
 *   该常量还参与 `PENDING_TIMEOUT_MS + HOLE_EXPAND_DURATION_MS` 的超时算式，
 *   改它等于改超时，不只是改手感）
 * - 极光流淌 / 胶片颗粒 / 星图漂移等常驻慢循环：1000~20000ms
 * - 音乐律动 450ms：对齐 120~140 BPM 的节拍周期
 * - `repeatCount = INFINITE` 的往复微光（如 700ms 扫光呼吸）
 * - **等速运镜（LinearInterpolator）的位移量**：开场遮罩 Ken Burns 微推进 1400ms、
 *   全息评分的彩虹扫光 1200ms、文字解码的高光扫过 900ms。
 *   它们配的是 [Easing.LINEAR]，时长决定的是"扫过一遍有多快"，属于物理量而非手感。
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
        /**
         * 100ms —— 按下反馈、状态微调等"必须立刻"的动作。
         * 实测吸收 90/120/140ms；表单抖动的半个周期用 `INSTANT / 2`（= 原 50ms）。
         */
        const val INSTANT = 100L

        /** 180ms —— 轻快反馈：淡入、列表项入场、按钮回弹。实测吸收 150/180ms */
        const val QUICK = 180L

        /** 240ms —— 默认档。绝大多数转场用它就对了。实测吸收 200/220/240/250ms */
        const val STANDARD = 240L

        /**
         * 300ms —— 需要被"看见"的动作：指示器滑动、面板展开、列表项错峰入场。
         * 实测吸收 260/280/300/320/340/350/360ms（360 与 420 平局，按"平局取快"落在这里）。
         */
        const val EMPHASIS = 300L

        /**
         * 420ms —— 大块内容的仪式感入场：整页渐显、长卷揭幕、解码动画的默认时长。
         * 实测吸收 380/400/420/450/480/500ms。
         */
        const val CINEMATIC = 420L

        /**
         * 600ms —— 一次性揭幕：整块内容"被展开"，比 CINEMATIC 更需要读完。
         * 实测吸收 520/550/600/650ms；这是全仓复用面最广的一批（6 个互不相关的组件在此区间）。
         */
        const val SEQUENCE = 600L

        /**
         * 900ms —— 庆祝与揭示：评分数字滚动、唱臂落片这类"结果值得被看完"的动作。
         * 实测吸收 850/900ms。
         */
        const val CELEBRATION = 900L
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
