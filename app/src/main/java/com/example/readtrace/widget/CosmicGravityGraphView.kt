package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.HapticFeedbackEngine
import com.example.readtrace.util.SpatialAudioEngine
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🪐 跨媒介认知引力星系物理视图 (Cosmic Gravity Knowledge Graph View)
 * 对标 Cosmos.so / Siteinspire 顶尖引力拓扑与 Awwwards 宇宙可视化：
 * - 将书籍、番剧、电影、游戏、声音构建为具有质量与引力场的引力天体（Cosmic Celestial Bodies）；
 * - 实时运行库仑斥力 + 虎克弹簧引力 + 阻尼减速物理力导向系统；
 * - 手指拖拽天体产生丝绸弹簧物理形变，松手触发 528Hz 空灵引力波共鸣震荡。
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
    )

    data class CosmicEdge(
        val source: CosmicNode,
        val target: CosmicNode,
        val strength: Float = 1.0f,
        val relationship: String = "共鸣引力",
    )

    data class StarDust(
        var x: Float,
        var y: Float,
        var speed: Float,
        var radius: Float,
        var alpha: Int,
    )

    private val nodes = mutableListOf<CosmicNode>()
    private val edges = mutableListOf<CosmicEdge>()
    private val stardustList = mutableListOf<StarDust>()

    private var draggedNode: CosmicNode? = null
    private var isSimulating = false
    private var physicsAnimator: ValueAnimator? = null
    private var pulsePhase = 0f

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
        setupPhysicsLoop()
    }

    private fun initStardust() {
        stardustList.clear()
        val random = Random(123)
        for (i in 0 until 60) {
            stardustList.add(
                StarDust(
                    x = random.nextFloat() * 1000f,
                    y = random.nextFloat() * 2000f,
                    speed = random.nextFloat() * 0.4f + 0.1f,
                    radius = random.nextFloat() * 2.0f + 0.8f,
                    alpha = random.nextInt(120) + 40,
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

        // 1. 构建天体节点
        books.take(16).forEachIndexed { i, book ->
            val angle = (i.toFloat() / min(16, books.size)) * 2f * Math.PI.toFloat()
            val dist = 120f * density + random.nextFloat() * 100f * density
            val nx = cx + cos(angle) * dist
            val ny = cy + sin(angle) * dist

            val color = when (book.mediaType) {
                MediaType.PODCAST -> Color.parseColor("#4DEEEA") // 极光青
                MediaType.BOOK -> Color.parseColor("#FFE700")    // 琥珀金
                MediaType.ANIME -> Color.parseColor("#FF6F91")   // 樱花粉
                MediaType.MOVIE -> Color.parseColor("#845EC2")   // 电影紫
                MediaType.GAME -> Color.parseColor("#00C9A7")    // 电玩绿
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

        // 2. 构建跨媒介引力星轨连线
        for (i in 0 until nodes.size) {
            for (j in i + 1 until nodes.size) {
                val n1 = nodes[i]
                val n2 = nodes[j]
                // 相同分类、或评分极高、或夜鹿/番剧关联
                val isRelated = n1.book.mediaType == n2.book.mediaType ||
                        (n1.book.category != null && n1.book.category == n2.book.category) ||
                        (i == 0 && j in 1..4)

                if (isRelated) {
                    edges.add(CosmicEdge(n1, n2, strength = 0.85f))
                }
            }
        }

        invalidate()
    }

    private fun setupPhysicsLoop() {
        physicsAnimator?.cancel()
        physicsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulsePhase = (pulsePhase + 0.04f) % (2f * Math.PI.toFloat())
                updatePhysicsStep()
                updateStardust()
                invalidate()
            }
            start()
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
        for (edge in edges) {
            val src = edge.source
            val dst = edge.target

            // 渐变光束
            edgePaint.shader = LinearGradient(
                src.x, src.y, dst.x, dst.y,
                src.color, dst.color,
                Shader.TileMode.CLAMP,
            )
            edgePaint.alpha = 90
            canvas.drawLine(src.x, src.y, dst.x, dst.y, edgePaint)

            // 绘制星轨流动光珠 (Animated Energy Bead)
            val beadProgress = ((pulsePhase + (src.x + dst.y) * 0.005f) % (2f * Math.PI.toFloat())) / (2f * Math.PI.toFloat())
            val beadX = src.x + (dst.x - src.x) * beadProgress
            val beadY = src.y + (dst.y - src.y) * beadProgress
            canvas.drawCircle(beadX, beadY, 2.8f, edgeBeadPaint)
        }

        // 3. 绘制引力天体节点 (Celestial Bodies)
        for (node in nodes) {
            val r = node.radius

            // 呼吸光晕 Halo
            val haloRadius = r * (1.25f + sin(pulsePhase) * 0.1f)
            haloPaint.shader = RadialGradient(
                node.x, node.y, haloRadius,
                intArrayOf(node.color, Color.TRANSPARENT),
                floatArrayOf(0.4f, 1.0f),
                Shader.TileMode.CLAMP,
            )
            haloPaint.alpha = if (node === draggedNode) 180 else 100
            canvas.drawCircle(node.x, node.y, haloRadius, haloPaint)

            // 天体球体本体
            nodePaint.shader = RadialGradient(
                node.x - r * 0.3f, node.y - r * 0.3f, r,
                intArrayOf(Color.WHITE, node.color, Color.parseColor("#0A0F1A")),
                floatArrayOf(0f, 0.65f, 1.0f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(node.x, node.y, r, nodePaint)

            // 天体中央 Emoji
            val emojiY = node.y + (emojiPaint.textSize * 0.35f)
            canvas.drawText(node.book.mediaType.emoji, node.x, emojiY, emojiPaint)

            // 天体名称 (Serif / Monospace)
            val titleY = node.y + r + 14f * resources.displayMetrics.density
            val displayTitle = if (node.book.title.length > 6) node.book.title.substring(0, 5) + "…" else node.book.title
            canvas.drawText(displayTitle, node.x, titleY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tx = event.x
        val ty = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
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
                    node.vx = 0f
                    node.vy = 0f
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggedNode?.let { node ->
                    HapticFeedbackEngine.lightClick(context)
                    SpatialAudioEngine.playCelestialTone()
                    onNodeClickListener?.invoke(node.book)
                    draggedNode = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        physicsAnimator?.cancel()
    }
}
