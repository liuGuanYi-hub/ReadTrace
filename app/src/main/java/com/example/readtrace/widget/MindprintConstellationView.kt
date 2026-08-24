package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
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
    object CrossMediaResonance : ConstellationFilter() // 🌌 跨媒介心智共鸣
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
        var hasCrossMediaEdge: Boolean = false,
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
                is ConstellationFilter.CrossMediaResonance -> hasCrossMediaEdge
            }
        }
    }

    data class ConstellationEdge(
        val nodeA: StarNode,
        val nodeB: StarNode,
        val similarity: Int,
        val isCrossMedia: Boolean = false,
        val resonanceTrait: String = "",
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

    private val auroraLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.2f)
    }

    private val pulseParticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
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

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
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
        duration = 10000L
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

    fun focusOnBook(bookId: Long) {
        val target = stars.firstOrNull { it.book.id == bookId } ?: return
        selectedStar = target
        val startX = offsetX
        val startY = offsetY
        val destX = -target.worldX
        val destY = -target.worldY

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L
            addUpdateListener {
                val f = it.animatedValue as Float
                offsetX = startX + (destX - startX) * f
                offsetY = startY + (destY - startY) * f
                invalidate()
            }
        }
        anim.start()
    }

    fun getCrossMediaResonancePeer(bookId: Long): Pair<StarNode, ConstellationEdge>? {
        val edge = edges.firstOrNull { it.isCrossMedia && (it.nodeA.book.id == bookId || it.nodeB.book.id == bookId) } ?: return null
        val peer = if (edge.nodeA.book.id == bookId) edge.nodeB else edge.nodeA
        return Pair(peer, edge)
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
        val spread = dpToPx(300f)

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

        // 计算星座连线
        for (i in 0 until stars.size) {
            for (j in i + 1 until stars.size) {
                val a = stars[i]
                val b = stars[j]

                // 1. 同媒介心智相似度连线
                if (a.book.mediaType == b.book.mediaType) {
                    val recs = BookSimilarityEngine.findSimilarBooks(a.book, databaseHelper, limit = 4)
                    val match = recs.firstOrNull { it.book.id == b.book.id }
                    if (match != null && match.similarityPercent >= 72) {
                        edges.add(ConstellationEdge(a, b, match.similarityPercent))
                        continue
                    }

                    // 番剧专属系列与同社团星座连线
                    if (a.book.mediaType == MediaType.ANIME && b.book.mediaType == MediaType.ANIME) {
                        val aAuthor = a.book.author.orEmpty()
                        val bAuthor = b.book.author.orEmpty()
                        val aTitle = a.book.title
                        val bTitle = b.book.title

                        val isSameStudio = (aAuthor.contains("京都动画") && bAuthor.contains("京都动画")) ||
                                (aAuthor.contains("骨头社") && bAuthor.contains("骨头社")) ||
                                (aAuthor.contains("david") && bAuthor.contains("david")) ||
                                (aAuthor.contains("MAPPA") && bAuthor.contains("MAPPA")) ||
                                (aAuthor.contains("A-1") && bAuthor.contains("A-1")) ||
                                (aAuthor.contains("CloverWorks") && bAuthor.contains("CloverWorks")) ||
                                (aAuthor.contains("WHITE FOX") && bAuthor.contains("WHITE FOX")) ||
                                (aAuthor.contains("动画工房") && bAuthor.contains("动画工房"))

                        val isSameFranchise = (aTitle.contains("JOJO") && bTitle.contains("JOJO")) ||
                                (aTitle.contains("夏目") && bTitle.contains("夏目")) ||
                                (aTitle.contains("轻音") && bTitle.contains("轻音")) ||
                                (aTitle.contains("春物") || aTitle.contains("青春恋爱物语")) && (bTitle.contains("春物") || bTitle.contains("青春恋爱物语")) ||
                                (aTitle.contains("灵能") && bTitle.contains("灵能")) ||
                                (aTitle.contains("间谍过家家") && bTitle.contains("间谍过家家")) ||
                                (aTitle.contains("咒术") && bTitle.contains("咒术")) ||
                                (aTitle.contains("路人女主") && bTitle.contains("路人女主")) ||
                                (aTitle.contains("约会大作战") && bTitle.contains("约会大作战"))

                        if (isSameFranchise) {
                            edges.add(ConstellationEdge(a, b, 95))
                        } else if (isSameStudio) {
                            edges.add(ConstellationEdge(a, b, 88))
                        }
                    }
                } else {
                    // 2. 跨媒介灵魂共鸣连线（书籍 vs 番剧 / 影视 / 游戏）
                    val crossTrait = detectCrossMediaTrait(a, b)
                    if (crossTrait != null) {
                        a.hasCrossMediaEdge = true
                        b.hasCrossMediaEdge = true
                        edges.add(
                            ConstellationEdge(
                                nodeA = a,
                                nodeB = b,
                                similarity = crossTrait.second,
                                isCrossMedia = true,
                                resonanceTrait = crossTrait.first,
                            )
                        )
                    }
                }
            }
        }
        invalidate()
    }

    private fun detectCrossMediaTrait(a: StarNode, b: StarNode): Pair<String, Int>? {
        val aTitle = a.book.title
        val bTitle = b.book.title

        // 1. 存在主义哲学与终极孤独（《百年孤独》/《局外人》/《1984》 vs 《EVA》/《来自深渊》/《夏日重现》）
        if ((aTitle.contains("百年孤独") || aTitle.contains("局外人") || aTitle.contains("1984")) &&
            (bTitle.contains("EVA") || bTitle.contains("新世纪福音战士") || bTitle.contains("来自深渊") || bTitle.contains("夏日重现")) ||
            (bTitle.contains("百年孤独") || bTitle.contains("局外人") || bTitle.contains("1984")) &&
            (aTitle.contains("EVA") || aTitle.contains("新世纪福音战士") || aTitle.contains("来自深渊") || aTitle.contains("夏日重现"))
        ) {
            return Pair("存在主义思辨 · 终极孤独", 94)
        }

        // 2. 爱的驯服与治愈救赎（《小王子》/《解忧杂货店》 vs 《紫罗兰永恒花园》/《夏目友人帐》）
        if ((aTitle.contains("小王子") || aTitle.contains("解忧杂货店")) &&
            (bTitle.contains("紫罗兰") || bTitle.contains("夏目友人帐")) ||
            (bTitle.contains("小王子") || bTitle.contains("解忧杂货店")) &&
            (aTitle.contains("紫罗兰") || aTitle.contains("夏目友人帐"))
        ) {
            return Pair("爱的驯服 · 治愈救赎", 96)
        }

        // 3. 宇宙宿命与宏大哲思（《三体》/《时间简史》 vs 《魔法少女小圆》/《EVA》）
        if ((aTitle.contains("三体") || aTitle.contains("时间简史") || aTitle.contains("上帝掷骰子")) &&
            (bTitle.contains("小圆") || bTitle.contains("EVA") || bTitle.contains("命运石之门")) ||
            (bTitle.contains("三体") || bTitle.contains("时间简史") || bTitle.contains("上帝掷骰子")) &&
            (aTitle.contains("小圆") || aTitle.contains("EVA") || aTitle.contains("命运石之门"))
        ) {
            return Pair("宇宙宿命 · 哲学神域", 93)
        }

        // 4. 青春悸动与灵魂追寻（《挪威的森林》/《边城》 vs 《孤独摇滚！》/《春物》/《强风吹拂》）
        if ((aTitle.contains("挪威的森林") || aTitle.contains("边城") || aTitle.contains("在细雨中呼喊")) &&
            (bTitle.contains("孤独摇滚") || bTitle.contains("春物") || bTitle.contains("青春恋爱物语") || bTitle.contains("强风吹拂")) ||
            (bTitle.contains("挪威的森林") || bTitle.contains("边城") || bTitle.contains("在细雨中呼喊")) &&
            (aTitle.contains("孤独摇滚") || aTitle.contains("春物") || aTitle.contains("青春恋爱物语") || aTitle.contains("强风吹拂"))
        ) {
            return Pair("青春羁绊 · 精神共鸣", 91)
        }

        // 5. 智斗推演与人性暗涌（《白夜行》/《恶意外》/《无人生还》 vs 《夏日重现》/《蓝色监狱》）
        if ((aTitle.contains("白夜行") || aTitle.contains("恶意") || aTitle.contains("无人生还")) &&
            (bTitle.contains("夏日重现") || bTitle.contains("蓝色监狱") || bTitle.contains("实力至上")) ||
            (bTitle.contains("白夜行") || bTitle.contains("恶意") || bTitle.contains("无人生还")) &&
            (aTitle.contains("夏日重现") || aTitle.contains("蓝色监狱") || aTitle.contains("实力至上"))
        ) {
            return Pair("本格智斗 · 人性推演", 90)
        }

        return null
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

        // 2. 绘制认知星座连线 (普通连线 + 跨媒介极光流光连线)
        edges.forEach { edge ->
            val aMatch = edge.nodeA.matches(activeFilter)
            val bMatch = edge.nodeB.matches(activeFilter)
            val isBothMatch = aMatch && bMatch

            val ax = cx + edge.nodeA.worldX
            val ay = cy + edge.nodeA.worldY
            val bx = cx + edge.nodeB.worldX
            val by = cy + edge.nodeB.worldY

            if (edge.isCrossMedia) {
                // 跨媒介极光流光连线
                val shader = LinearGradient(ax, ay, bx, by, edge.nodeA.colorHex, edge.nodeB.colorHex, Shader.TileMode.CLAMP)
                auroraLinePaint.shader = shader
                auroraLinePaint.alpha = if (isBothMatch) (if (isNight) 230 else 180) else 40
                canvas.drawLine(ax, ay, bx, by, auroraLinePaint)

                // 绘制沿线脉冲能量粒子
                if (isBothMatch) {
                    val pulseRatio = (animPhase * 2f + (edge.similarity % 5) * 0.2f) % 1.0f
                    val px = ax + (bx - ax) * pulseRatio
                    val py = ay + (by - ay) * pulseRatio
                    pulseParticlePaint.color = Color.WHITE
                    pulseParticlePaint.alpha = 240
                    canvas.drawCircle(px, py, dpToPx(3.5f), pulseParticlePaint)

                    // 绘制中间共鸣词胶囊徽标
                    val midX = (ax + bx) / 2f
                    val midY = (ay + by) / 2f
                    val traitText = "✨ ${edge.resonanceTrait} ${edge.similarity}%"
                    badgeTextPaint.textSize = dpToPx(9.5f)
                    badgeTextPaint.color = if (isNight) Color.WHITE else Color.parseColor("#1C1917")

                    val textW = badgeTextPaint.measureText(traitText)
                    val rect = RectF(
                        midX - textW / 2f - dpToPx(6f),
                        midY - dpToPx(9f),
                        midX + textW / 2f + dpToPx(6f),
                        midY + dpToPx(9f),
                    )

                    badgeBgPaint.color = if (isNight) Color.parseColor("#D9231E19") else Color.parseColor("#E6FFFFFF")
                    canvas.drawRoundRect(rect, dpToPx(6f), dpToPx(6f), badgeBgPaint)
                    canvas.drawText(traitText, midX, midY + dpToPx(3.2f), badgeTextPaint)
                }
            } else {
                // 普通同媒介连线
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
            }

            // 星辰核心 (Star Core)
            starCorePaint.color = star.colorHex
            starCorePaint.alpha = if (isMatch) 255 else 50
            canvas.drawCircle(sx, sy, currentRadius, starCorePaint)

            // 高亮选中金环
            if (isSel) {
                linePaint.shader = null
                linePaint.color = Color.parseColor("#D4AF37")
                linePaint.strokeWidth = dpToPx(2f)
                linePaint.alpha = 255
                canvas.drawCircle(sx, sy, currentRadius + dpToPx(4f), linePaint)
            }

            // 文字标牌
            if (isMatch) {
                textPaint.textSize = dpToPx(11f)
                textPaint.color = if (isNight) Color.parseColor("#E8E2D9") else Color.parseColor("#2B2724")
                textPaint.alpha = 230

                val title = if (star.book.title.length > 7) star.book.title.take(6) + "…" else star.book.title
                canvas.drawText("${star.book.mediaType.emoji}$title", sx, sy + currentRadius + dpToPx(13f), textPaint)

                subTextPaint.textSize = dpToPx(9f)
                subTextPaint.color = if (isNight) Color.parseColor("#9E988F") else Color.parseColor("#7A7265")
                subTextPaint.alpha = 180
                val sub = "${star.detectedRegion} · ${star.book.category ?: "名作"}"
                canvas.drawText(sub, sx, sy + currentRadius + dpToPx(24f), subTextPaint)
            }
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
