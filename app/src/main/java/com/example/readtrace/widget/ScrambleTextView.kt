package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.sin
import kotlin.random.Random

/**
 * ✨ 全息字符流光解密过渡文本框 (ScrambleTextView)
 *
 * 灵感来源：awwwards.com / 21st.dev 赛博朋克字符解密与全息流光动效
 * 核心原理：
 * 1. 预置多重随机特殊符号矩阵（!<>-_\\/[]{}—=+*^?#ΑΒΓΔΩ0101）；
 * 2. 动画过程中根据当前进度，由左至右逐字固定为最终文本，并伴随引领光标；
 * 3. 实时全息彩虹流光着色器（Holographic LinearGradient Shader）在字面扫过；
 * 4. 支持点击交互触发二次解密。
 */
class ScrambleTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val glyphs = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!<>-_\\/[]{}—=+*^?#ΑΒΓΔΩ"
    private var targetString: String = ""
    private var animator: ValueAnimator? = null
    private var shimmerAnimator: ValueAnimator? = null

    var enableShimmer: Boolean = true
    private var shimmerTranslate: Float = 0f
    private var shimmerGradient: LinearGradient? = null
    private val gradientMatrix = Matrix()

    private val shimmerColors = intArrayOf(
        currentTextColor,
        Color.parseColor("#4DEEEA"), // 极光青
        Color.parseColor("#FFE700"), // 金曜黄
        Color.parseColor("#FF2A85"), // 霓虹粉
        currentTextColor,
    )

    init {
        setOnClickListener {
            triggerScramble()
        }
    }

    fun setScrambleText(newText: CharSequence, duration: Long = 480L) {
        val target = newText.toString()
        if (target == targetString && text == target) return

        targetString = target
        triggerScramble(duration)
    }

    fun triggerScramble(duration: Long = 480L) {
        animator?.cancel()
        val length = targetString.length
        if (length == 0) {
            text = ""
            return
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.1f)
            addUpdateListener { va ->
                val progress = va.animatedValue as Float
                val settledCharsCount = (progress * length).toInt()

                val sb = StringBuilder()
                for (i in 0 until length) {
                    val originalChar = targetString[i]
                    if (originalChar.isWhitespace()) {
                        sb.append(originalChar)
                    } else if (i < settledCharsCount) {
                        sb.append(originalChar)
                    } else if (i == settledCharsCount && progress < 0.95f) {
                        // 引领解密光标
                        sb.append("▓")
                    } else {
                        // 随机乱码字符
                        val randGlyph = glyphs[Random.nextInt(glyphs.length)]
                        sb.append(randGlyph)
                    }
                }
                text = sb.toString()
            }
        }
        animator?.start()
        startShimmerAnimation()
    }

    private fun startShimmerAnimation() {
        if (!enableShimmer) return
        shimmerAnimator?.cancel()
        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val p = va.animatedValue as Float
                val w = paint.measureText(targetString.ifBlank { text.toString() }).coerceAtLeast(100f)
                shimmerTranslate = -w + p * w * 2.5f
                invalidate()
            }
        }
        shimmerAnimator?.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            shimmerGradient = LinearGradient(
                0f, 0f, w.toFloat() * 0.7f, 0f,
                shimmerColors,
                floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = shimmerGradient
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (enableShimmer && shimmerGradient != null && shimmerAnimator?.isRunning == true) {
            gradientMatrix.setTranslate(shimmerTranslate, 0f)
            shimmerGradient?.setLocalMatrix(gradientMatrix)
            paint.shader = shimmerGradient
        } else {
            paint.shader = null
        }
        super.onDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        shimmerAnimator?.cancel()
    }
}
