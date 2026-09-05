package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.MediaType
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

sealed class ConstellationFilter {
    object ALL : ConstellationFilter()
    data class ByMedia(val mediaType: MediaType) : ConstellationFilter()
    data class ByRegion(val regionName: String) : ConstellationFilter()
    object CrossMediaResonance : ConstellationFilter()
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
        var baseRadius: Float,
        val colorHex: Int,
        val isMajorStar: Boolean = false,
        var hasCrossMediaEdge: Boolean = false,
    ) {
        fun matches(filter: ConstellationFilter): Boolean {
            return when (filter) {
                is ConstellationFilter.ALL -> true
                is ConstellationFilter.ByMedia -> book.mediaType == filter.mediaType
                is ConstellationFilter.ByRegion -> {
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

    data class AmbientStar(
        val x: Float,
        val y: Float,
        val size: Float,
        val baseAlpha: Float,
        val phase: Float,
    )

    private val stars = mutableListOf<StarNode>()
    private val edges = mutableListOf<ConstellationEdge>()
    private val ambientStars = mutableListOf<AmbientStar>()

    // 变换与交互参数
    private var offsetX = 0f
    private var offsetY = 0f
    private var scaleFactor = 1.0f
    private var animPhase = 0f

    private var activeFilter: ConstellationFilter = ConstellationFilter.ALL
    private var selectedStar: StarNode? = null
    var onStarClickListener: ((Book, BookMindprint) -> Unit)? = null

    // 画笔系统
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val auroraLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val pulseParticlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // 动画驱动 (深空呼吸与微粒闪烁)
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 9000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animPhase = it.animatedValue as Float
            invalidate()
        }
    }

    // 手势系统（以缩放中心为锚点的平滑捏合缩放 + 惯性拖拽）
    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scaleFactor
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.35f, 3.0f)

                // 保持缩放中心点在手势焦点处，避免视觉跳跃
                val focusX = detector.focusX - width / 2f
                val focusY = detector.focusY - height / 2f
                offsetX = (offsetX - focusX) * (scaleFactor / oldScale) + focusX
                offsetY = (offsetY - focusY) * (scaleFactor / oldScale) + focusY

                constrainOffset()
                invalidate()
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                offsetX -= distanceX
                offsetY -= distanceY
                constrainOffset()
                invalidate()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val touchX = e.x
                val touchY = e.y
                val cx = width / 2f + offsetX
                val cy = height / 2f + offsetY

                // 触摸热区判定 (扩大至 32dp 以便手指精准拾取星辰)
                var nearestStar: StarNode? = null
                var minDist = dpToPx(32f) * scaleFactor.coerceAtLeast(0.7f)

                stars.forEach { star ->
                    if (star.matches(activeFilter)) {
                        val sx = cx + star.worldX * scaleFactor
                        val sy = cy + star.worldY * scaleFactor
                        val dist = hypot(touchX - sx, touchY - sy)
                        if (dist < minDist) {
                            minDist = dist
                            nearestStar = star
                        }
                    }
                }

                if (nearestStar != null) {
                    selectedStar = nearestStar
                    onStarClickListener?.invoke(nearestStar!!.book, nearestStar!!.mindprint)
                    smoothFocusOn(nearestStar!!.worldX, nearestStar!!.worldY)
                } else {
                    selectedStar = null
                    invalidate()
                }
                return true
            }
        },
    )

    init {
        // 生成深空背景微光星屑 (140颗随机分布的微粒，营造宇宙深邃感)
        val rnd = Random(42)
        for (i in 0 until 140) {
            ambientStars.add(
                AmbientStar(
                    x = (rnd.nextFloat() - 0.5f) * 3600f,
                    y = (rnd.nextFloat() - 0.5f) * 3600f,
                    size = dpToPx(0.7f + rnd.nextFloat() * 1.6f),
                    baseAlpha = 0.12f + rnd.nextFloat() * 0.40f,
                    phase = rnd.nextFloat() * 6.283f,
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
        smoothFocusOn(target.worldX, target.worldY)
    }

    /**
     * 获取与当前书籍关联的跨媒介共鸣伙伴星辰与连线
     */
    fun getCrossMediaResonancePeer(bookId: Long): Pair<StarNode, ConstellationEdge>? {
        val edge = edges.firstOrNull {
            it.isCrossMedia && (it.nodeA.book.id == bookId || it.nodeB.book.id == bookId)
        } ?: return null

        val peer = if (edge.nodeA.book.id == bookId) edge.nodeB else edge.nodeA
        return Pair(peer, edge)
    }

    private fun smoothFocusOn(targetWorldX: Float, targetWorldY: Float) {
        val startX = offsetX
        val startY = offsetY
        val destX = -targetWorldX * scaleFactor
        val destY = -targetWorldY * scaleFactor

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                offsetX = startX + (destX - startX) * f
                offsetY = startY + (destY - startY) * f
                constrainOffset()
                invalidate()
            }
        }
        anim.start()
    }

    private fun constrainOffset() {
        val maxBound = 2200f * scaleFactor
        offsetX = offsetX.coerceIn(-maxBound, maxBound)
        offsetY = offsetY.coerceIn(-maxBound, maxBound)
    }

    /**
     * 核心星系生成算法：
     * 1. 划分 4 大文化星系旋臂 (文学星云、动漫星团、光影银河、第九艺术星域)
     * 2. 采用黄金螺旋角 (137.5°) + 六维心智势能散落，彻底杜绝 211 节点互相重叠
     * 3. 严格控制骨干连线与跨媒介极光流光弦密度
     */
    fun setBooksData(books: List<Book>, databaseHelper: BookDatabaseHelper) {
        setBooksData(books, databaseHelper.getAllMindprints())
    }

    fun setBooksData(books: List<Book>, mindprintMap: Map<Long, BookMindprint>) {
        stars.clear()
        edges.clear()
        if (books.isEmpty()) {
            invalidate()
            return
        }

        // 四大文化星区世界中心坐标配置 (分布于广袤深空，互不交叠)
        val clusterCenters = mapOf(
            MediaType.BOOK to Pair(-dpToPx(380f), -dpToPx(320f)), // 左上：典雅文学星云
            MediaType.ANIME to Pair(dpToPx(380f), -dpToPx(320f)), // 右上：梦幻动漫星团
            MediaType.MOVIE to Pair(-dpToPx(360f), dpToPx(360f)), // 左下：沉浸影视银河
            MediaType.GAME to Pair(dpToPx(380f), dpToPx(340f)), // 右下：赛博游戏星海
            MediaType.MUSIC to Pair(0f, 0f), // 中央：音乐旋律星核
        )

        val mediaPalette = mapOf(
            MediaType.BOOK to Color.parseColor("#E07A5F"), // 珊瑚琥珀
            MediaType.ANIME to Color.parseColor("#9B5DE5"), // 幻境紫罗兰
            MediaType.MOVIE to Color.parseColor("#F4A261"), // 光影落日金
            MediaType.GAME to Color.parseColor("#00BBF9"), // 赛博冰川青
            MediaType.MUSIC to Color.parseColor("#81B29A"), // 灵息翡翠绿
        )

        // 1. 生成星辰节点（基于多维心智势能 + 斐波那契黄金螺旋分布）
        val mediaGroups = books.groupBy { it.mediaType }

        mediaGroups.forEach { (mediaType, list) ->
            val center = clusterCenters[mediaType] ?: Pair(0f, 0f)
            val baseColor = mediaPalette[mediaType] ?: Color.parseColor("#E07A5F")

            list.forEachIndexed { index, book ->
                val mp = mindprintMap[book.id] ?: BookMindprint(bookId = book.id)

                // 黄金角散落算法：按索引平滑扩展半径，彻底消除重叠
                val goldenAngle = 2.399963f // 137.5 度
                val dist = dpToPx(42f) + dpToPx(18.5f) * sqrt(index.toFloat() + 1f) * (1f + (index % 4) * 0.12f)
                val theta = index * goldenAngle + (mp.depthScore - mp.healingScore).toFloat() * 0.08f

                // 六维心智微偏移 (逻辑/理性 vs 情感/感性，深度 vs 治愈)
                val logicOffset = (mp.logicScore - mp.emotionScore).toFloat() * dpToPx(7f)
                val depthOffset = (mp.depthScore - mp.artistryScore).toFloat() * dpToPx(7f)

                val wx = center.first + cos(theta) * dist + logicOffset
                val wy = center.second + sin(theta) * dist + depthOffset

                // 核心知名作品（各星区前 3 部）或高评分神作标记为主要星辰 (Major Star)
                val isMajor = index < 3 || mp.averageScore() >= 9.1

                val node = StarNode(
                    book = book,
                    mindprint = mp,
                    worldX = wx,
                    worldY = wy,
                    baseRadius = if (isMajor) dpToPx(6.0f) else dpToPx(3.8f),
                    colorHex = baseColor,
                    isMajorStar = isMajor,
                )
                stars.add(node)
            }
        }

        // 2. 生成星系骨干连线（严格控制密度，构建优美星座骨架，杜绝蛛网灾难）
        val majorStars = stars.filter { it.isMajorStar }
        for (i in majorStars.indices) {
            for (j in i + 1 until majorStars.size) {
                val a = majorStars[i]
                val b = majorStars[j]

                // 同媒介主星连线 (近距离星座主干)
                if (a.book.mediaType == b.book.mediaType) {
                    val dist = hypot(a.worldX - b.worldX, a.worldY - b.worldY)
                    if (dist < dpToPx(340f) && edges.count { !it.isCrossMedia && (it.nodeA == a || it.nodeB == a) } < 3) {
                        edges.add(ConstellationEdge(a, b, 88))
                    }
                } else {
                    // 跨媒介灵魂共鸣连线 (控制最多 10~14 条极具哲思的极光光弦)
                    val crossTrait = detectCrossMediaTrait(a, b)
                    if (crossTrait != null && edges.count { it.isCrossMedia } < 12) {
                        a.hasCrossMediaEdge = true
                        b.hasCrossMediaEdge = true
                        edges.add(
                            ConstellationEdge(
                                nodeA = a,
                                nodeB = b,
                                similarity = crossTrait.second,
                                isCrossMedia = true,
                                resonanceTrait = crossTrait.first,
                            ),
                        )
                    }
                }
            }
        }

        invalidate()
    }

    private fun detectCrossMediaTrait(a: StarNode, b: StarNode): Pair<String, Int>? {
        fun matchesPair(k1: List<String>, k2: List<String>): Boolean {
            val aStr = "${a.book.title} ${a.book.author.orEmpty()} ${a.book.category.orEmpty()}"
            val bStr = "${b.book.title} ${b.book.author.orEmpty()} ${b.book.category.orEmpty()}"
            val m1 = k1.any { aStr.contains(it, ignoreCase = true) } && k2.any { bStr.contains(it, ignoreCase = true) }
            val m2 = k2.any { aStr.contains(it, ignoreCase = true) } && k1.any { bStr.contains(it, ignoreCase = true) }
            return m1 || m2
        }

        if (matchesPair(listOf("百年孤独", "鼠疫", "1984", "局外人"), listOf("EVA", "新世纪福音战士", "进击的巨人", "艾尔登法环"))) {
            return Pair("存在主义思辨 · 终极救赎", 98)
        }
        if (matchesPair(listOf("小王子", "边城", "月亮与六便士"), listOf("紫罗兰永恒花园", "夏目友人帐", "哈尔的移动城堡", "去月球"))) {
            return Pair("跨越时空 · 纯真之爱", 97)
        }
        if (matchesPair(listOf("三体", "时间简史", "银河帝国"), listOf("星际穿越", "盗梦空间", "命运石之门", "星际拓荒"))) {
            return Pair("时空维度 · 宏大哲思", 96)
        }
        if (matchesPair(listOf("活着", "老人与海"), listOf("肖申克的救赎", "黑神话：悟空", "只狼", "JOJO"))) {
            return Pair("逆境抗争 · 绝境孤勇", 96)
        }
        if (matchesPair(listOf("教父", "白夜行", "罪与罚"), listOf("女神异闻录5", "无间道", "极乐迪斯科"))) {
            return Pair("人性博弈 · 宿命抉择", 95)
        }
        if (matchesPair(listOf("晴る", "アポリア", "斜陽", "アルジャーノン", "月光浴"), listOf("葬送的芙莉莲", "关于地球的运动", "我心里危险的东西", "紫罗兰永恒花园", "小王子"))) {
            return Pair("物哀音律 · 跨次元共鸣", 99)
        }
        if (matchesPair(listOf("嘘じゃない", "花一匁", "残機", "不法侵入"), listOf("电锯人", "我的鬼女孩", "胆大党", "孤独摇滚", "女神异闻录5", "黑神话：悟空"))) {
            return Pair("夜行放克 · 疾走觉醒", 98)
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleGestureDetector.onTouchEvent(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)
        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        // 1. 深邃星空背景 (深黑曜空 / 唯美暖宣纸)
        if (isNight) {
            canvas.drawColor(Color.parseColor("#080B11"))
        } else {
            canvas.drawColor(Color.parseColor("#F5F1E8"))
        }

        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY

        // 2. 绘制深空背景微光星屑 (微弱呼吸，营造星海景深)
        ambientStars.forEach { p ->
            val px = cx + p.x * scaleFactor
            val py = cy + p.y * scaleFactor
            if (px in -20f..(width + 20f) && py in -20f..(height + 20f)) {
                val twinkle = 0.35f + 0.65f * sin(animPhase * 6.283f * 2.2f + p.phase)
                particlePaint.color = if (isNight) Color.WHITE else Color.parseColor("#9E9282")
                particlePaint.alpha = ((p.baseAlpha * twinkle) * (if (isNight) 160 else 70)).toInt().coerceIn(8, 255)
                canvas.drawCircle(px, py, p.size * scaleFactor.coerceIn(0.6f, 1.4f), particlePaint)
            }
        }

        // 3. 绘制星系四大星区星云光晕 (Nebula ambient glows)
        drawNebulaGlows(canvas, cx, cy, isNight)

        // 4. 绘制星座骨干连线
        edges.forEach { edge ->
            val aMatch = edge.nodeA.matches(activeFilter)
            val bMatch = edge.nodeB.matches(activeFilter)
            if (!aMatch || !bMatch) return@forEach

            val ax = cx + edge.nodeA.worldX * scaleFactor
            val ay = cy + edge.nodeA.worldY * scaleFactor
            val bx = cx + edge.nodeB.worldX * scaleFactor
            val by = cy + edge.nodeB.worldY * scaleFactor

            // 视界粗剔除 (两条端点均在视口外太远则跳过)
            val minX = minOf(ax, bx)
            val maxX = maxOf(ax, bx)
            val minY = minOf(ay, by)
            val maxY = maxOf(ay, by)
            if (maxX < -50f || minX > width + 50f || maxY < -50f || minY > height + 50f) return@forEach

            val isEdgeConnectedToSelected = selectedStar != null && (edge.nodeA == selectedStar || edge.nodeB == selectedStar)
            val isDimmed = selectedStar != null && !isEdgeConnectedToSelected

            if (edge.isCrossMedia) {
                // 跨媒介极光流光弦
                val shader = LinearGradient(ax, ay, bx, by, edge.nodeA.colorHex, edge.nodeB.colorHex, Shader.TileMode.CLAMP)
                auroraLinePaint.shader = shader
                auroraLinePaint.strokeWidth = dpToPx(if (isEdgeConnectedToSelected) 1.8f else 0.9f) * scaleFactor.coerceIn(0.5f, 1.8f)
                auroraLinePaint.alpha = if (isEdgeConnectedToSelected) (if (isNight) 220 else 180) else (if (isDimmed) 18 else (if (isNight) 110 else 75))
                canvas.drawLine(ax, ay, bx, by, auroraLinePaint)

                // 极光脉冲能量光斑 (沿连线流动)
                if (!isDimmed || isEdgeConnectedToSelected) {
                    val pulseRatio = (animPhase * 1.8f + (edge.similarity % 5) * 0.2f) % 1.0f
                    val px = ax + (bx - ax) * pulseRatio
                    val py = ay + (by - ay) * pulseRatio
                    pulseParticlePaint.color = Color.WHITE
                    pulseParticlePaint.alpha = if (isEdgeConnectedToSelected) 255 else (if (isNight) 160 else 110)
                    canvas.drawCircle(px, py, dpToPx(if (isEdgeConnectedToSelected) 2.8f else 1.8f) * scaleFactor.coerceIn(0.6f, 1.5f), pulseParticlePaint)
                }
            } else {
                // 星座骨架常态连线 (极其克制细腻的淡光)
                linePaint.color = if (isNight) Color.parseColor("#506072") else Color.parseColor("#BDB2A3")
                linePaint.strokeWidth = dpToPx(if (isEdgeConnectedToSelected) 1.2f else 0.6f) * scaleFactor.coerceIn(0.5f, 1.5f)
                linePaint.alpha = if (isEdgeConnectedToSelected) (if (isNight) 180 else 130) else (if (isDimmed) 10 else (if (isNight) 50 else 35))
                canvas.drawLine(ax, ay, bx, by, linePaint)
            }
        }

        // 5. 绘制星辰节点 (Star Nodes) - 纯净星空美学，绝不堆叠杂乱文字！
        stars.forEach { star ->
            val isMatch = star.matches(activeFilter)
            val isSel = star == selectedStar
            val isNeighbor = selectedStar != null && edges.any {
                (it.nodeA == selectedStar && it.nodeB == star) || (it.nodeB == selectedStar && it.nodeA == star)
            }
            val isDimmed = selectedStar != null && !isSel && !isNeighbor

            val sx = cx + star.worldX * scaleFactor
            val sy = cy + star.worldY * scaleFactor

            // 视界严格裁剪 (不在屏幕范围的节点不绘制)
            if (sx < -60f || sx > width + 60f || sy < -60f || sy > height + 60f) return@forEach

            val breathe = if (isMatch) (1f + 0.08f * sin(animPhase * 6.283f * 2f + star.worldX * 0.02f)) else 1.0f
            val baseR = if (isSel) dpToPx(9.0f) else if (isNeighbor) dpToPx(6.2f) else star.baseRadius
            val curRadius = baseR * scaleFactor.coerceIn(0.5f, 1.8f) * breathe

            // 发光光晕 (Radial Glow)
            if (isMatch && (!isDimmed || isSel)) {
                val glowRadius = curRadius * (if (isSel) 3.6f else if (isNeighbor) 2.4f else 2.0f)
                val glowAlpha = if (isSel) 190 else if (isNeighbor) 120 else (if (isNight) 70 else 40)
                val glowColor = Color.argb(
                    glowAlpha,
                    Color.red(star.colorHex),
                    Color.green(star.colorHex),
                    Color.blue(star.colorHex),
                )
                val transparentGlow = Color.argb(0, Color.red(star.colorHex), Color.green(star.colorHex), Color.blue(star.colorHex))

                val glowShader = RadialGradient(sx, sy, glowRadius, glowColor, transparentGlow, Shader.TileMode.CLAMP)
                starGlowPaint.shader = glowShader
                canvas.drawCircle(sx, sy, glowRadius, starGlowPaint)
            }

            // 星辰核心 (Star Core)
            starCorePaint.color = if (isSel) Color.WHITE else star.colorHex
            starCorePaint.alpha = if (isMatch) (if (isDimmed) 30 else 255) else 15
            canvas.drawCircle(sx, sy, curRadius, starCorePaint)

            // 选中时的发光星环 (Orbit Focus Ring)
            if (isSel) {
                ringPaint.color = Color.parseColor("#FFD166")
                ringPaint.strokeWidth = dpToPx(1.8f)
                ringPaint.alpha = 240
                val orbitRadius = curRadius + dpToPx(5.5f) * scaleFactor.coerceIn(0.6f, 1.5f)
                canvas.drawCircle(sx, sy, orbitRadius, ringPaint)
            }

            // 6. 动态 LOD 智能标签渲染：
            // 全景常态下绝不铺满文字！
            // 只有当：① 被选中(isSel) ② 1度关联星辰(isNeighbor) ③ 关键主要主星且放大至0.9x以上 ④ 深度放大至1.4x以上 时才绘制精巧微标
            val shouldShowLabel = isMatch && (isSel || isNeighbor || (star.isMajorStar && scaleFactor >= 0.9f) || scaleFactor >= 1.4f)
            if (shouldShowLabel && !isDimmed) {
                drawStarLabel(canvas, star, sx, sy, curRadius, isSel, isNeighbor, isNight)
            }
        }
    }

    private fun drawNebulaGlows(canvas: Canvas, cx: Float, cy: Float, isNight: Boolean) {
        val nebulaCenters = listOf(
            Triple(-dpToPx(380f), -dpToPx(320f), Color.parseColor("#E07A5F")),
            Triple(dpToPx(380f), -dpToPx(320f), Color.parseColor("#9B5DE5")),
            Triple(-dpToPx(360f), dpToPx(360f), Color.parseColor("#F4A261")),
            Triple(dpToPx(380f), dpToPx(340f), Color.parseColor("#00BBF9")),
        )

        nebulaCenters.forEach { (nx, ny, color) ->
            val gx = cx + nx * scaleFactor
            val gy = cy + ny * scaleFactor
            val radius = dpToPx(280f) * scaleFactor
            val glowAlpha = if (isNight) 22 else 12
            val glowColor = Color.argb(glowAlpha, Color.red(color), Color.green(color), Color.blue(color))
            val glowShader = RadialGradient(gx, gy, radius, glowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            particlePaint.shader = glowShader
            canvas.drawCircle(gx, gy, radius, particlePaint)
        }
        particlePaint.shader = null
    }

    /**
     * 极简高质感微标绘制 (带有圆润阴影胶囊与清爽文本)
     */
    private fun drawStarLabel(
        canvas: Canvas,
        star: StarNode,
        sx: Float,
        sy: Float,
        radius: Float,
        isSelected: Boolean,
        isNeighbor: Boolean,
        isNight: Boolean,
    ) {
        val maxLen = if (isSelected) 14 else 7
        val rawTitle = star.book.title
        val title = if (rawTitle.length > maxLen) rawTitle.take(maxLen - 1) + "…" else rawTitle
        val displayStr = "${star.book.mediaType.emoji} $title"

        labelTextPaint.textSize = dpToPx(if (isSelected) 11.5f else if (isNeighbor) 9.5f else 8.5f)
        labelTextPaint.color = if (isNight) {
            if (isSelected) Color.parseColor("#FFFFFF") else Color.parseColor("#E6E1D8")
        } else {
            if (isSelected) Color.parseColor("#141312") else Color.parseColor("#3C342C")
        }

        val textWidth = labelTextPaint.measureText(displayStr)
        val textHeight = labelTextPaint.textSize
        val padH = dpToPx(if (isSelected) 6f else 4.5f)
        val padV = dpToPx(if (isSelected) 3f else 2f)

        val rectY = sy + radius + dpToPx(3.5f)
        val rect = RectF(
            sx - textWidth / 2f - padH,
            rectY,
            sx + textWidth / 2f + padH,
            rectY + textHeight + padV * 2,
        )

        // 标签胶囊背景
        labelBgPaint.color = if (isSelected) {
            if (isNight) Color.parseColor("#E6262420") else Color.parseColor("#F5FFFFFF")
        } else {
            if (isNight) Color.parseColor("#9912151D") else Color.parseColor("#B3FFFFFF")
        }
        canvas.drawRoundRect(rect, dpToPx(4f), dpToPx(4f), labelBgPaint)

        // 选中时给胶囊加一圈金色细边框
        if (isSelected) {
            ringPaint.color = Color.parseColor("#FFD166")
            ringPaint.strokeWidth = dpToPx(1f)
            ringPaint.alpha = 200
            canvas.drawRoundRect(rect, dpToPx(4f), dpToPx(4f), ringPaint)
        }

        // 绘制文字 (纵向居中)
        canvas.drawText(displayStr, sx, rectY + textHeight + padV * 0.4f, labelTextPaint)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}

