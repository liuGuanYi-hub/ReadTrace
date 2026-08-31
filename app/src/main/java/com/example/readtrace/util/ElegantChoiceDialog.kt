package com.example.readtrace.util

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.readtrace.R

/**
 * 🎨 优雅单选列表弹窗（对比作品/笔记菜单/时间线筛选的统一组件）
 *
 * 深色圆角玻璃容器、卡式选项行（主标题+副标题+选中金勾）、逐项渐入上浮与点击弹性反馈。
 */
object ElegantChoiceDialog {

    class Choice(
        val label: String,
        val subtitle: String? = null,
        val leadingEmoji: String? = null,
    )

    fun show(
        activity: Activity,
        title: String,
        choices: List<Choice>,
        selectedIndex: Int? = null,
        onSelected: (index: Int) -> Unit,
    ) {
        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundResource(R.drawable.bg_elegant_dialog)
        }

        val titleView = TextView(activity).apply {
            text = title
            textSize = 16.5f
            setTextColor(activity.getColor(R.color.readtrace_ink))
            letterSpacing = 0.02f
        }
        container.addView(titleView)

        val scroll = ScrollView(activity).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, dp(12), 0, 0)
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(column)
        container.addView(scroll)

        val dialog = android.app.Dialog(activity).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setContentView(ScrollView(activity).apply {
                    addView(container)
            })
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    (activity.resources.displayMetrics.widthPixels * 0.9f).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                setGravity(Gravity.CENTER)
            }
        }

        choices.forEachIndexed { index, choice ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(
                    if (index == selectedIndex) R.drawable.bg_work_picker_item_selected
                    else R.drawable.bg_work_picker_item,
                )
                setPadding(dp(14), dp(11), dp(14), dp(11))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = if (index == 0) 0 else dp(8) }
            }

            if (choice.leadingEmoji != null) {
                row.addView(TextView(activity).apply {
                    text = choice.leadingEmoji
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginEnd = dp(9) }
                })
            }

            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(activity).apply {
                text = choice.label
                textSize = 14f
                setTextColor(activity.getColor(if (index == selectedIndex) R.color.picker_item_stroke_selected else R.color.readtrace_ink))
                setTypeface(typeface, if (index == selectedIndex) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            })
            choice.subtitle?.let { sub ->
                textCol.addView(TextView(activity).apply {
                    text = sub
                    textSize = 11.5f
                    setTextColor(activity.getColor(R.color.readtrace_muted))
                    setPadding(0, dp(2), 0, 0)
                })
            }
            row.addView(textCol)

            val check = TextView(activity).apply {
                textSize = 14f
                text = "✓"
                setTextColor(activity.getColor(R.color.chip_selected_text))
                setBackgroundResource(R.drawable.bg_check_gold)
                gravity = Gravity.CENTER
                visibility = if (index == selectedIndex) View.VISIBLE else View.GONE
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(10) }
            }
            row.addView(check)

            row.alpha = 0f
            row.translationY = dp(14).toFloat()
            row.animate().alpha(1f).translationY(0f)
                .setStartDelay(60L + index * 35L)
                .setDuration(280L)
                .start()

            row.setOnClickListener {
                ViewAnimationHelper.playCardBounce(row)
                HapticFeedbackEngine.lightClick(activity)
                onSelected(index)
                dialog.dismiss()
            }
            column.addView(row)
        }

        container.alpha = 0f
        container.translationY = dp(26).toFloat()
        container.animate().alpha(1f).translationY(0f)
            .setDuration(280L)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()

        dialog.show()
    }
}
