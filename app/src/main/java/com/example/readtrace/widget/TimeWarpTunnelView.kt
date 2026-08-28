package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.MediaType
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🌌 3D 时空穿梭隧道与心智流光胶囊渲染视图 (TimeWarpTunnelView)
 *
 * 核心渲染技术：
 * 1. 真实 3D 摄像机与视锥体透视投影（x' = x/z, y' = y/z）；
 * 2. 径向多边形时空环（Concentric Warp Rings）与光速流光粒子束；
 * 3. 悬浮流光记忆胶囊（Memory Flow Capsules），封存作品、时间戳、短评与心智能量；
 * 4. 手势上下拖拽惯性穿梭、1x~10x 曲速巡航与陀螺仪全息视差。
 */
class TimeWarpTunnelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class CapsuleItem(
        val book: Book,
        val mindprint: BookMindprint?,
        var zPos: Float, // 空间深度位置
        val angleRad: Float, // 围绕隧道中心排布的角度
        val ringRadius: Float, // 环绕半径
    )

    private val capsules = mutableListOf<CapsuleItem>()
    private var filteredCapsules = mutableListOf<CapsuleItem>()

    // 摄像机与透视参数
    private var cameraZ = 0f
    private val focalLength = 650f
    private val nearPlane = 20f
    private val farPlane = 3600f
    private val capsuleSpacing = 280f

    // 陀螺仪视差偏移量
    var gyroOffsetX = 0f
    var gyroOffsetY = 0f

    // 曲速巡航控制
    var speedMultiplier = 1.0f
    var isAutoCruising = true

    // 选中的胶囊
    var selectedCapsule: CapsuleItem? = null
    var onCapsuleClickListener: ((Book, BookMindprint?) -> Unit)? = null
    var onEraChangeListener: ((String) -> Unit)? = null

    // 画笔系统
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardRect = RectF()
    private val hitRects = mutableListOf<Pair<RectF, CapsuleItem>>()

    // 流光粒子（轻量化 36 颗，清新温润莫兰迪微芒）
    private data class WarpStar(var x: Float, var y: Float, var z: Float, val color: Int, val speed: Float)
    private val stars = mutableListOf<WarpStar>()
    private val starColors = intArrayOf(
        Color.parseColor("#E6D7B8"), // 晨曦米金
        Color.parseColor("#A8C4B8"), // 若草淡青
        Color.parseColor("#B4C5D8"), // 霁蓝微风
        Color.parseColor("#D6B8C4"), // 薄粉豆沙
        Color.parseColor("#EDE8DF"), // 象牙暖白
    )

    // 动画驱动
    private var animator: ValueAnimator? = null
    private val gestureDetector: GestureDetector

    init {
        // 初始化流光微尘粒子（轻量降级，减少循环开销）
        for (i in 0 until 36) {
            val angle = Math.random().toFloat() * Math.PI.toFloat() * 2f
            val dist = 100f + Math.random().toFloat() * 600f
            stars.add(
                WarpStar(
                    x = cos(angle) * dist,
                    y = sin(angle) * dist,
                    z = Math.random().toFloat() * farPlane,
                    color = starColors[i % starColors.size],
                    speed = 1.5f + Math.random().toFloat() * 2.5f,
                ),
            )
        }

        // 手势检测
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                cameraZ += distanceY * 1.8f
                clampCamera()
                invalidate()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val flingAnimator = ValueAnimator.ofFloat(cameraZ, cameraZ - velocityY * 0.45f).apply {
                    duration = 800
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        cameraZ = it.animatedValue as Float
                        clampCamera()
                        invalidate()
                    }
                }
                flingAnimator.start()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val hit = hitRects.lastOrNull { it.first.contains(e.x, e.y) }
                if (hit != null) {
                    selectedCapsule = hit.second
                    onCapsuleClickListener?.invoke(hit.second.book, hit.second.mindprint)
                    invalidate()
                    return true
                }
                return false
            }
        })
    }

    fun setData(books: List<Book>, mindprintMap: Map<Long, BookMindprint>) {
        capsules.clear()
        val sorted = books.sortedByDescending { it.id }

        sorted.forEachIndexed { index, book ->
            val angle = (index * 1.37f) % (Math.PI.toFloat() * 2f) // 黄金分割角排布
            val radius = 180f + (index % 3) * 45f
            val z = index * capsuleSpacing + 120f
            val mp = mindprintMap[book.id]
            capsules.add(CapsuleItem(book, mp, z, angle, radius))
        }

        applyFilter(null)
    }

    fun applyFilter(mediaType: MediaType?) {
        filteredCapsules.clear()
        if (mediaType == null) {
            filteredCapsules.addAll(capsules)
        } else {
            filteredCapsules.addAll(capsules.filter { it.book.mediaType == mediaType })
        }
        clampCamera()
        invalidate()
    }

    private fun clampCamera() {
        val maxZ = (filteredCapsules.size * capsuleSpacing) + 200f
        cameraZ = cameraZ.coerceIn(0f, maxOf(0f, maxZ))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (isAutoCruising) {
                    cameraZ += 1.5f * speedMultiplier
                    val maxZ = (filteredCapsules.size * capsuleSpacing) + 200f
                    if (cameraZ > maxZ) {
                        cameraZ = 0f
                    }
                }

                // 推进流光粒子
                stars.forEach { star ->
                    star.z -= (8f * speedMultiplier) * star.speed
                    if (star.z < nearPlane) {
                        star.z = farPlane
                    }
                }

                invalidate()
            }
        }
        animator?.start()
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w * 0.5f + gyroOffsetX * 35f
        val cy = h * 0.44f + gyroOffsetY * 35f

        hitRects.clear()

        // 1. 绘制清雅深黛水墨背景（去除死黑与刺眼感）
        paint.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#11161B"), Color.parseColor("#181E24"), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 2. 绘制清雅时空微光环 (Concentric Warp Rings)
        drawWarpRings(canvas, cx, cy)

        // 3. 绘制微光拉丝流光粒子 (Speed Streaks)
        drawSpeedStreaks(canvas, cx, cy)

        // 4. 绘制 3D 流光记忆胶囊 (按 Z 轴由远及近渲染，以实现正确遮挡)
        drawMemoryCapsules(canvas, cx, cy)

        // 5. 绘制中心微光核心灭点
        drawSingularityCore(canvas, cx, cy)
    }

    private fun drawWarpRings(canvas: Canvas, cx: Float, cy: Float) {
        val ringCount = 6
        val ringSpacing = 480f

        for (i in 0 until ringCount) {
            val baseZ = (i * ringSpacing - (cameraZ % ringSpacing) + ringSpacing) % (ringCount * ringSpacing)
            if (baseZ < nearPlane || baseZ > farPlane) continue

            val scale = focalLength / (focalLength + baseZ)
            val ringW = width * 1.1f * scale
            val ringH = height * 0.85f * scale
            val alpha = ((1f - (baseZ / farPlane)) * 60).toInt().coerceIn(6, 60)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxOf(1f, 1.8f * scale)
            paint.color = Color.argb(alpha, 220, 226, 230)
            paint.pathEffect = DashPathEffect(floatArrayOf(16f * scale, 16f * scale), 0f)

            canvas.drawRoundRect(cx - ringW * 0.5f, cy - ringH * 0.5f, cx + ringW * 0.5f, cy + ringH * 0.5f, 32f * scale, 32f * scale, paint)
            paint.pathEffect = null
        }
    }

    private fun drawSpeedStreaks(canvas: Canvas, cx: Float, cy: Float) {
        paint.style = Paint.Style.STROKE

        stars.forEach { star ->
            val relZ = star.z
            if (relZ in nearPlane..farPlane) {
                val scale = focalLength / (focalLength + relZ)
                val sx = cx + star.x * scale
                val sy = cy + star.y * scale

                val tailZ = relZ + (40f * speedMultiplier.coerceAtMost(2.5f))
                val tailScale = focalLength / (focalLength + tailZ)
                val tx = cx + star.x * tailScale
                val ty = cy + star.y * tailScale

                val alpha = ((1f - (relZ / farPlane)) * 180).toInt().coerceIn(0, 180)
                paint.strokeWidth = maxOf(1f, 2.2f * scale * speedMultiplier.coerceAtMost(2f))
                paint.color = Color.argb(alpha, Color.red(star.color), Color.green(star.color), Color.blue(star.color))

                canvas.drawLine(tx, ty, sx, sy, paint)
            }
        }
    }

    private fun drawMemoryCapsules(canvas: Canvas, cx: Float, cy: Float) {
        // 过滤并按 Z 深度从远到近排序 (线程安全防御)
        val snapshot = synchronized(filteredCapsules) {
            filteredCapsules.toList()
        }
        val renderList = snapshot.mapNotNull { item ->
            val relZ = item.zPos - cameraZ
            if (relZ in nearPlane..farPlane) {
                Pair(item, relZ)
            } else null
        }.sortedByDescending { it.second }

        renderList.forEach { (item, relZ) ->
            val scale = focalLength / (focalLength + relZ)
            val itemX = cx + (cos(item.angleRad) * item.ringRadius) * scale
            val itemY = cy + (sin(item.angleRad) * item.ringRadius * 0.85f) * scale

            val cardW = 280f * scale
            val cardH = 130f * scale
            val cardLeft = itemX - cardW * 0.5f
            val cardTop = itemY - cardH * 0.5f
            val cardRight = cardLeft + cardW
            val cardBottom = cardTop + cardH

            val alphaFactor = (1f - (relZ / farPlane)).coerceIn(0f, 1f)
            val isSelected = selectedCapsule?.book?.id == item.book.id

            // 保存可点击区域 (前排胶囊)
            if (relZ < 1400f) {
                val bounds = RectF(cardLeft, cardTop, cardRight, cardBottom)
                hitRects.add(Pair(bounds, item))
            }

            // 1. 优雅磨砂薄暮卡片底板
            cardRect.set(cardLeft, cardTop, cardRight, cardBottom)
            val bgAlpha = (alphaFactor * (if (isSelected) 245 else 200)).toInt()
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(bgAlpha, 22, 28, 36)
            canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, paint)

            // 2. 温润莫兰迪媒介色边框（去除生硬高饱和霓虹）
            val strokeColor = when (item.book.mediaType) {
                MediaType.BOOK -> Color.parseColor("#7FA88B") // 苍山竹青
                MediaType.ANIME -> Color.parseColor("#D48590") // 浅绯落樱
                MediaType.MOVIE -> Color.parseColor("#8B9EBF") // 晴岚霁蓝
                MediaType.GAME -> Color.parseColor("#C8A265") // 琥珀沉香
                MediaType.MUSIC -> Color.parseColor("#9F8DB0") // 幽兰微紫
            }
            val strokeAlpha = (alphaFactor * (if (isSelected) 230 else 140)).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isSelected) 2.6f * scale else 1.2f * scale
            paint.color = Color.argb(strokeAlpha, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor))
            canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, paint)

            // 3. 内部排版 (仅在中近景清晰渲染文字)
            if (scale > 0.35f) {
                // A. 顶部 Emoji + 分类 Badge
                val textPadX = cardLeft + 14f * scale
                var textCursorY = cardTop + 24f * scale

                textPaint.textSize = 13.5f * scale
                textPaint.isFakeBoldText = true
                textPaint.color = strokeColor
                canvas.drawText("${item.book.mediaType.emoji} ${item.book.mediaType.displayName}", textPadX, textCursorY, textPaint)

                // 评分/心智标识
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = Color.parseColor("#E6D7B8")
                canvas.drawText("★ ${item.book.rating ?: 5.0}", cardRight - 14f * scale, textCursorY, textPaint)
                textPaint.textAlign = Paint.Align.LEFT

                // B. 作品标题
                textCursorY += 28f * scale
                textPaint.textSize = 16.5f * scale
                textPaint.color = Color.parseColor("#FFFFFF")
                val title = if (item.book.title.length > 10) item.book.title.take(9) + ".." else item.book.title
                canvas.drawText("《$title》", textPadX, textCursorY, textPaint)

                // C. 金句/短评
                textCursorY += 22f * scale
                textPaint.textSize = 11f * scale
                textPaint.isFakeBoldText = false
                textPaint.color = Color.parseColor("#A8A29A")
                val comment = item.book.shortComment?.take(18) ?: item.book.author ?: "心智印痕收录"
                canvas.drawText("“$comment”", textPadX, textCursorY, textPaint)

                // D. 底部纪元时间标识
                textCursorY += 24f * scale
                textPaint.textSize = 10f * scale
                textPaint.color = Color.parseColor("#98A2A8")
                canvas.drawText("记忆刻印 · NO.${item.book.id}", textPadX, textCursorY, textPaint)
            }
        }
    }

    private fun drawSingularityCore(canvas: Canvas, cx: Float, cy: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(180, 77, 238, 234)
        canvas.drawCircle(cx, cy, 5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(90, 240, 0, 255)
        canvas.drawCircle(cx, cy, 14f, paint)
    }
}
