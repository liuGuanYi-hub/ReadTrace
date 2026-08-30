package com.example.readtrace.util

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.Window
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.example.readtrace.R

/**
 * 🎨 优雅二次确认弹窗组件（删除/归档/清空/恢复等重要操作的统一拦截浮层）
 *
 * 采用和纸/黑曜质感微圆角卡片、朱砂红危险动作按钮与雅金常规按钮、触觉震动反馈与进场微弹簧动效。
 */
object ElegantConfirmDialog {

    fun show(
        activity: Activity,
        title: String,
        message: String = "",
        confirmText: String = "确认",
        cancelText: String = "取消",
        isDanger: Boolean = false,
        showCancel: Boolean = true,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null,
    ) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val isDark = ThemeHelper.isDarkMode(activity)
        var dialog: Dialog? = null

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(20))
            setBackgroundResource(R.drawable.bg_elegant_dialog)
        }

        // 1. 标题
        val titleView = TextView(activity).apply {
            text = title
            textSize = 17f
            setTextColor(activity.getColor(R.color.readtrace_ink))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.02f
        }
        container.addView(titleView)

        // 2. 说明正文
        if (message.isNotBlank()) {
            val messageView = TextView(activity).apply {
                text = message
                textSize = 13.5f
                setTextColor(activity.getColor(R.color.readtrace_muted))
                setLineSpacing(dp(4).toFloat(), 1f)
                setPadding(0, dp(10), 0, 0)
            }
            container.addView(messageView)
        }

        // 3. 底部操作按钮行
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(22), 0, 0)
        }

        // 取消按钮
        if (showCancel) {
            val btnCancel = TextView(activity).apply {
                text = cancelText
                textSize = 14f
                setTextColor(activity.getColor(R.color.readtrace_muted))
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setBackgroundResource(R.drawable.bg_status_chip)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    ViewAnimationHelper.playCardBounce(this)
                    HapticFeedbackEngine.lightClick(activity)
                    dialog?.dismiss()
                    onCancel?.invoke()
                }
            }
            btnRow.addView(btnCancel)
        }

        // 确认按钮
        val btnConfirm = TextView(activity).apply {
            text = confirmText
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (showCancel) marginStart = dp(10)
            }

            if (isDanger) {
                // 朱砂红危险按钮
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#C62828"))
                    cornerRadius = dp(16).toFloat()
                }
            } else {
                // 雅金高光按钮
                background = GradientDrawable().apply {
                    setColor(if (isDark) Color.parseColor("#996515") else Color.parseColor("#8C6D46"))
                    cornerRadius = dp(16).toFloat()
                }
            }

            isClickable = true
            isFocusable = true
            setOnClickListener {
                ViewAnimationHelper.playCardBounce(this)
                HapticFeedbackEngine.lightClick(activity)
                dialog?.dismiss()
                onConfirm()
            }
        }
        btnRow.addView(btnConfirm)

        container.addView(btnRow)

        dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(container)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    (activity.resources.displayMetrics.widthPixels * 0.88f).toInt().coerceAtMost(dp(420)),
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setGravity(Gravity.CENTER)
            }
        }

        container.alpha = 0f
        container.scaleX = 0.92f
        container.scaleY = 0.92f
        container.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(240L)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        dialog.show()
    }
}
