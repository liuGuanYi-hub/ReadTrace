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
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import com.example.readtrace.model.MediaType
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 🗺️ 3D 情绪拓扑与等高线心智地形图渲染视图 (MindprintTopologyView)
 *
 * 核心特性：
 * 1. 多峰复合高斯势能地形算法（计算 28x28 网格精神海拔起伏）；
 * 2. 三大渲染模式：3D 发光等高线 (CONTOUR)、空间立体线框 (WIREFRAME)、热力引力场 (HEATMAP)；
 * 3. 巅峰作品水晶方尖碑信标 (Landmark Beacons) 与能量光柱；
 * 4. 单指拖拽旋转视角 (Yaw/Pitch)、双指缩放与海拔切片分析。
 */
class MindprintTopologyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class RenderMode(val displayName: String) {
        CONTOUR("🌌 3D 等高线"),
        WIREFRAME("🌐 立体网格"),
        HEATMAP("🌋 引力热力场"),
    }

    data class LandmarkBeacon(
        val book: Book,
        val mindprint: BookMindprint,
        val gx: Float,
        val gy: Float,
        val elevation: Float,
        var screenX: Float = 0f,
        var screenY: Float = 0f,
    )

    private val gridSize = 26
    private val heightGrid = Array(gridSize) { FloatArray(gridSize) }
    private val beacons = mutableListOf<LandmarkBeacon>()

    // 渲染模式
    var renderMode = RenderMode.CONTOUR
        set(value) {
            field = value
            invalidate()
        }

    // 视角控制 (3D 旋转与缩放)
    private var yawDeg = 45f
    private var pitchDeg = 55f
    private var zoomScale = 1.0f

    // 每帧预计算的旋转三角函数缓存，避免在网格循环中反复执行 toRadians/cos
    private var cosYaw = 1f
    private var sinYaw = 0f
    private var cosPitch = 1f
    private var sinPitch = 0f

    // 投影结果复用缓冲：零分配渲染（单点复用 tmpPt，多点并行场景复用 quadBuf 槽位）
    private val tmpPt = FloatArray(2)
    private val quadBuf = FloatArray(8)

    // 陀螺仪视差
    var gyroOffsetX = 0f
    var gyroOffsetY = 0f

    // 海拔切片阈值 (0.0f ~ 1.0f)
    var sliceThreshold = 0.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
            invalidate()
        }

    // 交互回调
    var onBeaconClickListener: ((Book, BookMindprint) -> Unit)? = null
    private var selectedBeacon: LandmarkBeacon? = null

    // 画笔系统
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPath = Path()

    // 手势检测
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private val scaleDetector: ScaleGestureDetector

    // 呼吸律动
    private var breathPhase = 0f
    private var animator: ValueAnimator? = null

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomScale = (zoomScale * detector.scaleFactor).coerceIn(0.6f, 2.4f)
                invalidate()
                return true
            }
        })
    }

    fun setData(books: List<Book>, mindprints: Map<Long, BookMindprint>) {
        beacons.clear()

        // 挑选代表性高光作品作为心智地标
        val validPairs = books.mapNotNull { b ->
            mindprints[b.id]?.let { mp -> Pair(b, mp) }
        }

        validPairs.take(16).forEachIndexed { index, (book, mp) ->
            // 将 5 维雷达图映射至 2D 网格坐标 (gx, gy)
            // X 轴：情感共鸣度 (0 ~ gridSize)
            // Y 轴：思想哲学深度 (0 ~ gridSize)
            val emotionalScore = ((mp.emotionScore * 0.6 + mp.healingScore * 0.4) / 10.0).toFloat()
            val philosophicalScore = ((mp.depthScore * 0.6 + mp.logicScore * 0.4) / 10.0).toFloat()

            val gx = (2.5f + emotionalScore * (gridSize - 5f)).coerceIn(2f, gridSize - 3f)
            val gy = (2.5f + philosophicalScore * (gridSize - 5f)).coerceIn(2f, gridSize - 3f)
            val peakH = 0.45f + (mp.averageScore().toFloat() / 10f) * 0.55f

            beacons.add(LandmarkBeacon(book, mp, gx, gy, peakH))
        }

        rebuildTerrainHeight()
        invalidate()
    }

    private fun rebuildTerrainHeight() {
        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                var h = 0.08f // 基底海平面

                // 叠加各个地标的高斯峰值
                beacons.forEach { beacon ->
                    val dx = (i - beacon.gx).toDouble()
                    val dy = (j - beacon.gy).toDouble()
                    val distSq = dx * dx + dy * dy
                    val sigmaSq = 9.0 // 高斯影响半径
                    val gaussian = beacon.elevation * exp(-distSq / (2.0 * sigmaSq))
                    h += gaussian.toFloat()
                }

                heightGrid[i][j] = h.coerceIn(0f, 1.2f)
            }
        }
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
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                breathPhase = (breathPhase + 0.04f) % (Math.PI.toFloat() * 2f)
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
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true

                // 检测是否点击了信标
                val hit = beacons.firstOrNull { b ->
                    val dx = event.x - b.screenX
                    val dy = event.y - b.screenY
                    sqrt(dx * dx + dy * dy) < 48f
                }
                if (hit != null) {
                    selectedBeacon = hit
                    onBeaconClickListener?.invoke(hit.book, hit.mindprint)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    yawDeg = (yawDeg + dx * 0.45f) % 360f
                    pitchDeg = (pitchDeg - dy * 0.35f).coerceIn(20f, 85f)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w * 0.5f + gyroOffsetX * 25f
        val cy = h * 0.48f + gyroOffsetY * 25f

        // 1. 深邃暗黑星海背景
        paint.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#05070B"), Color.parseColor("#0C111C"), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 2. 根据模式绘制 3D 拓扑地貌
        val cellW = (w * 0.024f) * zoomScale
        val cellH = (w * 0.024f) * zoomScale
        val elevationScale = 140f * zoomScale

        cosYaw = Math.cos(Math.toRadians(yawDeg.toDouble())).toFloat()
        sinYaw = Math.sin(Math.toRadians(yawDeg.toDouble())).toFloat()
        cosPitch = Math.cos(Math.toRadians(pitchDeg.toDouble())).toFloat()
        sinPitch = Math.sin(Math.toRadians(pitchDeg.toDouble())).toFloat()

        when (renderMode) {
            RenderMode.CONTOUR -> drawContourLayers(canvas, cx, cy, cellW, cellH, elevationScale)
            RenderMode.WIREFRAME -> drawWireframeMesh(canvas, cx, cy, cellW, cellH, elevationScale)
            RenderMode.HEATMAP -> drawHeatmapGradient(canvas, cx, cy, cellW, cellH, elevationScale)
        }

        // 3. 绘制地标信标方尖碑 (Landmark Beacons)
        drawLandmarkBeacons(canvas, cx, cy, cellW, cellH, elevationScale)
    }

    private fun project3D(gx: Float, gy: Float, gz: Float, cx: Float, cy: Float, cellW: Float, cellH: Float, elevationScale: Float, out: FloatArray, offset: Int = 0) {
        val half = gridSize * 0.5f
        val x = (gx - half) * cellW
        val y = (gy - half) * cellH
        val z = gz * elevationScale

        // 绕 Y 轴偏航旋转 (Yaw)
        val xRot = x * cosYaw - y * sinYaw
        val yRot = x * sinYaw + y * cosYaw

        // 绕 X 轴俯仰投影 (Pitch)
        out[offset] = cx + xRot
        out[offset + 1] = cy + yRot * sinPitch - z * cosPitch
    }

    private fun drawWireframeMesh(canvas: Canvas, cx: Float, cy: Float, cellW: Float, cellH: Float, elevationScale: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f

        for (i in 0 until gridSize) {
            gridPath.reset()
            for (j in 0 until gridSize) {
                val gz = heightGrid[i][j]
                if (gz < sliceThreshold) continue
                project3D(i.toFloat(), j.toFloat(), gz, cx, cy, cellW, cellH, elevationScale, tmpPt)
                if (j == 0) gridPath.moveTo(tmpPt[0], tmpPt[1]) else gridPath.lineTo(tmpPt[0], tmpPt[1])
            }
            paint.color = getElevationColor(heightGrid[i][gridSize / 2])
            canvas.drawPath(gridPath, paint)
        }

        for (j in 0 until gridSize) {
            gridPath.reset()
            for (i in 0 until gridSize) {
                val gz = heightGrid[i][j]
                if (gz < sliceThreshold) continue
                project3D(i.toFloat(), j.toFloat(), gz, cx, cy, cellW, cellH, elevationScale, tmpPt)
                if (i == 0) gridPath.moveTo(tmpPt[0], tmpPt[1]) else gridPath.lineTo(tmpPt[0], tmpPt[1])
            }
            paint.color = getElevationColor(heightGrid[gridSize / 2][j])
            canvas.drawPath(gridPath, paint)
        }
    }

    private fun drawContourLayers(canvas: Canvas, cx: Float, cy: Float, cellW: Float, cellH: Float, elevationScale: Float) {
        val contourLevels = floatArrayOf(0.15f, 0.35f, 0.55f, 0.75f, 0.95f)

        // 1. 先绘制微弱底网
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.0f
        paint.color = Color.argb(40, 77, 238, 234)
        for (i in 0 until gridSize step 2) {
            gridPath.reset()
            for (j in 0 until gridSize step 2) {
                val gz = heightGrid[i][j]
                project3D(i.toFloat(), j.toFloat(), gz, cx, cy, cellW, cellH, elevationScale, tmpPt)
                if (j == 0) gridPath.moveTo(tmpPt[0], tmpPt[1]) else gridPath.lineTo(tmpPt[0], tmpPt[1])
            }
            canvas.drawPath(gridPath, paint)
        }

        // 2. 绘制各层等高线光环
        contourLevels.forEachIndexed { lvlIdx, levelH ->
            if (levelH >= sliceThreshold) {
                paint.strokeWidth = 2.4f
                paint.color = getElevationColor(levelH)

                for (i in 0 until gridSize - 1) {
                    for (j in 0 until gridSize - 1) {
                        val h00 = heightGrid[i][j]
                        val h10 = heightGrid[i + 1][j]
                        val h01 = heightGrid[i][j + 1]

                        if ((h00 >= levelH && h10 < levelH) || (h00 < levelH && h10 >= levelH)) {
                            project3D(i + 0.5f, j.toFloat(), levelH, cx, cy, cellW, cellH, elevationScale, quadBuf, 0)
                            project3D(i.toFloat(), j + 0.5f, levelH, cx, cy, cellW, cellH, elevationScale, quadBuf, 2)
                            canvas.drawLine(quadBuf[0], quadBuf[1], quadBuf[2], quadBuf[3], paint)
                        }
                    }
                }
            }
        }
    }

    private fun drawHeatmapGradient(canvas: Canvas, cx: Float, cy: Float, cellW: Float, cellH: Float, elevationScale: Float) {
        paint.style = Paint.Style.FILL

        for (i in 0 until gridSize - 1) {
            for (j in 0 until gridSize - 1) {
                val gz = (heightGrid[i][j] + heightGrid[i + 1][j] + heightGrid[i][j + 1] + heightGrid[i + 1][j + 1]) / 4f
                if (gz < sliceThreshold) continue

                project3D(i.toFloat(), j.toFloat(), heightGrid[i][j], cx, cy, cellW, cellH, elevationScale, quadBuf, 0)
                project3D(i + 1f, j.toFloat(), heightGrid[i + 1][j], cx, cy, cellW, cellH, elevationScale, quadBuf, 2)
                project3D(i + 1f, j + 1f, heightGrid[i + 1][j + 1], cx, cy, cellW, cellH, elevationScale, quadBuf, 4)
                project3D(i.toFloat(), j + 1f, heightGrid[i][j + 1], cx, cy, cellW, cellH, elevationScale, quadBuf, 6)

                gridPath.reset()
                gridPath.moveTo(quadBuf[0], quadBuf[1])
                gridPath.lineTo(quadBuf[2], quadBuf[3])
                gridPath.lineTo(quadBuf[4], quadBuf[5])
                gridPath.lineTo(quadBuf[6], quadBuf[7])
                gridPath.close()

                paint.color = getElevationColor(gz, alpha = 160)
                canvas.drawPath(gridPath, paint)
            }
        }
    }

    private fun drawLandmarkBeacons(canvas: Canvas, cx: Float, cy: Float, cellW: Float, cellH: Float, elevationScale: Float) {
        beacons.forEach { beacon ->
            val gz = heightGrid[beacon.gx.toInt()][beacon.gy.toInt()]
            if (gz >= sliceThreshold) {
                project3D(beacon.gx, beacon.gy, 0f, cx, cy, cellW, cellH, elevationScale, quadBuf, 0)
                project3D(beacon.gx, beacon.gy, gz, cx, cy, cellW, cellH, elevationScale, quadBuf, 2)
                beacon.screenX = quadBuf[2]
                beacon.screenY = quadBuf[3]

                val isSelected = selectedBeacon?.book?.id == beacon.book.id

                // A. 垂直发光引力光柱
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (isSelected) 2.5f else 1.2f
                paint.color = Color.argb(if (isSelected) 220 else 120, 77, 238, 234)
                paint.pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
                canvas.drawLine(quadBuf[0], quadBuf[1], quadBuf[2], quadBuf[3], paint)
                paint.pathEffect = null

                // B. 水晶方尖碑顶部菱形信标
                paint.style = Paint.Style.FILL
                val beaconColor = when (beacon.book.mediaType) {
                    MediaType.BOOK -> Color.parseColor("#4DEEEA")
                    MediaType.ANIME -> Color.parseColor("#FF2A85")
                    MediaType.MOVIE -> Color.parseColor("#FFE700")
                    MediaType.GAME -> Color.parseColor("#74EE15")
                    MediaType.MUSIC -> Color.parseColor("#F000FF")
                }
                paint.color = beaconColor

                val diamondSize = (if (isSelected) 10f else 6.5f) * zoomScale
                val diamondPath = Path().apply {
                    moveTo(quadBuf[2], quadBuf[3] - diamondSize)
                    lineTo(quadBuf[2] + diamondSize, quadBuf[3])
                    lineTo(quadBuf[2], quadBuf[3] + diamondSize)
                    lineTo(quadBuf[2] - diamondSize, quadBuf[3])
                    close()
                }
                canvas.drawPath(diamondPath, paint)

                // C. 漂浮标题标签 (高光时或放大时渲染)
                if (zoomScale > 0.85f || isSelected) {
                    textPaint.textSize = 11f * zoomScale
                    textPaint.isFakeBoldText = isSelected
                    textPaint.color = if (isSelected) Color.WHITE else Color.parseColor("#D5DFEE")
                    val title = if (beacon.book.title.length > 7) beacon.book.title.take(6) + ".." else beacon.book.title
                    canvas.drawText("${beacon.book.mediaType.emoji} $title", quadBuf[2] + diamondSize + 6f, quadBuf[3] + 4f, textPaint)
                }
            }
        }
    }

    fun focusBook(bookId: Long) {
        val target = beacons.firstOrNull { it.book.id == bookId } ?: return
        selectedBeacon = target
        zoomScale = 1.35f
        invalidate()
    }

    fun create1080pPosterBitmap(): android.graphics.Bitmap {
        val targetW = 1080
        val targetH = 1440
        val bmp = android.graphics.Bitmap.createBitmap(targetW, targetH, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. 背景
        paint.shader = LinearGradient(0f, 0f, 0f, targetH.toFloat(), Color.parseColor("#05070B"), Color.parseColor("#0C111C"), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, targetW.toFloat(), targetH.toFloat(), paint)
        paint.shader = null

        val cx = targetW * 0.5f
        val cy = targetH * 0.46f
        val cellW = targetW * 0.024f * 1.25f
        val cellH = targetW * 0.024f * 1.25f
        val elevationScale = 180f

        drawContourLayers(canvas, cx, cy, cellW, cellH, elevationScale)
        drawLandmarkBeacons(canvas, cx, cy, cellW, cellH, elevationScale)

        // 2. 顶部典藏标题
        textPaint.color = Color.WHITE
        textPaint.textSize = 40f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("🗺️ READTRACE 3D MINDPRINT TOPOLOGY", cx, 90f, textPaint)

        textPaint.color = Color.parseColor("#4DEEEA")
        textPaint.textSize = 22f
        textPaint.isFakeBoldText = false
        canvas.drawText("心智等高线地貌 · 精神大陆三维拓扑图谱", cx, 132f, textPaint)

        // 3. 底部图例
        textPaint.color = Color.parseColor("#8F9CAE")
        textPaint.textSize = 18f
        canvas.drawText("深海蓝 < 2000m · 翠绿平原 < 4500m · 金黄高地 < 6500m · 紫晶山脉 < 8000m · 雪白巅顶 ≥ 8000m", cx, targetH - 60f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        return bmp
    }

    private fun getElevationColor(elevation: Float, alpha: Int = 220): Int {
        return when {
            elevation < 0.25f -> Color.argb(alpha, 14, 116, 238) // 深海蓝
            elevation < 0.50f -> Color.argb(alpha, 16, 185, 129) // 翠绿平原
            elevation < 0.75f -> Color.argb(alpha, 245, 158, 11) // 金黄高地
            elevation < 0.95f -> Color.argb(alpha, 217, 70, 239) // 紫晶山脉
            else -> Color.argb(alpha, 255, 255, 255) // 雪白巅顶
        }
    }
}
