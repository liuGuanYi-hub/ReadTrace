package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import kotlin.random.Random

/**
 * ✨ 全息字符流光解密过渡文本框 (ScrambleTextView)
 *
 * 灵感来源：awwwards.com / 21st.dev 赛博朋克字符解密过渡动效
 * 核心原理：
 * 1. 预置多重随机特殊符号池（!<>-_\\/[]{}—=+*^?#________0101）；
 * 2. 动画过程中根据当前进度，由左至右逐字固定为最终文本；
 * 3. 正在解密中的字位高频随机跳变字符，营造出全息终端数据流光汇聚的科幻感。
 */
class ScrambleTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val glyphs = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!<>-_\\/[]{}—=+*^?#"
    private var targetString: String = ""
    private var animator: ValueAnimator? = null

    fun setScrambleText(newText: CharSequence, duration: Long = 420L) {
        val target = newText.toString()
        if (target == targetString && text == target) return

        targetString = target
        animator?.cancel()

        val length = targetString.length
        if (length == 0) {
            text = ""
            return
        }

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = LinearInterpolator()
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
    }
}
