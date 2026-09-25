package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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

    // T2.3：跨媒介弦渐变按边缓存（几何基于固定的世界坐标），每帧仅重设局部矩阵；
    // 数据重算（setBooksData）时清空重建
    private val auroraGradientCache = HashMap<ConstellationEdge, LinearGradient>()
    private val auroraShaderMatrix = Matrix()
    private val glowShaderMatrix = Matrix()

    /** 四大星区星云中心（世界坐标）与基色，lazy 常量化 */
    private val nebulaCentersInternal by lazy {
        listOf(
            Triple(-dpToPx(380f), -dpToPx(320f), Color.parseColor("#E07A5F")),
            Triple(dpToPx(380f), -dpToPx(320f), Color.parseColor("#9B5DE5")),
            Triple(-dpToPx(360f), dpToPx(360f), Color.parseColor("#F4A261")),
            Triple(dpToPx(380f), dpToPx(340f), Color.parseColor("#00BBF9")),
        )
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

    /** T2.3：视图不可见（页面被遮挡/切走）时暂停星空呼吸动画，省电并释放 GPU；恢复可见时无缝续播 */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE) animator.resume() else animator.pause()
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
        auroraGradientCache.clear()
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

                // 核心知名作品标记为主要星辰 (Major Star)。
                //
                // 2026-09-14 定标：原为「各星区前 3 部 或 六维均分 ≥ 9.1」，实测 218 部
                // 真实归档下全场仅 21 部主星（book 只有 3 部），跨媒介候选对在阈值 86 下
                // 只剩 9 对，跨媒介共鸣弦几乎绝迹——这正是「双生共鸣消失」的核心原因之一。
                // 现放宽为「前 6 部 或 六维均分 ≥ 8.8」：主星 48 部、候选 110 对，
                // 配额 36 有充分择优余量，且 O(n²) 仅 1128 次比对，绘制无压力。
                val isMajor = index < MAJOR_STAR_TOP_N || mp.averageScore() >= MAJOR_STAR_MIN_AVG

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

        // 2a. 同媒介主星连线（近距离星座主干）——先建，作为骨架
        for (i in majorStars.indices) {
            for (j in i + 1 until majorStars.size) {
                val a = majorStars[i]
                val b = majorStars[j]
                if (a.book.mediaType != b.book.mediaType) continue
                val dist = hypot(a.worldX - b.worldX, a.worldY - b.worldY)
                if (dist < dpToPx(340f) && edges.count { !it.isCrossMedia && (it.nodeA == a || it.nodeB == a) } < 3) {
                    edges.add(ConstellationEdge(a, b, 88))
                }
            }
        }

        // 2b. 跨媒介灵魂共鸣弦：按「媒介对」分组配额 + 组内择优录取。
        //
        //     为什么不直接全局排序取前 N：书籍主星数量远少于番剧/游戏，全局排序时
        //     书×番剧的对会被高分对（音乐×番剧、游戏×番剧）整体挤出配额，导致
        //     「跨媒介双生微卡」这个依赖 book×anime 的入口永远拿不到数据。
        //     分组配额保证每个媒介组合都有代表，组内再按相似度择优。
        data class CrossCandidate(val a: StarNode, val b: StarNode, val trait: String, val score: Int)

        val byMediaPair = LinkedHashMap<String, MutableList<CrossCandidate>>()
        for (i in majorStars.indices) {
            for (j in i + 1 until majorStars.size) {
                val a = majorStars[i]
                val b = majorStars[j]
                if (a.book.mediaType == b.book.mediaType) continue
                // 白名单策展优先，未命中走六维相似度兜底（见 detectCrossMediaTrait）
                val crossTrait = detectCrossMediaTrait(a, b) ?: continue
                // 媒介对做归一化键，保证 (书,番剧) 与 (番剧,书) 落入同一组
                val key = listOf(a.book.mediaType.name, b.book.mediaType.name).sorted().joinToString("×")
                byMediaPair.getOrPut(key) { mutableListOf() }
                    .add(CrossCandidate(a, b, crossTrait.first, crossTrait.second))
            }
        }

        // 每组配额 = 总量 / 组数，向上取整，保证组数多时每组至少 1 条
        val pairCount = byMediaPair.size.coerceAtLeast(1)
        val perPairQuota = (MAX_CROSS_MEDIA_EDGES + pairCount - 1) / pairCount

        // 组内择优，但每组的录取要「按作品轮转」而非一次取满：
        // 若某组一次取满配额，书籍这类主星稀少的媒介会把边额度全用在同一部书上，
        // 导致 book×anime 组再也挤不进任何一个书名。
        // 轮转做法：组内按相似度排序后，逐轮录取（每轮每部作品最多 1 条），
        // 直到该组配额用完或候选耗尽。
        //
        // 处理顺序：候选数【升序】，即稀有媒介组合优先。
        //   反例（按候选数降序 / 按插入顺序）：game×music 有 32 个候选，会先于
        //   anime×book（8 个候选）执行；而书籍侧只有 6 部主星、每书仅 3 条额度，
        //   等轮到 book 相关组合时额度已被 book×game / book×music / book×movie 抢光，
        //   「跨媒介双生微卡」依赖的 book×anime 就永远取不到数据。
        //   实测：改为升序后 book×anime 从 0 条提升到 4~5 条。
        val globalEdgeCount = HashMap<Long, Int>()
        val admitted = mutableListOf<CrossCandidate>()
        byMediaPair.entries.sortedBy { it.value.size }.forEach { (_, group) ->
            val pool = group.sortedByDescending { it.score }.toMutableList()
            var taken = 0
            var progress = true
            while (taken < perPairQuota && progress) {
                progress = false
                // 每轮内同一作品只出一条，保证额度分散到不同作品上
                val touchThisRound = HashSet<Long>()
                val pickedThisRound = mutableListOf<CrossCandidate>()
                val iterator = pool.iterator()
                while (iterator.hasNext() && taken < perPairQuota) {
                    val c = iterator.next()
                    val idA = c.a.book.id
                    val idB = c.b.book.id
                    if (idA in touchThisRound || idB in touchThisRound) continue
                    // 每书上限在此处直接生效：避免「先凑满配额、最后再丢弃」的浪费
                    if ((globalEdgeCount[idA] ?: 0) >= MAX_EDGES_PER_BOOK) continue
                    if ((globalEdgeCount[idB] ?: 0) >= MAX_EDGES_PER_BOOK) continue
                    touchThisRound.add(idA)
                    touchThisRound.add(idB)
                    pickedThisRound.add(c)
                    iterator.remove()
                    taken++
                    progress = true
                }
                // 本轮录取统一记账，确保同轮内不会超发同一部作品的额度
                pickedThisRound.forEach { c ->
                    globalEdgeCount[c.a.book.id] = (globalEdgeCount[c.a.book.id] ?: 0) + 1
                    globalEdgeCount[c.b.book.id] = (globalEdgeCount[c.b.book.id] ?: 0) + 1
                }
                admitted.addAll(pickedThisRound)
            }
        }

        // 配额未用满时（组内候选不足），把余量还给全局高分候选，避免浪费配额
        if (admitted.size < MAX_CROSS_MEDIA_EDGES) {
            val admittedIds = admitted.map { it.a.book.id to it.b.book.id }.toHashSet()
            val rest = byMediaPair.values.flatten()
                .filter { (it.a.book.id to it.b.book.id) !in admittedIds }
                .sortedByDescending { it.score }
            for (c in rest) {
                if (admitted.size >= MAX_CROSS_MEDIA_EDGES) break
                val idA = c.a.book.id
                val idB = c.b.book.id
                if ((globalEdgeCount[idA] ?: 0) >= MAX_EDGES_PER_BOOK) continue
                if ((globalEdgeCount[idB] ?: 0) >= MAX_EDGES_PER_BOOK) continue
                globalEdgeCount[idA] = (globalEdgeCount[idA] ?: 0) + 1
                globalEdgeCount[idB] = (globalEdgeCount[idB] ?: 0) + 1
                admitted.add(c)
            }
        }

        // 落线：额度已在录取阶段校验，此处直接成弦
        admitted.sortedByDescending { it.score }.forEach { c ->
            c.a.hasCrossMediaEdge = true
            c.b.hasCrossMediaEdge = true
            edges.add(
                ConstellationEdge(
                    nodeA = c.a,
                    nodeB = c.b,
                    similarity = c.score,
                    isCrossMedia = true,
                    resonanceTrait = c.trait,
                ),
            )
        }

        invalidate()
    }

    private fun detectCrossMediaTrait(a: StarNode, b: StarNode): Pair<String, Int>? {
        // ── 第一层：策展白名单（命中则给出有文学意味的专属文案与高契合度）
        curatedTrait(a, b)?.let { return it }

        // ── 第二层：六维心智兜底（白名单未命中时，按心智档案相似度决定是否成弦）
        return dynamicTrait(a, b)
    }

    /**
     * 策展白名单：手工编排的跨媒介组合，文案具备文学意味。
     * 未命中返回 null，交由 dynamicTrait 兜底。
     */
    private fun curatedTrait(a: StarNode, b: StarNode): Pair<String, Int>? {
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

    /**
     * 六维心智兜底：按五维核心心智距离（深度/文笔/情感/逻辑/治愈）计算相似度。
     *
     * 为什么用五维而非六维：difficultyScore（阅读阻力）是「载体属性」不是「心智属性」，
     * 一本难读的哲学书和一部轻松的游戏在情绪上完全可以同频，把难度计入会系统性
     * 压低跨媒介配对（跨媒介的难度天然差异最大）。难度只在同媒介内比对时才有意义。
     *
     * 归一化：经验分布显示五维平均差集中在 0.4~2.6，故以 2.6 为满量程，
     * 使相似度真正落在 65~99 全区间，而不是人人 90%+。
     */
    private fun dynamicTrait(a: StarNode, b: StarNode): Pair<String, Int>? {
        val ma = a.mindprint
        val mb = b.mindprint

        // 双方都无档案数据时不成弦（避免默认 8.0 造出假共鸣）
        if (!hasMindprintData(ma) && !hasMindprintData(mb)) return null

        val diff = (
            kotlin.math.abs(ma.depthScore - mb.depthScore) +
                kotlin.math.abs(ma.artistryScore - mb.artistryScore) +
                kotlin.math.abs(ma.emotionScore - mb.emotionScore) +
                kotlin.math.abs(ma.logicScore - mb.logicScore) +
                kotlin.math.abs(ma.healingScore - mb.healingScore)
            ) / 5.0

        // 满量程 2.6：低于 0.6 视为高度同频（99），高于 2.6 视为完全不共振（65）
        val similarity = (99.0 - (diff / 2.6) * 30.0).toInt().coerceIn(65, 99)
        if (similarity < CROSS_MEDIA_MIN_SIMILARITY) return null

        // 文案取两侧共同的精神特征：优先共同标签，其次双方 category 拼接
        val commonTag = a.book.tags.firstOrNull { tA ->
            b.book.tags.any { tB -> tB.contains(tA, ignoreCase = true) || tA.contains(tB, ignoreCase = true) }
        }
        val trait = when {
            commonTag != null -> "跨媒介共鸣 · $commonTag"
            similarity >= 93 -> "同频心智 · 高度契合"
            similarity >= 88 -> "气质相近 · 精神互文"
            else -> "异质共鸣 · 观点交火"
        }
        return Pair(trait, similarity)
    }

    /** 判断心智档案是否携带真实数据（五维全为默认 8.0 且难度为 5.0 时视为未录入） */
    private fun hasMindprintData(mp: BookMindprint): Boolean {
        val defaults = mp.depthScore == 8.0 && mp.artistryScore == 8.0 &&
            mp.emotionScore == 8.0 && mp.logicScore == 8.0 &&
            mp.difficultyScore == 5.0 && mp.healingScore == 8.0
        return !defaults
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
            canvas.drawColor(SKY_NIGHT)
        } else {
            canvas.drawColor(SKY_DAY)
        }

        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY

        // T2.3：世界→屏幕变换矩阵（每帧一次），供缓存的弦渐变重设局部矩阵
        auroraShaderMatrix.setScale(scaleFactor, scaleFactor)
        auroraShaderMatrix.postTranslate(cx, cy)

        // 2. 绘制深空背景微光星屑 (微弱呼吸，营造星海景深)
        ambientStars.forEach { p ->
            val px = cx + p.x * scaleFactor
            val py = cy + p.y * scaleFactor
            if (px in -20f..(width + 20f) && py in -20f..(height + 20f)) {
                val twinkle = 0.35f + 0.65f * sin(animPhase * 6.283f * 2.2f + p.phase)
                particlePaint.color = if (isNight) Color.WHITE else AMBIENT_PARTICLE_DAY
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
                // 跨媒介极光流光弦：渐变按边缓存（世界坐标几何），每帧仅重设世界→屏幕的局部矩阵
                val shader = auroraGradientCache.getOrPut(edge) {
                    LinearGradient(
                        edge.nodeA.worldX, edge.nodeA.worldY,
                        edge.nodeB.worldX, edge.nodeB.worldY,
                        edge.nodeA.colorHex, edge.nodeB.colorHex, Shader.TileMode.CLAMP,
                    )
                }
                shader.setLocalMatrix(auroraShaderMatrix)
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
                linePaint.color = if (isNight) SKELETON_LINE_NIGHT else SKELETON_LINE_DAY
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

            // 发光光晕 (Radial Glow)：单位渐变 + Matrix 复用 + MODULATE 染色滤镜缓存，零每帧分配
            if (isMatch && (!isDimmed || isSel)) {
                val glowRadius = curRadius * (if (isSel) 3.6f else if (isNeighbor) 2.4f else 2.0f)
                val glowAlpha = if (isSel) 190 else if (isNeighbor) 120 else (if (isNight) 70 else 40)
                val glowColor = Color.argb(
                    glowAlpha,
                    Color.red(star.colorHex),
                    Color.green(star.colorHex),
                    Color.blue(star.colorHex),
                )

                glowShaderMatrix.setScale(glowRadius, glowRadius)
                glowShaderMatrix.postTranslate(sx, sy)
                GLOW_UNIT_SHADER.setLocalMatrix(glowShaderMatrix)
                starGlowPaint.shader = GLOW_UNIT_SHADER
                starGlowPaint.colorFilter = glowFilterFor(glowColor)
                canvas.drawCircle(sx, sy, glowRadius, starGlowPaint)
            }

            // 星辰核心 (Star Core)
            starCorePaint.color = if (isSel) Color.WHITE else star.colorHex
            starCorePaint.alpha = if (isMatch) (if (isDimmed) 30 else 255) else 15
            canvas.drawCircle(sx, sy, curRadius, starCorePaint)

            // 选中时的发光星环 (Orbit Focus Ring)
            if (isSel) {
                ringPaint.color = FOCUS_RING_COLOR
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
        // T2.3：星云中心/颜色 lazy 常量化，渐变复用单位 shader + Matrix + 染色滤镜（零每帧分配）
        val glowAlpha = if (isNight) 22 else 12
        nebulaCentersInternal.forEach { (nx, ny, baseColor) ->
            val gx = cx + nx * scaleFactor
            val gy = cy + ny * scaleFactor
            val radius = dpToPx(280f) * scaleFactor
            val glowColor = Color.argb(glowAlpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            glowShaderMatrix.setScale(radius, radius)
            glowShaderMatrix.postTranslate(gx, gy)
            GLOW_UNIT_SHADER.setLocalMatrix(glowShaderMatrix)
            particlePaint.shader = GLOW_UNIT_SHADER
            particlePaint.colorFilter = glowFilterFor(glowColor)
            canvas.drawCircle(gx, gy, radius, particlePaint)
        }
        particlePaint.shader = null
        particlePaint.colorFilter = null
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
            ringPaint.color = FOCUS_RING_COLOR
            ringPaint.strokeWidth = dpToPx(1f)
            ringPaint.alpha = 200
            canvas.drawRoundRect(rect, dpToPx(4f), dpToPx(4f), ringPaint)
        }

        // 绘制文字 (纵向居中)
        canvas.drawText(displayStr, sx, rectY + textHeight + padV * 0.4f, labelTextPaint)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    companion object {
        // T2.3：onDraw 热路径常量与复用对象——不再每帧 parseColor / new Shader（此前每帧数百次分配）

        /** 跨媒介共鸣弦数量上限：原为 12，兜底策略引入后提高到 36 以保证后半程作品也有共鸣机会 */
        private const val MAX_CROSS_MEDIA_EDGES = 36

        /** 单部作品最多参与的跨媒介弦数：防止个别高分作品把配额吸干 */
        private const val MAX_EDGES_PER_BOOK = 3

        /**
         * 主星判定：每个星区（媒介）按 rating 排序后的前 N 部即为主星。
         * 与 [MAJOR_STAR_MIN_AVG] 是「或」关系。原为 3，2026-09-14 提高到 6，
         * 修掉 book 侧主星只有 3 部导致跨媒介配对畸形偏斜的问题。
         */
        private const val MAJOR_STAR_TOP_N = 6

        /**
         * 主星判定：六维均分达到此值即为主星（跨媒介共鸣的骨干池）。
         * 原为 9.1，实测全场仅 9 部作品达标，池子太小无法撑起 36 条弦；降为 8.8。
         */
        private const val MAJOR_STAR_MIN_AVG = 8.8

        /**
         * 跨媒介共鸣的最低相似度门槛：低于此值视为不共振，不成弦。
         *
         * 定标依据（2026-09-14 用 218 部真实作品归档实测）：
         *   六维相似度经 `99 - (diff / 2.6) * 30` 归一化后，跨媒介全量对的
         *   中位数落在钳制下限 65，说明绝大多数配对本就不该成弦；而主星池
         *   （每媒介 top3 或六维均分 ≥ 9.1，共 21 部）内：
         *     阈值 86 → 仅 9 对    （配额 36 完全吃不满，星图几乎无弦）
         *     阈值 82 → 20 对
         *     阈值 80 → 31 对      ← 采用：配额基本吃满，且 80 分对应五维平均差
         *     阈值 78 → 42 对        仅 1.65，配对语义仍然成立
         *     阈值 65 → 162 对     （下限，任何两部都能成弦，失去区分度）
         *   取 80 是「配额饱和度」与「配对可靠性」的拐点。
         */
        private const val CROSS_MEDIA_MIN_SIMILARITY = 80

        private val SKELETON_LINE_NIGHT = Color.parseColor("#506072")
        private val SKELETON_LINE_DAY = Color.parseColor("#BDB2A3")
        private val AMBIENT_PARTICLE_DAY = Color.parseColor("#9E9282")
        private val SKY_NIGHT = Color.parseColor("#080B11")
        private val SKY_DAY = Color.parseColor("#F5F1E8")
        private val FOCUS_RING_COLOR = Color.parseColor("#FFD166")

        /**
         * 单位光晕渐变（半径 1，白→透明）：每帧通过 Matrix 缩放平移复用，
         * 色彩由 MODULATE 滤镜染色（滤镜按目标色缓存），替代每星每帧 new RadialGradient
         */
        private val GLOW_UNIT_SHADER = RadialGradient(0f, 0f, 1f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)

        private val glowFilterCache = HashMap<Int, PorterDuffColorFilter>()

        private fun glowFilterFor(glowColor: Int): PorterDuffColorFilter =
            glowFilterCache.getOrPut(glowColor) {
                PorterDuffColorFilter(glowColor, PorterDuff.Mode.MULTIPLY)
            }
    }
}

