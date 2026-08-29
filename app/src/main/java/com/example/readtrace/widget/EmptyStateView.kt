package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * 极简线稿空态：虚线圆环内一本摊开的书 + 漂浮星点，纯代码绘制
 */
class EmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var lineColor: Int = Color.parseColor("#3A6348")
        set(value) { field = value; invalidate() }
    var accentColor: Int = Color.parseColor("#D4AF37")
        set(value) { field = value; invalidate() }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f * 0.72f

        // 虚线圆环
        stroke.color = lineColor
        stroke.strokeWidth = 3f
        val dash = Path()
        var angle = -90.0
        while (angle < 270.0) {
            val sweep = 14.0
            val px1 = cx + r * cos(Math.toRadians(angle)).toFloat()
            val py1 = cy + r * sin(Math.toRadians(angle)).toFloat()
            val px2 = cx + r * cos(Math.toRadians(angle + sweep)).toFloat()
            val py2 = cy + r * sin(Math.toRadians(angle + sweep)).toFloat()
            dash.moveTo(px1, py1)
            dash.lineTo(px2, py2)
            angle += sweep + 10.0
        }
        canvas.drawPath(dash, stroke)

        // 摊开的书（两页线稿）
        stroke.color = lineColor
        stroke.strokeWidth = 4f
        val bookW = r * 0.9f
        val bookH = r * 0.52f
        val spineX = cx
        val topY = cy - bookH / 2f
        val botY = cy + bookH / 2f
        val leftPath = Path().apply {
            moveTo(spineX, topY + bookH * 0.12f)
            quadTo(spineX - bookW * 0.5f, topY - bookH * 0.18f, spineX - bookW, topY + bookH * 0.06f)
            lineTo(spineX - bookW, botY)
            quadTo(spineX - bookW * 0.5f, botY - bookH * 0.14f, spineX, botY - bookH * 0.06f)
            close()
        }
        val rightPath = Path().apply {
            moveTo(spineX, topY + bookH * 0.12f)
            quadTo(spineX + bookW * 0.5f, topY - bookH * 0.18f, spineX + bookW, topY + bookH * 0.06f)
            lineTo(spineX + bookW, botY)
            quadTo(spineX + bookW * 0.5f, botY - bookH * 0.14f, spineX, botY - bookH * 0.06f)
            close()
        }
        canvas.drawPath(leftPath, stroke)
        canvas.drawPath(rightPath, stroke)

        // 书脊
        canvas.drawLine(spineX, topY + bookH * 0.12f, spineX, botY - bookH * 0.06f, stroke)

        // 漂浮星点
        fill.color = accentColor
        listOf(
            Triple(cx - r * 0.82f, cy - r * 0.66f, 5f),
            Triple(cx + r * 0.86f, cy - r * 0.5f, 4f),
            Triple(cx + r * 0.66f, cy + r * 0.78f, 3.5f),
        ).forEach { (x, y, rad) ->
            canvas.drawCircle(x, y, rad * resources.displayMetrics.density, fill)
        }
    }
}
