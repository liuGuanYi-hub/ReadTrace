package com.example.readtrace.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.readtrace.MainActivity
import com.example.readtrace.R
import com.example.readtrace.util.HapticFeedbackEngine

/**
 * 🏛️ 极简画刊 · 人文微缩呼吸启动揭幕系统 (Editorial Magazine Splash Overlay)
 *
 * 设计灵感：SiteInspire 瑞士画刊版式 + OnePageLove 纯粹克制美学
 *
 * 核心动效：
 * 1. 衬线大字 R E A D T R A C E 呼吸式字距展开（Tracking Expansion：letterSpacing 0.15f -> 0.42f）
 * 2. 黄金比例极细微光分割线动态生长（0dp -> 44dp）
 * 3. 随机浮现博尔赫斯/马尔克斯/普鲁斯特等文学名家关于“记忆、痕迹与精神世界”的传世箴言（Y 轴上浮渐显）
 * 4. 拟真纸张微震触觉联动（HapticFeedbackEngine.pageTurnRustle）
 * 5. 画刊卡片慢镜头级 Ken Burns 微幅推进（1.000 -> 1.035）
 * 6. 纸页消散揭幕（Dissolve Fade Out），平滑展露已在后台静默预加载就绪的展馆主界面
 * 7. 全流程可轻触瞬切跳过（Tap-to-skip）与单次进程冷启动守卫
 */
object EditorialSplashOverlay {

    /** 记录单次 App 进程是否已展示过开屏，避免从后台切回或跳转返回时重复打扰 */
    @Volatile
    private var hasShownThisProcess = false

    /**
     * 重置冷启动标记（主要用于单元测试或特定预览场景）
     */
    fun resetProcessStateForTesting() {
        hasShownThisProcess = false
    }

    data class EditorialQuote(
        val text: String,
        val author: String
    )

    /** 精选名家关于“书籍、记忆、痕迹与数字花园”的经典金句池 */
    private val CURATED_QUOTES = listOf(
        EditorialQuote("“我心里一直在暗暗设想，天堂应该是图书馆的模样。”", "—— 豪尔赫·路易斯·博尔赫斯"),
        EditorialQuote("“生活不是我们活过的日子，而是我们记住的日子。”", "—— 加西亚·马尔克斯"),
        EditorialQuote("“真正的发现之旅不在于寻找新大陆，而在于拥有新视野。”", "—— 马塞尔·普鲁斯特"),
        EditorialQuote("“岁月不饶人，我亦未曾饶过岁月。”", "—— 木心"),
        EditorialQuote("“你喜欢一个城市的理由，在于它对你的问题所作的回答。”", "—— 伊塔洛·卡尔维诺"),
        EditorialQuote("“我的心是一座隐秘的博物馆，存放着所有未曾说出的痕迹。”", "—— 费尔南多·佩索阿"),
        EditorialQuote("“在书里，我们可以把消逝的时光找回来。”", "—— 弗吉尼亚·伍尔夫"),
        EditorialQuote("“每一个人的故事都是重要的、永恒的、神圣的。”", "—— 赫尔曼·黑塞"),
        EditorialQuote("“读过的每一页，都会在某个阶段留下痕迹。”", "—— 阅痕 · 数字策展"),
        EditorialQuote("“时间是把我带走的河流，但我就是那河流。”", "—— 豪尔赫·路易斯·博尔赫斯"),
        EditorialQuote("“世界上只有一种真正的英雄主义，那就是认清生活的真相后依然热爱生活。”", "—— 罗曼·罗兰"),
        EditorialQuote("“宇宙即是一本书，我们皆是翻动书页的人。”", "—— 翁贝托·埃科")
    )

