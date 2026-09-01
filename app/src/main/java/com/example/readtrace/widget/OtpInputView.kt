package com.example.readtrace.widget

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.readtrace.R
import com.example.readtrace.util.HapticFeedbackEngine

/**
 * 6 位分离式验证码输入方格
 *
 * 实现要点：真正接收键盘输入的是一个 1dp 的透明 EditText，
 * 六个方格只是它的「显示器」。这样可以直接复用系统的软键盘、
 * 退格、粘贴和输入法候选逻辑，不需要自己实现一整套按键分发，
 * 也不会出现自定义 View 在部分输入法下收不到按键的问题。
 */
class OtpInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 验证码填满时的回调 */
    var onComplete: ((String) -> Unit)? = null

    /** 内容变化时的回调 */
    var onChanged: ((String) -> Unit)? = null

    /** 方格数量 */
    var codeLength: Int = 6
        set(value) {
            if (value <= 0) return
            field = value
            rebuildBoxes()
        }

    private val boxes = mutableListOf<TextView>()
    private lateinit var boxContainer: LinearLayout
    private lateinit var hiddenInput: EditText

    private var isErrorState = false
    private var suppressRender = false

    init {
        buildViews()
        rebuildBoxes()
    }

    private fun buildViews() {
        boxContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        addView(boxContainer, LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(54)))

        // 承载键盘输入的隐藏输入框
        hiddenInput = EditText(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.TRANSPARENT)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            isCursorVisible = false
            // 关闭系统自动填充，避免和自定义方格渲染互相打架
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        addView(hiddenInput, LayoutParams(dpToPx(1), dpToPx(1)).apply {
            gravity = Gravity.CENTER
        })

        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (suppressRender) return
                val raw = s?.toString() ?: ""
                val digits = raw.filter { it.isDigit() }

                // 过滤非法字符或截断超长输入时，回写并让光标停在末尾
                if (digits != raw || digits.length > codeLength) {
                    val corrected = digits.take(codeLength)
                    suppressRender = true
                    hiddenInput.setText(corrected)
                    hiddenInput.setSelection(corrected.length)
                    suppressRender = false
                    render(corrected)
                    onChanged?.invoke(corrected)
                    if (corrected.length == codeLength) onComplete?.invoke(corrected)
                    return
                }

                render(digits)
                onChanged?.invoke(digits)
                if (digits.length == codeLength) {
                    HapticFeedbackEngine.lightClick(context)
                    onComplete?.invoke(digits)
                }
            }
        })

        setOnClickListener { requestInputFocus() }
    }

    private fun rebuildBoxes() {
        boxContainer.removeAllViews()
        boxes.clear()
        val gap = dpToPx(8)
        for (i in 0 until codeLength) {
            val box = TextView(context).apply {
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.readtrace_ink))
                background = ContextCompat.getDrawable(context, R.drawable.bg_otp_box_default)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (i > 0) marginStart = gap
                }
                setOnClickListener { requestInputFocus() }
            }
            boxes.add(box)
            boxContainer.addView(box)
        }
        render(hiddenInput.text?.toString() ?: "")
    }

    private fun render(code: String) {
        val activeIndex = code.length.coerceAtMost(codeLength - 1)
        boxes.forEachIndexed { index, box ->
            box.text = code.getOrNull(index)?.toString() ?: ""
            box.background = ContextCompat.getDrawable(
                context,
                when {
                    isErrorState && code.isNotEmpty() -> R.drawable.bg_otp_box_error
                    index == activeIndex && code.length < codeLength -> R.drawable.bg_otp_box_active
                    code.getOrNull(index) != null -> R.drawable.bg_otp_box_active
                    else -> R.drawable.bg_otp_box_default
                }
            )
        }
    }

    /** 当前已输入的验证码 */
    fun getCode(): String {
        return hiddenInput.text?.toString() ?: ""
    }

    /** 清空输入并回到初始态 */
    fun clear() {
        suppressRender = true
        hiddenInput.setText("")
        suppressRender = false
        isErrorState = false
        render("")
    }

    /**
     * 校验失败时闪一下错误态
     *
     * 只做短暂高亮，不长期停留在错误色上——用户刚开始改第一个数字时
     * 满屏红色会干扰输入，闪一次足够传达「刚才那次不对」。
     */
    fun flashError() {
        isErrorState = true
        render(getCode())
        postDelayed({
            isErrorState = false
            render(getCode())
        }, ERROR_FLASH_MS)
    }

    /**
     * 弹出软键盘并把焦点交还给隐藏输入框
     *
     * SHOW_IMPLICIT 在 API 34 被标记废弃，但它的替代方案（showSoftInput(view, 0)）
     * 在部分输入法下不会主动弹出键盘，这里保留原语义并显式抑制警告。
     */
    @Suppress("DEPRECATION")
    fun requestInputFocus() {
        hiddenInput.requestFocus()
        hiddenInput.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val ERROR_FLASH_MS = 900L
    }
}
