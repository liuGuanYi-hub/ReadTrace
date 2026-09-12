package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.example.readtrace.util.HapticFeedbackEngine
import kotlin.math.abs
import kotlin.math.sin

/**
 * 🌟 鸿蒙流光寻迹与刷动磁吸底部导航栏 (Fluid Spotlight Brush & Magnetic Drag Dock)
 *
 * 交互骨架：
 * 1. 【画刷式轨迹跟踪】：手指在各 Tab 间往复刷动，临近 Tab 图标平滑放大与浮动；
 * 2. 【临近磁吸形变】：动态计算与各 Tab 的几何距离，驱动图标缩放与位移；
 * 3. 【棘轮微震矩阵】：跨越 Tab 感应边界时触发清脆细腻的线性马达棘轮微震；
 * 4. 【松手智能吸附】：松手后吸附至最近 Tab，自动完成页面平滑切换。
 *
 * 注：按压「流光光球」视觉层已整体移除，待重新设计后在此回填渲染逻辑。
 */
class FluidSpotlightNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** 指尖当前坐标（相对本视图），供后续光球渲染设计使用 */
    private var spotlightX = 0f
    private var spotlightY = 0f

    private var downX = 0f
    private var downY = 0f
    private var isDragging = false

    private var currentHoverIndex = -1
    private var lastHoverIndex = -1

    private var snapAnimator: ValueAnimator? = null

    var onTabSelectedListener: ((Int) -> Unit)? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                spotlightX = ev.x
                spotlightY = ev.y
                isDragging = false
                updateHoverState(ev.x)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (dx > touchSlop && dx > dy) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    resetTabDistortion()
                }
            }
        }
        return isDragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                spotlightX = event.x
                spotlightY = event.y
                isDragging = false
                updateHoverState(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                if (dx > touchSlop) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                spotlightX = event.x
                spotlightY = event.y
                updateHoverState(event.x)
                applyProximityDistortion(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val targetIndex = getTabIndexAt(event.x)
                if (targetIndex != -1) {
                    snapToTab(targetIndex)
                    onTabSelectedListener?.invoke(targetIndex)
                } else {
                    resetTabDistortion()
                }
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                resetTabDistortion()
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** 检查当前触点正悬停在哪一个 Tab 上，并触发清脆棘轮微震 */
    private fun updateHoverState(x: Float) {
        val index = getTabIndexAt(x)
        if (index != -1 && index != currentHoverIndex) {
            currentHoverIndex = index
            if (lastHoverIndex != -1) {
                HapticFeedbackEngine.dockBrushRatchetTick(context)
            }
            lastHoverIndex = currentHoverIndex
        }
    }

    /** 临近磁吸与弹性形变算法 */
    private fun applyProximityDistortion(x: Float) {
        val innerBar = getInnerNavBar() ?: return
        val count = innerBar.childCount
        if (count == 0) return

        val maxInfluenceDistance = (width.toFloat() / count) * 1.1f

        for (i in 0 until count) {
            val child = innerBar.getChildAt(i)
            val childCenterX = child.left + child.width / 2f
            val dist = abs(x - childCenterX)

            if (dist < maxInfluenceDistance) {
                val factor = (1f - (dist / maxInfluenceDistance)).coerceIn(0f, 1f)
                // 高斯加权平滑过渡 (0f ~ 1f)
                val smoothFactor = (sin((factor - 0.5) * Math.PI) * 0.5 + 0.5).toFloat()
                child.scaleX = 1f + 0.16f * smoothFactor
                child.scaleY = 1f + 0.16f * smoothFactor
                child.translationY = -dpToPx(4.5f) * smoothFactor
            } else {
                child.scaleX = 1f
                child.scaleY = 1f
                child.translationY = 0f
            }
        }
    }

    /** 弹性复原所有 Tab 的缩放与位移 */
    private fun resetTabDistortion() {
        val innerBar = getInnerNavBar() ?: return
        for (i in 0 until innerBar.childCount) {
            val child = innerBar.getChildAt(i)
            child.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240L)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }

    /** 松手后触点平滑磁吸至目标 Tab（驱动临近形变跟随） */
    private fun snapToTab(tabIndex: Int) {
        val innerBar = getInnerNavBar() ?: return
        if (tabIndex !in 0 until innerBar.childCount) return

        val targetChild = innerBar.getChildAt(tabIndex)
        val targetCenterX = targetChild.left + targetChild.width / 2f
        val startX = spotlightX

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(startX, targetCenterX).apply {
            duration = 260L
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener { anim ->
                spotlightX = anim.animatedValue as Float
                applyProximityDistortion(spotlightX)
            }
            start()
        }

        resetTabDistortion()
    }

    private fun getTabIndexAt(x: Float): Int {
        val innerBar = getInnerNavBar() ?: return -1
        val count = innerBar.childCount
        if (count == 0) return -1

        for (i in 0 until count) {
            val child = innerBar.getChildAt(i)
            if (x >= child.left && x <= child.right) {
                return i
            }
        }
        // 边界保护：若滑出微量边界，吸附到首尾 Tab
        return if (x < (innerBar.getChildAt(0)?.left ?: 0)) 0 else count - 1
    }

    private fun getInnerNavBar(): LinearLayout? {
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v is LinearLayout) return v
        }
        return null
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}
