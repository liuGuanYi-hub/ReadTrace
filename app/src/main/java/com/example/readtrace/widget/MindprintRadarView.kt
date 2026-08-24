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

    private var primaryTitle: String = ""
    private var compareTitle: String? = null
    private var compareMindprint: BookMindprint? = null

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

    // 主作品填充区画笔 (琉璃琥珀色)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44C47D5C")
        style = Paint.Style.FILL
    }

    // 主作品外轮廓画笔
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C47D5C")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
    }

    // 主作品顶点圆点画笔
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9C5232")
        style = Paint.Style.FILL
    }

    // 对比作品填充区画笔 (深邃冰川蓝)
    private val compareFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#330284C7")
        style = Paint.Style.FILL
    }

    // 对比作品外轮廓画笔
    private val compareStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0284C7")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.2f)
    }

    // 对比作品顶点圆点画笔
    private val compareDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0369A1")
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

    private val compareScoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0284C7")
        textSize = dpToPx(9.5f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(10.5f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun setMindprint(mindprint: BookMindprint, animate: Boolean = true) {
        setComparison("", mindprint, null, null, animate)
    }

    fun setComparison(
        pTitle: String,
        pMindprint: BookMindprint,
        cTitle: String?,
        cMindprint: BookMindprint?,
        animate: Boolean = true,
    ) {
        primaryTitle = pTitle
        depthScore = pMindprint.depthScore.coerceIn(0.0, 10.0)
        artistryScore = pMindprint.artistryScore.coerceIn(0.0, 10.0)
        emotionScore = pMindprint.emotionScore.coerceIn(0.0, 10.0)
        logicScore = pMindprint.logicScore.coerceIn(0.0, 10.0)
        difficultyScore = pMindprint.difficultyScore.coerceIn(0.0, 10.0)
        healingScore = pMindprint.healingScore.coerceIn(0.0, 10.0)

        compareTitle = cTitle
        compareMindprint = cMindprint

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
        val cy = height / 2f + (if (compareMindprint != null) dpToPx(8f) else 0f)
        val maxRadius = min(width / 2f, height / 2f) - dpToPx(38f)
        if (maxRadius <= 0) return

        // 绘制图例 (若处于对比模式)
        if (compareMindprint != null && !compareTitle.isNullOrBlank()) {
            val legendY = dpToPx(14f)
            legendPaint.color = Color.parseColor("#9C5232")
            canvas.drawText("■ $primaryTitle", cx - dpToPx(65f), legendY, legendPaint)

            legendPaint.color = Color.parseColor("#0284C7")
            canvas.drawText("■ $compareTitle", cx + dpToPx(65f), legendY, legendPaint)
        }

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

        val compScores = compareMindprint?.let {
            doubleArrayOf(
                it.depthScore,
                it.artistryScore,
                it.emotionScore,
                it.logicScore,
                it.difficultyScore,
                it.healingScore,
            )
        }

        for (j in 0 until 6) {
            val angle = -Math.PI / 2 + j * (Math.PI / 3)
            val endX = (cx + maxRadius * cos(angle)).toFloat()
            val endY = (cy + maxRadius * sin(angle)).toFloat()
            canvas.drawLine(cx, cy, endX, endY, axisPaint)

            val labelDistance = maxRadius + dpToPx(20f)
            val labelX = (cx + labelDistance * cos(angle)).toFloat()
            val labelY = (cy + labelDistance * sin(angle)).toFloat()

            val scoreStr = String.format(Locale.getDefault(), "%.1f", scores[j])
            val labelStr = dimensionLabels[j]

            val yOffset = when (j) {
                0 -> -dpToPx(4f)
                3 -> dpToPx(14f)
                else -> dpToPx(4f)
            }

            canvas.drawText(labelStr, labelX, labelY + yOffset, labelPaint)

            if (compScores != null) {
                val compStr = String.format(Locale.getDefault(), "%.1f", compScores[j])
                canvas.drawText("$scoreStr vs $compStr", labelX, labelY + yOffset + dpToPx(12f), compareScoreTextPaint)
            } else {
                canvas.drawText(scoreStr, labelX, labelY + yOffset + dpToPx(12f), scoreTextPaint)
            }
        }

        // 3. 绘制对比作品覆盖多边形 (若存在)
        if (compScores != null) {
            val compPath = Path()
            val compDots = mutableListOf<Pair<Float, Float>>()

            for (j in 0 until 6) {
                val angle = -Math.PI / 2 + j * (Math.PI / 3)
                val currentScore = (compScores[j] / 10.0).toFloat() * animProgress
                val scoreRadius = maxRadius * currentScore.coerceIn(0.05f, 1.0f)
                val x = (cx + scoreRadius * cos(angle)).toFloat()
                val y = (cy + scoreRadius * sin(angle)).toFloat()

                compDots.add(Pair(x, y))
                if (j == 0) compPath.moveTo(x, y) else compPath.lineTo(x, y)
            }
            compPath.close()

            canvas.drawPath(compPath, compareFillPaint)
            canvas.drawPath(compPath, compareStrokePaint)

            for ((x, y) in compDots) {
                canvas.drawCircle(x, y, dpToPx(3.5f), compareDotPaint)
                canvas.drawCircle(x, y, dpToPx(3.5f), dotBorderPaint)
            }
        }

        // 4. 绘制主作品覆盖多边形
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

        canvas.drawPath(dataPath, fillPaint)
        canvas.drawPath(dataPath, strokePaint)

        for ((x, y) in dotCoords) {
            canvas.drawCircle(x, y, dpToPx(4.5f), dotPaint)
            canvas.drawCircle(x, y, dpToPx(4.5f), dotBorderPaint)
        }
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}
