package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.example.readtrace.model.BookMindprint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MindprintRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var depthScore = 8.0
    private var artistryScore = 8.0
    private var emotionScore = 8.0
    private var logicScore = 8.0
    private var difficultyScore = 5.0
    private var healingScore = 8.0

    private var animProgress = 1.0f
    private var animator: ValueAnimator? = null

    private val dimensionLabels = arrayOf(
        "🧠 思想",
        "🖋️ 文笔",
        "❤️ 情感",
        "📐 逻辑",
        "⛰️ 门槛",
        "🌿 治愈",
    )

    // 网格画笔
    private val webPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDD4CA")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
    }

    // 放射轴线画笔
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EADFD5")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(3f), dpToPx(3f)), 0f)
    }

    // 数据填充区画笔
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44C47D5C")
        style = Paint.Style.FILL
    }

    // 数据外轮廓画笔
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C47D5C")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
    }

    // 数据顶点小圆点画笔
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9C5232")
        style = Paint.Style.FILL
    }

    private val dotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }

    // 文本画笔
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F3832")
        textSize = dpToPx(11.5f)
        textAlign = Paint.Align.CENTER
    }

    private val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9C5232")
        textSize = dpToPx(10f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun setMindprint(mindprint: BookMindprint, animate: Boolean = true) {
        depthScore = mindprint.depthScore.coerceIn(0.0, 10.0)
        artistryScore = mindprint.artistryScore.coerceIn(0.0, 10.0)
        emotionScore = mindprint.emotionScore.coerceIn(0.0, 10.0)
        logicScore = mindprint.logicScore.coerceIn(0.0, 10.0)
        difficultyScore = mindprint.difficultyScore.coerceIn(0.0, 10.0)
        healingScore = mindprint.healingScore.coerceIn(0.0, 10.0)

        if (animate) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0.1f, 1.0f).apply {
                duration = 650
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animProgress = 1.0f
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultWidth = dpToPx(280f).toInt()
        val defaultHeight = dpToPx(240f).toInt()

        val width = resolveSize(defaultWidth, widthMeasureSpec)
        val height = resolveSize(defaultHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        // 留出四周文字边距
        val maxRadius = min(cx, cy) - dpToPx(38f)
        if (maxRadius <= 0) return

        // 1. 绘制 4 层正六边形同心蛛网
        val levels = 4
        for (i in 1..levels) {
            val levelRadius = maxRadius * (i.toFloat() / levels)
            val hexPath = Path()
            for (j in 0 until 6) {
                val angle = -Math.PI / 2 + j * (Math.PI / 3)
                val x = (cx + levelRadius * cos(angle)).toFloat()
                val y = (cy + levelRadius * sin(angle)).toFloat()
                if (j == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
            }
            hexPath.close()
            canvas.drawPath(hexPath, webPaint)
        }

        // 2. 绘制 6 条放射轴线与文字标签
        val scores = doubleArrayOf(
            depthScore,
            artistryScore,
            emotionScore,
            logicScore,
            difficultyScore,
            healingScore,
        )

        for (j in 0 until 6) {
            val angle = -Math.PI / 2 + j * (Math.PI / 3)
            val endX = (cx + maxRadius * cos(angle)).toFloat()
            val endY = (cy + maxRadius * sin(angle)).toFloat()
            canvas.drawLine(cx, cy, endX, endY, axisPaint)

            // 绘制角标标签与当前分值
            val labelDistance = maxRadius + dpToPx(20f)
            val labelX = (cx + labelDistance * cos(angle)).toFloat()
            val labelY = (cy + labelDistance * sin(angle)).toFloat()

            val scoreStr = String.format(Locale.getDefault(), "%.1f", scores[j])
            val labelStr = dimensionLabels[j]

            // 针对上下左右不同方位的文本微调偏移
            val yOffset = when (j) {
                0 -> -dpToPx(4f) // 顶部
                3 -> dpToPx(14f) // 底部
                else -> dpToPx(4f)
            }

            canvas.drawText(labelStr, labelX, labelY + yOffset, labelPaint)
            canvas.drawText(scoreStr, labelX, labelY + yOffset + dpToPx(12f), scoreTextPaint)
        }

        // 3. 绘制多维心智覆盖多边形
        val dataPath = Path()
        val dotCoords = mutableListOf<Pair<Float, Float>>()

        for (j in 0 until 6) {
            val angle = -Math.PI / 2 + j * (Math.PI / 3)
            val currentScore = (scores[j] / 10.0).toFloat() * animProgress
            val scoreRadius = maxRadius * currentScore.coerceIn(0.05f, 1.0f)
            val x = (cx + scoreRadius * cos(angle)).toFloat()
            val y = (cy + scoreRadius * sin(angle)).toFloat()

            dotCoords.add(Pair(x, y))
            if (j == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
        }
        dataPath.close()

        // 填充半透明柔光
        canvas.drawPath(dataPath, fillPaint)
        // 描边
        canvas.drawPath(dataPath, strokePaint)

        // 4. 绘制顶点高光发光圆点
        for ((x, y) in dotCoords) {
            canvas.drawCircle(x, y, dpToPx(4.5f), dotPaint)
            canvas.drawCircle(x, y, dpToPx(4.5f), dotBorderPaint)
        }
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}
