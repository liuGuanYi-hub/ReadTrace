package com.example.readtrace.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.example.readtrace.util.HapticFeedbackEngine
import kotlin.math.abs

/**
 * 列表项手势速滑容器 (Swipeable Action Item Layout)
 * 支持右滑标记在读/追更，左滑移入回收站/归档。
 */
class SwipeableActionLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val triggerThreshold = dpToPx(72).toFloat()

    private var downX = 0f
    private var downY = 0f
    private var isHorizontalDragging = false
    private var hasTriggeredHaptic = false

    private val bgIndicatorLayout: FrameLayout
    private val tvLeftAction: TextView
    private val tvRightAction: TextView

    var onSwipeRightTriggered: (() -> Unit)? = null
    var onSwipeLeftTriggered: (() -> Unit)? = null

    init {
        // 背景层（动作提示与色彩渐变）
        bgIndicatorLayout = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.INVISIBLE
        }

        // 右滑指示器（青绿：标记在读）
        tvLeftAction = TextView(context).apply {
            text = "📖 标记在读"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val pad = dpToPx(16)
            setPadding(pad, 0, pad, 0)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A7A58")) // 深翡翠绿
                cornerRadius = dpToPx(16).toFloat()
            }
        }

        // 左滑指示器（赤红：移入回收站）
        tvRightAction = TextView(context).apply {
            text = "🗑️ 移入回收站"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val pad = dpToPx(16)
            setPadding(pad, 0, pad, 0)
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#8C2A2A")) // 深绯红
                cornerRadius = dpToPx(16).toFloat()
            }
        }

        bgIndicatorLayout.addView(tvLeftAction)
        bgIndicatorLayout.addView(tvRightAction)
        addView(bgIndicatorLayout, 0)
    }

    private fun getForegroundView(): View? {
        return if (childCount > 1) getChildAt(1) else null
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                isHorizontalDragging = false
                hasTriggeredHaptic = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.4f) {
                    isHorizontalDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isHorizontalDragging = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val foreground = getForegroundView() ?: return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                // 阻尼系数
                val translation = dx * 0.65f
                foreground.translationX = translation

                if (abs(translation) > 10) {
                    bgIndicatorLayout.visibility = View.VISIBLE
                    if (translation > 0) {
                        tvLeftAction.visibility = View.VISIBLE
                        tvRightAction.visibility = View.GONE
                        val progress = (translation / triggerThreshold).coerceIn(0f, 1f)
                        tvLeftAction.alpha = progress
                    } else {
                        tvLeftAction.visibility = View.GONE
                        tvRightAction.visibility = View.VISIBLE
                        val progress = (abs(translation) / triggerThreshold).coerceIn(0f, 1f)
                        tvRightAction.alpha = progress
                    }
                }

                // 达到触发阈值时的震动反馈
                if (abs(translation) >= triggerThreshold && !hasTriggeredHaptic) {
                    HapticFeedbackEngine.dockBrushRatchetTick(context)
                    hasTriggeredHaptic = true
                } else if (abs(translation) < triggerThreshold) {
                    hasTriggeredHaptic = false
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val currentTranslation = foreground.translationX
                parent?.requestDisallowInterceptTouchEvent(false)

                if (currentTranslation >= triggerThreshold) {
                    // 右滑成功（标记在读）
                    animateSwipeOut(foreground, toRight = true) {
                        onSwipeRightTriggered?.invoke()
                    }
                } else if (currentTranslation <= -triggerThreshold) {
                    // 左滑成功（移入回收站）
                    animateSwipeOut(foreground, toRight = false) {
                        onSwipeLeftTriggered?.invoke()
                    }
                } else {
                    // 未达到阈值，弹簧复位
                    animateReset(foreground)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateReset(foreground: View) {
        foreground.animate().cancel()
        foreground.animate()
            .translationX(0f)
            .setDuration(240L)
            .setInterpolator(OvershootInterpolator(1.2f))
            .withEndAction {
                bgIndicatorLayout.visibility = View.INVISIBLE
            }
            .start()
    }

    private fun animateSwipeOut(foreground: View, toRight: Boolean, onEnd: () -> Unit) {
        val targetX = if (toRight) width.toFloat() else -width.toFloat()
        foreground.animate().cancel()
        foreground.animate()
            .translationX(targetX)
            .alpha(0f)
            .setDuration(200L)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
            .start()
    }

    fun resetCardState() {
        val foreground = getForegroundView() ?: return
        foreground.translationX = 0f
        foreground.alpha = 1f
        bgIndicatorLayout.visibility = View.INVISIBLE
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}