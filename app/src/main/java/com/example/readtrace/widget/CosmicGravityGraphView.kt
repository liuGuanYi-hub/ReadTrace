package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.SpatialAudioEngine
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🪐 跨媒介认知引力星系物理视图 (Cosmic Gravity Knowledge Graph View)
 * 对标 Cosmos.so / Siteinspire 顶尖引力拓扑与 Awwwards 宇宙可视化：
 * - 将书籍、番剧、电影、游戏、声音构建为具有质量与引力场的引力天体（Cosmic Celestial Bodies）；
 * - 实时运行库仑斥力 + 虎克弹簧引力 + 阻尼减速物理力导向系统；
 * - 手指拖拽天体产生丝绸弹簧物理形变，松手带惯性自然滑行；点击天体触发 528Hz 空灵引力波共鸣并跳转详情。
 */
class CosmicGravityGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class CosmicNode(
        val book: Book,
        var x: Float,
        var y: Float,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var radius: Float = 26f,
        val color: Int = Color.parseColor("#4DEEEA"),
        var isSelected: Boolean = false,
        // 球体/光晕着色器以节点为原点缓存，绘制时仅平移画布，避免每帧重建
        var bodyShader: RadialGradient? = null,
        var haloShader: RadialGradient? = null,
        var displayTitle: String = "",
    )

    data class CosmicEdge(
        val source: CosmicNode,
        val target: CosmicNode,
        val strength: Float = 1.0f,
        val relationship: String = "共鸣引力",
        val crossMedia: Boolean = false,
        // 两端颜色的预混合色，绘制时直接使用，避免每帧创建渐变着色器
        val blendedColor: Int = Color.WHITE,
    )

    /** 候选连线：记录两端节点下标、共振权重与是否跨媒介 */
    private data class CandidateEdge(val a: Int, val b: Int, val weight: Float, val crossMedia: Boolean)

    data class StarDust(
        var x: Float,
        var y: Float,
        var speed: Float,
        var radius: Float,
        var alpha: Int,
    )

    private companion object {
        /** 单个天体允许的最大连线数，防止星轨过密 */
        const val MAX_NODE_DEGREE = 4

        /** 渲染天体数的防御性上限，正常数量由调用方控制 */
        const val MAX_RENDER_NODES = 40
    }

    private val nodes = mutableListOf<CosmicNode>()
    private val edges = mutableListOf<CosmicEdge>()
    private val stardustList = mutableListOf<StarDust>()

    private var draggedNode: CosmicNode? = null
    private var isSimulating = false
    private var physicsAnimator: ValueAnimator? = null
    private var pulsePhase = 0f
    private var isWindowVisible = false

    // 拖拽起点与上一帧位置，用于区分点击/拖拽并产生抛掷惯性
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchLastX = 0f
    private var touchLastY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    var onNodeClickListener: ((Book) -> Unit)? = null

    // 绘制画笔
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val edgeBeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 11f * resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 15f * resources.displayMetrics.scaledDensity
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
        initStardust()
    }

    private fun initStardust() {
        stardustList.clear()
        val random = Random(123)
        for (i in 0 until 28) {
            stardustList.add(
                StarDust(
                    x = random.nextFloat() * 1000f,
                    y = random.nextFloat() * 2000f,
                    speed = random.nextFloat() * 0.3f + 0.08f,
                    radius = random.nextFloat() * 1.6f + 0.6f,
                    alpha = random.nextInt(90) + 30,
                )
            )
        }
    }

    fun setGalaxyData(books: List<Book>) {
        nodes.clear()
        edges.clear()

        if (books.isEmpty()) {
            invalidate()
            return
        }

        val w = if (width > 0) width.toFloat() else 1080f
        val h = if (height > 0) height.toFloat() else 1920f
        val cx = w / 2f
        val cy = h / 2f

        val random = Random(42)
        val density = resources.displayMetrics.density

        // 1. 构建天体节点（清新莫兰迪水墨调色盘）
        val renderBooks = books.take(MAX_RENDER_NODES)
        renderBooks.forEachIndexed { i, book ->
            val angle = (i.toFloat() / renderBooks.size) * 2f * Math.PI.toFloat()
            val dist = 120f * density + random.nextFloat() * 100f * density
            val nx = cx + cos(angle) * dist
            val ny = cy + sin(angle) * dist

            val color = when (book.mediaType) {
                MediaType.MUSIC -> Color.parseColor("#88A4B8") // 霁蓝水墨
                MediaType.BOOK -> Color.parseColor("#C8A265")  // 沉香暖金
                MediaType.ANIME -> Color.parseColor("#D48590") // 浅绯樱粉
                MediaType.MOVIE -> Color.parseColor("#8E80A8") // 幽兰微紫
                MediaType.GAME -> Color.parseColor("#6FA886")  // 苍竹松绿
            }

            nodes.add(
                CosmicNode(
                    book = book,
                    x = nx,
                    y = ny,
                    radius = (24f + (book.rating ?: 4.5).toFloat() * 1.8f) * density,
                    color = color,
                )
            )
        }

        // 预生成节点着色器与展示标题，供绘制阶段缓存复用
        nodes.forEach { node ->
            val r = node.radius
            node.bodyShader = RadialGradient(
                -r * 0.3f, -r * 0.3f, r,
                intArrayOf(Color.WHITE, node.color, Color.parseColor("#141A22")),
                floatArrayOf(0f, 0.70f, 1.0f),
                Shader.TileMode.CLAMP,
            )
            node.haloShader = RadialGradient(
                0f, 0f, r,
                intArrayOf(node.color, Color.TRANSPARENT),
                floatArrayOf(0.35f, 1.0f),
                Shader.TileMode.CLAMP,
            )
            node.displayTitle =
                if (node.book.title.length > 6) node.book.title.substring(0, 5) + "…" else node.book.title
        }

        // 2. 构建跨媒介共鸣星轨：按分类/作者/标签共振生成候选连线，跨媒介连线加权优先，
        //    并限制单节点度数，避免同类型节点两两全连造成视觉噪声
        val candidates = mutableListOf<CandidateEdge>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val b1 = nodes[i].book
                val b2 = nodes[j].book
                var weight = 0f
                if (b1.category != null && b1.category == b2.category) weight += 2f
                if (b1.author != null && b1.author == b2.author) weight += 2f
                val sharedTags = b1.tags.intersect(b2.tags.toSet()).size
                if (sharedTags > 0) weight += sharedTags
                if (weight <= 0f) continue
                val crossMedia = b1.mediaType != b2.mediaType
                if (crossMedia) weight += 1.5f
                candidates.add(CandidateEdge(i, j, weight, crossMedia))
            }
        }

        candidates.sortByDescending { it.weight }
        val degree = IntArray(nodes.size)
        for (candidate in candidates) {
            if (degree[candidate.a] >= MAX_NODE_DEGREE || degree[candidate.b] >= MAX_NODE_DEGREE) continue
            edges.add(
                CosmicEdge(
                    nodes[candidate.a],
                    nodes[candidate.b],
                    strength = if (candidate.crossMedia) 1.0f else 0.7f,
                    relationship = if (candidate.crossMedia) "跨媒介共鸣" else "同域引力",
                    crossMedia = candidate.crossMedia,
                    blendedColor = blendColors(nodes[candidate.a].color, nodes[candidate.b].color),
                )
            )
            degree[candidate.a]++
            degree[candidate.b]++
        }

        // 孤立天体兜底：与最近的同媒介天体相连，保证媒介星团成形
        for (i in nodes.indices) {
            if (degree[i] > 0) continue
            var nearest = -1
            var nearestDist = Float.MAX_VALUE
            for (j in nodes.indices) {
                if (j == i || nodes[j].book.mediaType != nodes[i].book.mediaType) continue
                val d = hypot(nodes[j].x - nodes[i].x, nodes[j].y - nodes[i].y)
                if (d < nearestDist) {
                    nearestDist = d
                    nearest = j
                }
            }
            if (nearest >= 0) {
                edges.add(
                    CosmicEdge(
                        nodes[i],
                        nodes[nearest],
                        strength = 0.5f,
                        relationship = "同域引力",
                        blendedColor = blendColors(nodes[i].color, nodes[nearest].color),
                    )
                )
                degree[i]++
                degree[nearest]++
            }
        }

        invalidate()
    }

    /** 取两端天体颜色的算术混合色，用于连线纯色绘制 */
    private fun blendColors(c1: Int, c2: Int): Int = Color.rgb(
        (Color.red(c1) + Color.red(c2)) / 2,
        (Color.green(c1) + Color.green(c2)) / 2,
        (Color.blue(c1) + Color.blue(c2)) / 2,
    )

    private fun startSimulation() {
        if (physicsAnimator == null) {
            physicsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 16L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    pulsePhase = (pulsePhase + 0.04f) % (2f * Math.PI.toFloat())
                    updatePhysicsStep()
                    updateStardust()
                    invalidate()
                }
            }
        }
        if (physicsAnimator?.isRunning != true) physicsAnimator?.start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 动画循环在挂载后才启动，窗口不可见时自动暂停，避免后台空转
        if (isWindowVisible) startSimulation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        isWindowVisible = visibility == View.VISIBLE
        if (isWindowVisible && isAttachedToWindow) {
            startSimulation()
        } else {
            physicsAnimator?.cancel()
        }
    }

    private fun updatePhysicsStep() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || nodes.isEmpty()) return

        val cx = w / 2f
        val cy = h / 2f
        val kRepulsion = 45000f
        val kSpring = 0.0035f
        val restLength = 110f * resources.displayMetrics.density
        val kCenter = 0.0018f
        val damping = 0.88f

        // 1. 节点间库仑斥力
        for (i in 0 until nodes.size) {
            val n1 = nodes[i]
            for (j in i + 1 until nodes.size) {
                val n2 = nodes[j]
                val dx = n2.x - n1.x
                val dy = n2.y - n1.y
                val dist = hypot(dx, dy).coerceAtLeast(30f)
                val force = kRepulsion / (dist * dist)

                val fx = (dx / dist) * force
                val fy = (dy / dist) * force

                if (n1 !== draggedNode) {
                    n1.vx -= fx
                    n1.vy -= fy
                }
                if (n2 !== draggedNode) {
                    n2.vx += fx
                    n2.vy += fy
                }
            }
        }

        // 2. 星轨引力弹簧力
        for (edge in edges) {
            val n1 = edge.source
            val n2 = edge.target
            val dx = n2.x - n1.x
            val dy = n2.y - n1.y
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            val delta = dist - restLength
            val force = delta * kSpring * edge.strength

            val fx = (dx / dist) * force
            val fy = (dy / dist) * force

            if (n1 !== draggedNode) {
                n1.vx += fx
                n1.vy += fy
            }
            if (n2 !== draggedNode) {
                n2.vx -= fx
                n2.vy -= fy
            }
        }

        // 3. 向心万有引力与边界软碰撞
        for (node in nodes) {
            if (node === draggedNode) continue

            val dcx = cx - node.x
            val dcy = cy - node.y
            node.vx += dcx * kCenter
            node.vy += dcy * kCenter

            // 速度衰减与积分
            node.vx *= damping
            node.vy *= damping

            node.x += node.vx
            node.y += node.vy

            // 视口边界碰撞
            val pad = node.radius + 10f
            node.x = node.x.coerceIn(pad, w - pad)
            node.y = node.y.coerceIn(pad, h - pad)
        }
    }

    private fun updateStardust() {
        val h = if (height > 0) height.toFloat() else 2000f
        val w = if (width > 0) width.toFloat() else 1080f
        for (star in stardustList) {
            star.y -= star.speed
            if (star.y < 0) {
                star.y = h
                star.x = Random.nextFloat() * w
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. 绘制背景微光星尘
        for (star in stardustList) {
            starPaint.color = Color.argb(star.alpha, 200, 230, 255)
            canvas.drawCircle(star.x, star.y, star.radius, starPaint)
        }

        // 2. 绘制星轨引力光波连线 (Gravity Laser Beams)
        edgePaint.shader = null
        for (edge in edges) {
            val src = edge.source
            val dst = edge.target

            // 纯色光束（预混色）：跨媒介共鸣边更亮更粗，同域引力边保持低调
            edgePaint.color = edge.blendedColor
            edgePaint.alpha = if (edge.crossMedia) 150 else 55
            edgePaint.strokeWidth = if (edge.crossMedia) 2.4f else 1.2f
            canvas.drawLine(src.x, src.y, dst.x, dst.y, edgePaint)

            // 绘制星轨流动光珠 (Animated Energy Bead)
            val beadProgress = ((pulsePhase + (src.x + dst.y) * 0.005f) % (2f * Math.PI.toFloat())) / (2f * Math.PI.toFloat())
            val beadX = src.x + (dst.x - src.x) * beadProgress
            val beadY = src.y + (dst.y - src.y) * beadProgress
            canvas.drawCircle(beadX, beadY, if (edge.crossMedia) 3.2f else 2.2f, edgeBeadPaint)
        }

        // 3. 绘制引力天体节点 (Celestial Bodies)
        // 着色器以节点原点缓存，绘制时仅平移/缩放画布，避免每帧重建渐变
        for (node in nodes) {
            val r = node.radius
            canvas.save()
            canvas.translate(node.x, node.y)

            // 呼吸光晕 Halo：通过缩放画布实现呼吸效果
            val haloScale = 1.25f + sin(pulsePhase) * 0.1f
            canvas.save()
            canvas.scale(haloScale, haloScale)
            haloPaint.shader = node.haloShader
            haloPaint.alpha = if (node === draggedNode) 180 else 100
            canvas.drawCircle(0f, 0f, r, haloPaint)
            canvas.restore()

            // 天体球体本体
            nodePaint.shader = node.bodyShader
            canvas.drawCircle(0f, 0f, r, nodePaint)

            // 天体中央 Emoji
            canvas.drawText(node.book.mediaType.emoji, 0f, emojiPaint.textSize * 0.35f, emojiPaint)

            // 天体名称 (Serif / Monospace)
            canvas.drawText(node.displayTitle, 0f, r + 14f * resources.displayMetrics.density, textPaint)

            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tx = event.x
        val ty = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = tx
                touchDownY = ty
                touchLastX = tx
                touchLastY = ty
                draggedNode = nodes.find { node ->
                    hypot(node.x - tx, node.y - ty) <= node.radius * 1.5f
                }
                if (draggedNode != null) {
                    HapticFeedbackEngine.stampImpact(context)
                    SpatialAudioEngine.playCelestialTone(pan = (tx / width.toFloat()) * 2f - 1f)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                draggedNode?.let { node ->
                    node.x = tx
                    node.y = ty
                    touchLastX = tx
                    touchLastY = ty
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                draggedNode?.let { node ->
                    // 拖拽结束时把最后一次位移转为惯性，天体被"抛"出后自然滑行衰减
                    node.vx = (tx - touchLastX) * 0.8f
                    node.vy = (ty - touchLastY) * 0.8f
                    // 仅当位移未超过系统触摸阈值时视为点击，拖拽松手不再误跳详情页
                    val isTap = hypot(tx - touchDownX, ty - touchDownY) <= touchSlop
                    if (isTap) {
                        HapticFeedbackEngine.lightClick(context)
                        SpatialAudioEngine.playCelestialTone()
                        onNodeClickListener?.invoke(node.book)
                    }
                    draggedNode = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                draggedNode = null
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        physicsAnimator?.cancel()
    }
}
