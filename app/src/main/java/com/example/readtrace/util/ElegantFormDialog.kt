package com.example.readtrace.util

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.readtrace.R

/**
 * 🎨 优雅表单弹窗（详情页添加角色/大纲/地标/调整评分的统一组件）
 *
 * 设计语言与作品选择底板一致：
 * 1. 深色圆角玻璃容器 + hairline 描边；
 * 2. 标签式输入框（聚焦暖金描边）、多行自适应；
 * 3. 金色渐变保存胶囊 + 描边取消胶囊；
 * 4. 进场逐段渐入上浮与按钮弹性反馈。
 */
object ElegantFormDialog {

    class Field(
        val key: String,
        val label: String,
        val hint: String = "",
        val preset: String = "",
        val inputType: Int = InputType.TYPE_CLASS_TEXT,
        val minLines: Int = 1,
        val required: Boolean = false,
    )

    fun show(
        activity: Activity,
        title: String,
        confirmText: String = "保 存",
        fields: List<Field>,
        onConfirm: (values: Map<String, String>) -> Unit,
    ) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(20))
            setBackgroundResource(R.drawable.bg_elegant_dialog)
        }

        // 标题
        val titleView = TextView(activity).apply {
            text = title
            textSize = 16.5f
            setTextColor(activity.getColor(R.color.readtrace_ink))
            letterSpacing = 0.02f
        }
        container.addView(titleView)

        // 输入区
        val inputs = mutableMapOf<String, EditText>()
        val scroll = ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        scroll.addView(column)
        container.addView(scroll)

        fields.forEachIndexed { index, field ->
            val label = TextView(activity).apply {
                text = field.label
                textSize = 12.5f
                setTextColor(activity.getColor(R.color.readtrace_muted))
                setPadding(2, if (index == 0) 0 else dp(12), 0, dp(5))
            }
            column.addView(label)

            val input = EditText(activity).apply {
                hint = field.hint
                setText(field.preset)
                setTextSize(14f)
                setTextColor(activity.getColor(R.color.readtrace_ink))
                setHintTextColor(activity.getColor(R.color.readtrace_muted))
                inputType = field.inputType
                minLines = field.minLines
                gravity = Gravity.TOP or Gravity.START
                setBackgroundResource(R.drawable.bg_form_input)
                setPadding(dp(13), dp(10), dp(13), dp(10))
            }
            inputs[field.key] = input
            column.addView(input)
        }

        // 按钮行
        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18), 0, 0)
        }
        fun button(text: String, confirm: Boolean): TextView = TextView(activity).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            isAllCaps = false
            if (confirm) {
                setBackgroundResource(R.drawable.bg_chip_picker_selected)
                setTextColor(activity.getColor(R.color.chip_selected_text))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            } else {
                setBackgroundResource(R.drawable.bg_chip_picker_idle)
                setTextColor(activity.getColor(R.color.chip_idle_text))
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = if (confirm) 0 else dp(10)
            }
            setOnClickListener { ViewAnimationHelper.playCardBounce(this) }
        }

        val btnCancel = button("取消", confirm = false)
        val btnConfirm = button(confirmText, confirm = true)
        buttonRow.addView(btnCancel)
        buttonRow.addView(btnConfirm)
        container.addView(buttonRow)

        val dialog = android.app.Dialog(activity).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(container)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    (activity.resources.displayMetrics.widthPixels * 0.9f).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setGravity(Gravity.CENTER)
                setWindowAnimations(android.R.style.Animation_Dialog)
            }
        }

        // 进场动效：容器上浮 + 字段逐个渐入 + 按钮弹入
        container.alpha = 0f
        container.translationY = dp(28).toFloat()
        container.animate().alpha(1f).translationY(0f).setDuration(300L).start()
        fields.forEachIndexed { index, field ->
            val input = inputs.getValue(field.key)
            input.alpha = 0f
            input.translationY = dp(12).toFloat()
            input.animate().alpha(1f).translationY(0f)
                .setStartDelay(80L + index * 45L)
                .setDuration(280L)
                .start()
        }
        btnConfirm.scaleX = 0.6f
        btnConfirm.scaleY = 0.6f
        btnConfirm.animate().scaleX(1f).scaleY(1f)
            .setStartDelay(120L)
            .setDuration(300L)
            .setInterpolator(OvershootInterpolator(2f))
            .start()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        btnConfirm.setOnClickListener {
            val missing = fields.firstOrNull { it.required && inputs.getValue(it.key).text.toString().trim().isEmpty() }
            if (missing != null) {
                val input = inputs.getValue(missing.key)
                input.animate().translationX(-dp(6).toFloat()).setDuration(50)
                    .withEndAction { input.animate().translationX(dp(6).toFloat()).setDuration(50).withEndAction { input.animate().translationX(0f).setDuration(50).start() }.start() }
                    .start()
                Toast.makeText(activity, "请填写「${missing.label.substringAfter(" ")}」", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val values = fields.associate { it.key to inputs.getValue(it.key).text.toString().trim() }
            dialog.dismiss()
            onConfirm(values)
        }

        dialog.show()
    }
}