    /**
     * 在 MainActivity 满足冷启动条件时触发画刊揭幕动效
     */
    fun showIfColdLaunch(
        activity: AppCompatActivity,
        root: ViewGroup,
        savedInstanceState: Bundle?,
        onDismiss: (() -> Unit)? = null
    ) {
        // 1. 若非冷启动、由屏幕旋转/配置变更重建，或从外部特定 Tab 深链接打开，则直接旁路
        if (hasShownThisProcess || savedInstanceState != null) {
            onDismiss?.invoke()
            return
        }

        val targetTab = activity.intent?.getIntExtra(MainActivity.EXTRA_TAB_INDEX, -1) ?: -1
        if (targetTab in 0..4 && targetTab != MainActivity.TAB_HUB) {
            // 外部显式指定非首页 Tab（如直达书架/回忆录），直接放行
            onDismiss?.invoke()
            return
        }

        hasShownThisProcess = true

        val decorView = activity.window.decorView as? ViewGroup
        val targetContainer = decorView ?: root
        val inflater = activity.layoutInflater
        val overlayView = inflater.inflate(R.layout.layout_editorial_splash_overlay, targetContainer, false)

        // 适配全屏 Edge-to-Edge 避让系统状态栏与手势底栏
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(overlayView) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        targetContainer.addView(
            overlayView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val card = overlayView.findViewById<View>(R.id.editorialSplashCard)
        val title = overlayView.findViewById<TextView>(R.id.editorialSplashTitle)
        val divider = overlayView.findViewById<View>(R.id.editorialSplashDivider)
        val quoteContainer = overlayView.findViewById<View>(R.id.editorialSplashQuoteContainer)
        val quoteText = overlayView.findViewById<TextView>(R.id.editorialSplashQuoteText)
        val quoteAuthor = overlayView.findViewById<TextView>(R.id.editorialSplashQuoteAuthor)
        val skipHint = overlayView.findViewById<View>(R.id.editorialSplashSkip)

        // 随机注入文学箴言
        val quote = CURATED_QUOTES.random()
        quoteText.text = quote.text
        quoteAuthor.text = quote.author

        // 初始视觉状态设定
        overlayView.alpha = 1f
        card.scaleX = 1.0f
        card.scaleY = 1.0f

        title.alpha = 0f
        title.letterSpacing = 0.15f
        title.translationY = -14f

        val density = activity.resources.displayMetrics.density
        val targetDividerWidth = (44 * density).toInt()
        val dividerLp = divider.layoutParams
        dividerLp.width = 0
        divider.layoutParams = dividerLp

        quoteContainer.alpha = 0f
        quoteContainer.translationY = 26f
        skipHint.alpha = 0f

        val mainHandler = Handler(Looper.getMainLooper())
        var isDismissed = false
        val runningAnimators = mutableListOf<Animator>()

        fun dismissOverlay(immediate: Boolean) {
            if (isDismissed) return
            isDismissed = true

            // 停止所有未完动效
            runningAnimators.forEach { it.cancel() }
            runningAnimators.clear()
            mainHandler.removeCallbacksAndMessages(null)

            val fadeDuration = if (immediate) 150L else 320L
            overlayView.animate()
                .alpha(0f)
                .scaleX(1.035f)
                .scaleY(1.035f)
                .setDuration(fadeDuration)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    (overlayView.parent as? ViewGroup)?.removeView(overlayView) ?: root.removeView(overlayView)
                    onDismiss?.invoke()
                }
                .start()
        }

        // 轻触任意区域瞬切跳过，杜绝拖慢记录心流
        overlayView.setOnClickListener {
            dismissOverlay(immediate = true)
        }

        // 1. 核心字标动态舒展展开动画（letterSpacing 0.15f -> 0.42f）
        val letterSpacingAnim = ValueAnimator.ofFloat(0.15f, 0.42f).apply {
            duration = 850L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                val value = it.animatedValue as Float
                title.letterSpacing = value
            }
        }
        val titleAlphaAnim = ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f).apply {
            duration = 600L
        }
        val titleTransAnim = ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, -14f, 0f).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
        }

        // 2. 黄金比例细线宽度延展（0dp -> 44dp）
        val dividerAnim = ValueAnimator.ofInt(0, targetDividerWidth).apply {
            duration = 550L
            startDelay = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val lp = divider.layoutParams
                lp.width = it.animatedValue as Int
                divider.layoutParams = lp
            }
        }

        // 3. 人文名家金句上浮渐显
        val quoteAlphaAnim = ObjectAnimator.ofFloat(quoteContainer, View.ALPHA, 0f, 1f).apply {
            duration = 650L
            startDelay = 320L
        }
        val quoteTransAnim = ObjectAnimator.ofFloat(quoteContainer, View.TRANSLATION_Y, 26f, 0f).apply {
            duration = 650L
            startDelay = 320L
            interpolator = DecelerateInterpolator()
        }

        // 4. Ken Burns 卡片慢镜头级微推进（1.000 -> 1.035）
        val kenBurnsX = ObjectAnimator.ofFloat(card, View.SCALE_X, 1.0f, 1.035f).apply {
            duration = 1400L
            interpolator = LinearInterpolator()
        }
        val kenBurnsY = ObjectAnimator.ofFloat(card, View.SCALE_Y, 1.0f, 1.035f).apply {
            duration = 1400L
            interpolator = LinearInterpolator()
        }

        // 5. 底部跳过指引微显
        val skipAnim = ObjectAnimator.ofFloat(skipHint, View.ALPHA, 0f, 0.38f).apply {
            duration = 400L
            startDelay = 500L
        }

        val animSet = AnimatorSet().apply {
            playTogether(
                letterSpacingAnim,
                titleAlphaAnim,
                titleTransAnim,
                dividerAnim,
                quoteAlphaAnim,
                quoteTransAnim,
                kenBurnsX,
                kenBurnsY,
                skipAnim
            )
        }

        overlayView.post {
            if (isDismissed || activity.isFinishing || activity.isDestroyed) return@post

            runningAnimators.add(animSet)
            animSet.start()

            // 在动效黄金点（~460ms，字距舒展成型时）触发拟真纸张微震触感
            mainHandler.postDelayed({
                if (!isDismissed && !activity.isFinishing && !activity.isDestroyed) {
                    HapticFeedbackEngine.pageTurnRustle(activity)
                }
            }, 460L)

            // 达到预设最佳观赏时长（1800ms）后自动平滑揭幕
            mainHandler.postDelayed({
                dismissOverlay(immediate = false)
            }, 1800L)
        }
    }
}
