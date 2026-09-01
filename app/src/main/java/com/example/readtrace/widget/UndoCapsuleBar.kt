package com.example.readtrace.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.readtrace.R
import com.example.readtrace.util.HapticFeedbackEngine

/**
 * 4 秒先锋撤销微胶囊 (4-Second Undo Capsule Bar)
 * 操作即生效 + 4秒撤销，彻底消除烦人的二次确认弹窗。
 */
class UndoCapsuleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val containerLayout: LinearLayout
    private val tvMessage: TextView
    private val btnUndo: TextView
    private val progressBar: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null
    private var progressAnimator: ValueAnimator? = null

    private var onUndoAction: (() -> Unit)? = null
    private var onCommitAction: (() -> Unit)? = null

    init {
        visibility = View.GONE
        alpha = 0f
        translationY = dpToPx(30).toFloat()

        // 胶囊主体
        containerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = dpToPx(16)
            val vPad = dpToPx(10)
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6181C26")) // 高透曜石深空黑
                cornerRadius = dpToPx(24).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
            elevation = dpToPx(8).toFloat()
        }

        tvMessage = TextView(context).apply {
            text = "已移入回收站"
            textSize = 13f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnUndo = TextView(context).apply {
            text = "撤销"
            textSize = 13f
            setTextColor(Color.parseColor("#F4A261")) // 先锋琥珀金
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val padH = dpToPx(12)
            val padV = dpToPx(5)
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26F4A261"))
                cornerRadius = dpToPx(14).toFloat()
            }
            setOnClickListener {
                HapticFeedbackEngine.lightClick(context)
                dismiss()
                onUndoAction?.invoke()
            }
        }

        containerLayout.addView(tvMessage)
        containerLayout.addView(btnUndo)

        // 底部微型 2dp 进度条
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 1000
            progressDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#80F4A261"))
                cornerRadius = dpToPx(1).toFloat()
            }
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(2)).apply {
                gravity = Gravity.BOTTOM
                marginStart = dpToPx(16)
                marginEnd = dpToPx(16)
                bottomMargin = dpToPx(2)
            }
            layoutParams = lp
        }

        val wrapLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
            val marginH = dpToPx(18)
            val marginB = dpToPx(16)
            setMargins(marginH, 0, marginH, marginB)
        }
        addView(containerLayout, wrapLp)
        addView(progressBar)
    }

    /**
     * 弹出撤销胶囊
     * @param message 提示文案，例如 "已移入回收站"
     * @param durationMs 持续毫秒数，默认 4000ms
     * @param onUndo 用户点击撤销时的回调
     * @param onCommit 4 秒倒计时自然结束时的固化回调
     */
    fun showCapsule(
        message: String,
        durationMs: Long = 4000L,
        onUndo: () -> Unit,
        onCommit: (() -> Unit)? = null,
    ) {
        cancelScheduled()

        this.onUndoAction = onUndo
        this.onCommitAction = onCommit
        tvMessage.text = message

        visibility = View.VISIBLE
        animate().cancel()
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()

        // 进度条倒计时动画
        progressBar.progress = 1000
        progressAnimator = ValueAnimator.ofInt(1000, 0).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                progressBar.progress = it.animatedValue as Int
            }
            start()
        }

        val runnable = Runnable {
            dismiss()
            onCommitAction?.invoke()
        }
        dismissRunnable = runnable
        handler.postDelayed(runnable, durationMs)
    }

    fun dismiss() {
        cancelScheduled()
        animate().cancel()
        animate()
            .alpha(0f)
            .translationY(dpToPx(25).toFloat())
            .setDuration(200L)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                }
            })
            .start()
    }

    private fun cancelScheduled() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        progressAnimator?.cancel()
        progressAnimator = null
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}