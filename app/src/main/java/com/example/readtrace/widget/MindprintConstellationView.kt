package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.BookSimilarityEngine
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

sealed class ConstellationFilter {
    object ALL : ConstellationFilter()
    data class ByMedia(val mediaType: MediaType) : ConstellationFilter()
    data class ByRegion(val regionName: String) : ConstellationFilter()
}

class MindprintConstellationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class StarNode(
        val book: Book,
        val mindprint: BookMindprint,
        var worldX: Float,
        var worldY: Float,
        var radius: Float,
        val colorHex: Int,
        val detectedRegion: String,
    ) {
        fun matches(filter: ConstellationFilter): Boolean {
            return when (filter) {
                is ConstellationFilter.ALL -> true
                is ConstellationFilter.ByMedia -> book.mediaType == filter.mediaType
                is ConstellationFilter.ByRegion -> {
                    detectedRegion.contains(filter.regionName) ||
                        (book.category ?: "").contains(filter.regionName) ||
                        book.tags.any { it.contains(filter.regionName) }
                }
            }
        }
    }

    data class ConstellationEdge(
        val nodeA: StarNode,
        val nodeB: StarNode,
        val similarity: Int,
    )

    data class AmbientParticle(
        val x: Float,
        val y: Float,
        val size: Float,
        val baseAlpha: Float,
        val phase: Float,
    )

    private val stars = mutableListOf<StarNode>()
    private val edges = mutableListOf<ConstellationEdge>()
    private val ambientParticles = mutableListOf<AmbientParticle>()

    private var offsetX = 0f
    private var offsetY = 0f
    private var animPhase = 0f

    private var activeFilter: ConstellationFilter = ConstellationFilter.ALL
    private var selectedStar: StarNode? = null
    var onStarClickListener: ((Book, BookMindprint) -> Unit)? = null

    // 画笔
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.2f)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
    }

    private val starGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val starCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX -= distanceX
            offsetY -= distanceY
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val touchX = e.x - width / 2f - offsetX
            val touchY = e.y - height / 2f - offsetY

            val hit = stars.firstOrNull { star ->
                hypot((touchX - star.worldX).toDouble(), (touchY - star.worldY).toDouble()) <= star.radius * 2.5f
            }

            if (hit != null) {
                selectedStar = hit
                invalidate()
                onStarClickListener?.invoke(hit.book, hit.mindprint)
                return true
            }
            return false
        }
    })

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animPhase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        val rnd = Random(42)
        for (i in 0 until 60) {
            ambientParticles.add(
                AmbientParticle(
                    x = (rnd.nextFloat() - 0.5f) * 2000f,
                    y = (rnd.nextFloat() - 0.5f) * 2000f,
                    size = dpToPx(1f + rnd.nextFloat() * 2f),
                    baseAlpha = 0.2f + rnd.nextFloat() * 0.5f,
                    phase = rnd.nextFloat() * 6.28f,
                ),
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    fun setFilter(filter: ConstellationFilter) {
        activeFilter = filter
        invalidate()
    }

    fun setBooksData(books: List<Book>, databaseHelper: BookDatabaseHelper) {
        stars.clear()
        edges.clear()
        if (books.isEmpty()) {
            invalidate()
            return
        }

        val colors = intArrayOf(
            Color.parseColor("#E07A5F"), // 珊瑚琥珀
            Color.parseColor("#3D84B8"), // 星云海蓝
            Color.parseColor("#81B29A"), // 翡翠微绿
            Color.parseColor("#F4A261"), // 暖阳金黄
            Color.parseColor("#9B5DE5"), // 幻夜紫罗兰
            Color.parseColor("#00BBF9"), // 冰川青蓝
        )

        val count = books.size
        val spread = dpToPx(280f)

        books.forEachIndexed { idx, book ->
            val mp = databaseHelper.getMindprint(book.id)
            val angle = (idx.toFloat() / count) * 6.283185f

            // 基于六维心智投影计算星系相对位置
            val logicBias = (mp.logicScore - mp.emotionScore).toFloat() * dpToPx(18f)
            val depthBias = (mp.depthScore - mp.healingScore).toFloat() * dpToPx(18f)
            val baseDist = spread * (0.6f + 0.4f * (idx % 3))

            val wx = cos(angle) * baseDist + logicBias
            val wy = sin(angle) * baseDist + depthBias

            val detectedReg = BookSimilarityEngine.detectRegion(book)

            val node = StarNode(
                book = book,
                mindprint = mp,
                worldX = wx,
                worldY = wy,
                radius = dpToPx(14f + (mp.averageScore().toFloat() - 5f).coerceAtLeast(0f) * 1.5f),
                colorHex = colors[idx % colors.size],
                detectedRegion = detectedReg,
            )
            stars.add(node)
        }

        // 计算星座连线 (依据书籍相似度 >= 72%)
        for (i in 0 until stars.size) {
            for (j in i + 1 until stars.size) {
                val a = stars[i]
                val b = stars[j]
                val recs = BookSimilarityEngine.findSimilarBooks(a.book, databaseHelper, limit = 4)
                val match = recs.firstOrNull { it.book.id == b.book.id }
                if (match != null && match.similarityPercent >= 72) {
                    edges.add(ConstellationEdge(a, b, match.similarityPercent))
                }
            }
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isNight) {
            canvas.drawColor(Color.parseColor("#0F1310"))
        } else {
            canvas.drawColor(Color.parseColor("#F4EFE6"))
        }

        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY

        // 1. 绘制背景散落微光星子
        ambientParticles.forEach { p ->
            val px = cx + p.x
            val py = cy + p.y
            if (px >= 0 && px <= width && py >= 0 && py <= height) {
                val shimmer = 0.5f + 0.5f * sin(animPhase * 6.283f * 2 + p.phase)
                particlePaint.alpha = ((p.baseAlpha * shimmer) * (if (isNight) 200 else 100)).toInt().coerceIn(10, 255)
                particlePaint.color = if (isNight) Color.WHITE else Color.parseColor("#8C7F70")
                canvas.drawCircle(px, py, p.size, particlePaint)
            }
        }

        // 2. 绘制认知星座连线 (Constellation Lines)
        edges.forEach { edge ->
            val aMatch = edge.nodeA.matches(activeFilter)
            val bMatch = edge.nodeB.matches(activeFilter)
            val isBothMatch = aMatch && bMatch

            val ax = cx + edge.nodeA.worldX
            val ay = cy + edge.nodeA.worldY
            val bx = cx + edge.nodeB.worldX
            val by = cy + edge.nodeB.worldY

            val baseAlpha = if (isBothMatch) {
                (edge.similarity.toFloat() / 100f * (if (isNight) 170 else 120)).toInt().coerceIn(40, 230)
            } else {
                25
            }

            linePaint.color = if (isNight) Color.parseColor("#80A48A") else Color.parseColor("#A89E90")
            linePaint.alpha = baseAlpha
            linePaint.strokeWidth = dpToPx(if (isBothMatch) 1.4f else 0.8f)
            canvas.drawLine(ax, ay, bx, by, linePaint)
        }

        // 3. 绘制星辰节点 (Star Nodes)
        stars.forEach { star ->
            val isMatch = star.matches(activeFilter)
            val sx = cx + star.worldX
            val sy = cy + star.worldY

            val isSel = star == selectedStar
            val breathe = if (isMatch) (1f + 0.12f * sin(animPhase * 6.283f * 1.5f + star.worldX * 0.01f)) else 1.0f
            val currentRadius = (if (isMatch) star.radius else star.radius * 0.75f) * breathe * (if (isSel) 1.25f else 1.0f)

            if (isMatch) {
                // 发光光晕 (Radial Glow)
                val glowRadius = currentRadius * 2.6f
                val glowColor = Color.argb(if (isNight) 110 else 70, Color.red(star.colorHex), Color.green(star.colorHex), Color.blue(star.colorHex))
                val transparentGlow = Color.argb(0, Color.red(star.colorHex), Color.green(star.colorHex), Color.blue(star.colorHex))

                val glowShader = RadialGradient(sx, sy, glowRadius, glowColor, transparentGlow, Shader.TileMode.CLAMP)
                starGlowPaint.shader = glowShader
                canvas.drawCircle(sx, sy, glowRadius, starGlowPaint)

                // 星核实心圆
                starCorePaint.color = star.colorHex
                starCorePaint.alpha = 255
                canvas.drawCircle(sx, sy, currentRadius, starCorePaint)

                // 核心白曜高光
                starCorePaint.color = if (isNight) Color.WHITE else Color.parseColor("#FFFDF7")
                canvas.drawCircle(sx, sy, currentRadius * 0.42f, starCorePaint)

                // 作品名称与作者标签
                textPaint.color = if (isNight) Color.parseColor("#F5F3ED") else Color.parseColor("#20241F")
                textPaint.alpha = 255
                textPaint.textSize = dpToPx(12.5f)
                canvas.drawText("《${star.book.title}》", sx, sy + currentRadius + dpToPx(15f), textPaint)

                subTextPaint.color = if (isNight) Color.parseColor("#9EA599") else Color.parseColor("#71776D")
                subTextPaint.alpha = 230
                subTextPaint.textSize = dpToPx(10.5f)
                val author = star.book.author ?: "未知"
                canvas.drawText("${star.book.mediaType.emoji} $author · ${star.detectedRegion}", sx, sy + currentRadius + dpToPx(27f), subTextPaint)
            } else {
                // 暗淡微光呈现 (Dimmed Node)
                starCorePaint.color = star.colorHex
                starCorePaint.alpha = 45
                canvas.drawCircle(sx, sy, currentRadius, starCorePaint)

                textPaint.color = if (isNight) Color.parseColor("#556055") else Color.parseColor("#B0A89C")
                textPaint.alpha = 60
                textPaint.textSize = dpToPx(11f)
                canvas.drawText("《${star.book.title}》", sx, sy + currentRadius + dpToPx(14f), textPaint)
            }
        }
    }

    private fun dpToPx(dp: Float): Float =
        dp * resources.displayMetrics.density
}
