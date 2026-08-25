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

    // 流光粒子
    private data class WarpStar(var x: Float, var y: Float, var z: Float, val color: Int, val speed: Float)
    private val stars = mutableListOf<WarpStar>()
    private val starColors = intArrayOf(
        Color.parseColor("#4DEEEA"), // 电光青
        Color.parseColor("#74EE15"), // 极光绿
        Color.parseColor("#FFE700"), // 晨曦金
        Color.parseColor("#F000FF"), // 霓虹紫
        Color.parseColor("#FF2A85"), // 幻境粉
    )

    // 动画驱动
    private var animator: ValueAnimator? = null
    private val gestureDetector: GestureDetector

    init {
        // 初始化流光粒子
        for (i in 0 until 90) {
            val angle = Math.random().toFloat() * Math.PI.toFloat() * 2f
            val dist = 100f + Math.random().toFloat() * 600f
            stars.add(
                WarpStar(
                    x = cos(angle) * dist,
                    y = sin(angle) * dist,
                    z = Math.random().toFloat() * farPlane,
                    color = starColors[i % starColors.size],
                    speed = 2f + Math.random().toFloat() * 4f,
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

        // 1. 绘制深邃虚空背景渐变
        paint.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#06080D"), Color.parseColor("#0B0F19"), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 2. 绘制多边形时空隧道环 (Concentric Warp Rings)
        drawWarpRings(canvas, cx, cy)

        // 3. 绘制光速拉丝流光粒子 (Speed Streaks)
        drawSpeedStreaks(canvas, cx, cy)

        // 4. 绘制 3D 流光记忆胶囊 (按 Z 轴由远及近渲染，以实现正确遮挡)
        drawMemoryCapsules(canvas, cx, cy)

        // 5. 绘制中心微光虫洞灭点
        drawSingularityCore(canvas, cx, cy)
    }

    private fun drawWarpRings(canvas: Canvas, cx: Float, cy: Float) {
        val ringCount = 8
        val ringSpacing = 380f

        for (i in 0 until ringCount) {
            val baseZ = (i * ringSpacing - (cameraZ % ringSpacing) + ringSpacing) % (ringCount * ringSpacing)
            if (baseZ < nearPlane || baseZ > farPlane) continue

            val scale = focalLength / (focalLength + baseZ)
            val ringW = width * 1.1f * scale
            val ringH = height * 0.85f * scale
            val alpha = ((1f - (baseZ / farPlane)) * 140).toInt().coerceIn(10, 140)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxOf(1f, 2.5f * scale)
            paint.color = Color.argb(alpha, 77, 238, 234)
            paint.pathEffect = DashPathEffect(floatArrayOf(18f * scale, 12f * scale), 0f)

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

                val tailZ = relZ + (60f * speedMultiplier)
                val tailScale = focalLength / (focalLength + tailZ)
                val tx = cx + star.x * tailScale
                val ty = cy + star.y * tailScale

                val alpha = ((1f - (relZ / farPlane)) * 255).toInt().coerceIn(0, 255)
                paint.strokeWidth = maxOf(1f, 3.5f * scale * speedMultiplier.coerceAtMost(3f))
                paint.color = Color.argb(alpha, Color.red(star.color), Color.green(star.color), Color.blue(star.color))

                canvas.drawLine(tx, ty, sx, sy, paint)
            }
        }
    }

    private fun drawMemoryCapsules(canvas: Canvas, cx: Float, cy: Float) {
        // 过滤并按 Z 深度从远到近排序
        val renderList = filteredCapsules.mapNotNull { item ->
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

            // 1. 胶囊流光底板
            cardRect.set(cardLeft, cardTop, cardRight, cardBottom)
            val bgAlpha = (alphaFactor * (if (isSelected) 240 else 190)).toInt()
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(bgAlpha, 16, 22, 34)
            canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, paint)

            // 2. 霓虹发光边框
            val strokeColor = when (item.book.mediaType) {
                MediaType.BOOK -> Color.parseColor("#4DEEEA")
                MediaType.ANIME -> Color.parseColor("#FF2A85")
                MediaType.MOVIE -> Color.parseColor("#FFE700")
                MediaType.GAME -> Color.parseColor("#74EE15")
                MediaType.PODCAST -> Color.parseColor("#F000FF")
            }
            val strokeAlpha = (alphaFactor * (if (isSelected) 255 else 160)).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isSelected) 3.5f * scale else 1.8f * scale
            paint.color = Color.argb(strokeAlpha, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor))
            canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, paint)

            // 3. 内部排版 (仅在中近景清晰渲染文字)
            if (scale > 0.35f) {
                // A. 顶部 Emoji + 分类 Badge
                val textPadX = cardLeft + 14f * scale
                var textCursorY = cardTop + 24f * scale

                textPaint.textSize = 14f * scale
                textPaint.isFakeBoldText = true
                textPaint.color = strokeColor
                canvas.drawText("${item.book.mediaType.emoji} ${item.book.mediaType.displayName}", textPadX, textCursorY, textPaint)

                // 评分/心智标识
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.color = Color.parseColor("#FFE700")
                canvas.drawText("★ ${item.book.rating ?: 5.0}", cardRight - 14f * scale, textCursorY, textPaint)
                textPaint.textAlign = Paint.Align.LEFT

                // B. 作品标题
                textCursorY += 28f * scale
                textPaint.textSize = 17f * scale
                textPaint.color = Color.WHITE
                val title = if (item.book.title.length > 10) item.book.title.take(9) + ".." else item.book.title
                canvas.drawText("《$title》", textPadX, textCursorY, textPaint)

                // C. 金句/短评
                textCursorY += 22f * scale
                textPaint.textSize = 11f * scale
                textPaint.isFakeBoldText = false
                textPaint.color = Color.parseColor("#8F9CAE")
                val comment = item.book.shortComment?.take(18) ?: item.book.author ?: "心智星系收录"
                canvas.drawText("“$comment”", textPadX, textCursorY, textPaint)

                // D. 底部纪元时间标识
                textCursorY += 24f * scale
                textPaint.textSize = 10f * scale
                textPaint.color = Color.parseColor("#4DEEEA")
                canvas.drawText("纪元印痕 · NO.${item.book.id}", textPadX, textCursorY, textPaint)
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
