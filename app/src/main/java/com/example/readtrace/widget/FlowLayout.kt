package com.example.readtrace.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

/** 极简自动换行容器：子视图从左到右排列，超出宽度自动换行，用于分类标签点选区 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    private val hGap = dp(10)
    private val vGap = dp(10)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChildren(widthMeasureSpec, heightMeasureSpec)
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        var lineHeight = 0
        var lineUsed = paddingTop
        var cursorX = paddingStart
        var maxRowWidth = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val w = child.measuredWidth
            val h = child.measuredHeight
            if (cursorX + w > maxWidth - paddingEnd && cursorX > paddingStart) {
                maxRowWidth = maxOf(maxRowWidth, cursorX - hGap)
                lineUsed += lineHeight + vGap
                cursorX = paddingStart
                lineHeight = 0
            }
            cursorX += w + hGap
            lineHeight = maxOf(lineHeight, h)
        }
        maxRowWidth = maxOf(maxRowWidth, cursorX - hGap)
        setMeasuredDimension(
            if (maxWidth > 0) maxWidth else maxRowWidth + paddingStart + paddingEnd,
            lineUsed + lineHeight + paddingBottom,
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val availableWidth = width - paddingStart - paddingEnd
        var cursorX = paddingStart
        var cursorY = paddingTop

        var lineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val w = child.measuredWidth
            val h = child.measuredHeight
            if (cursorX + w > availableWidth + paddingStart && cursorX > paddingStart) {
                cursorX = paddingStart
                cursorY += lineHeight + vGap
                lineHeight = 0
            }
            child.layout(cursorX, cursorY, cursorX + w, cursorY + h)
            cursorX += w + hGap
            lineHeight = maxOf(lineHeight, h)
        }
    }
}
