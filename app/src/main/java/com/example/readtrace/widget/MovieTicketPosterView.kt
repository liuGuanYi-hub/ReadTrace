package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🎟️ 复古电影票根海报视图 (MovieTicketPosterView)
 *
 * P5 阶段二升级：
 * 1. 撕票物理裂变动效（Ticket Tear & Fission Dynamic Motion）：0.0f -> 1.0f 连续撕裂插值；
 * 2. 锯齿撕裂纸质边缘（Jagged Paper Fibers Tear Path）：程序化不规则纸张纤维撕痕；
 * 3. 副票 3D 偏移与倾角脱落（Physics Offset & Tilt）：向右下偏移 dx=+45dp, dy=+22dp, rotate=-5.8°；
 * 4. 裂变流光能量缝隙（Fission Glow & Sparks）：中缝产生全息激光流光与自发光跳动脉冲微粒。
 */
class MovieTicketPosterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private class FissionSpark(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        val radius: Float,
        var alpha: Float = 1.0f,
    ) {
        fun update(dt: Float) {
            x += vx * dt
            y += vy * dt
            vx *= 0.92f
            vy *= 0.92f
            alpha = (alpha - 1.5f * dt).coerceAtLeast(0f)
        }
    }

    enum class TicketTheme(
        val displayName: String,
        val bgColors: IntArray,
        val ticketBgColor: Int,
        val textColor: Int,
        val subTextColor: Int,
        val accentColor: Int,
        val stubBgColor: Int,
        val perforationColor: Int,
        val radarColor: Int,
        val borderGlowColor: Int,
    ) {
        NOIR_CINEMA(
            "🎞️ 经典胶片",
            intArrayOf(Color.parseColor("#121113"), Color.parseColor("#1E1B18"), Color.parseColor("#0D0D0E")),
            Color.parseColor("#211F24"),
            Color.parseColor("#F5EFE6"),
            Color.parseColor("#A89F91"),
            Color.parseColor("#D4AF37"), // 胶片金
            Color.parseColor("#1A181D"),
            Color.parseColor("#55D4AF37"),
            Color.parseColor("#F4A261"),
            Color.parseColor("#40D4AF37"),
        ),
        VINTAGE_KRAFT(
            "📜 复古羊皮",
            intArrayOf(Color.parseColor("#F5EBE1"), Color.parseColor("#EBDCC9"), Color.parseColor("#E0CDB8")),
            Color.parseColor("#FAF3EB"),
            Color.parseColor("#2C221E"),
            Color.parseColor("#7A685D"),
            Color.parseColor("#A64B2A"), // 陶土红
            Color.parseColor("#F0E4D3"),
            Color.parseColor("#55A64B2A"),
            Color.parseColor("#C84B31"),
            Color.parseColor("#33A64B2A"),
        ),
        SUNSET_SCREEN(
            "🌅 落日放映",
            intArrayOf(Color.parseColor("#1F1118"), Color.parseColor("#38192A"), Color.parseColor("#1A1423")),
            Color.parseColor("#2E1929"),
            Color.parseColor("#FFF0F5"),
            Color.parseColor("#D8B4C8"),
            Color.parseColor("#FF758F"), // 珊瑚粉
            Color.parseColor("#241320"),
            Color.parseColor("#55FF758F"),
            Color.parseColor("#FFB703"),
            Color.parseColor("#40FF758F"),
        ),
    }

    private var currentTheme: TicketTheme = TicketTheme.NOIR_CINEMA
    private var movie: Book? = null
    private var mindprint: BookMindprint? = null

    private var movieCoverBitmap: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fissionPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 撕票裂变状态
    var isTorn: Boolean = false
        private set
    var tearProgress: Float = 0f
        private set
    private var tearAnimator: ValueAnimator? = null
    private val fissionSparks = mutableListOf<FissionSpark>()

    var onTicketTearListener: ((isTorn: Boolean, seamScreenX: Float, seamScreenY: Float) -> Unit)? = null

    fun setData(
        movie: Book,
        mindprint: BookMindprint?,
        theme: TicketTheme = TicketTheme.NOIR_CINEMA,
    ) {
        this.movie = movie
        this.mindprint = mindprint
        this.currentTheme = theme

        loadCoverBitmap(movie.coverUrl.orEmpty())
        invalidate()
    }

    fun setTheme(theme: TicketTheme) {
        this.currentTheme = theme
        invalidate()
    }

    fun getTheme(): TicketTheme = currentTheme

    fun toggleTear(animate: Boolean = true) {
        isTorn = !isTorn
        val target = if (isTorn) 1.0f else 0.0f

        val (sx, sy) = getSeamScreenCoordinates()

        if (isTorn) {
            // 产生裂变火花
            spawnFissionSparks()
        }

        onTicketTearListener?.invoke(isTorn, sx, sy)

        if (!animate) {
            tearProgress = target
            invalidate()
            return
        }

        tearAnimator?.cancel()
        tearAnimator = ValueAnimator.ofFloat(tearProgress, target).apply {
            duration = if (isTorn) 520L else 380L
            interpolator = if (isTorn) OvershootInterpolator(1.15f) else DecelerateInterpolator(1.2f)
            addUpdateListener { va ->
                tearProgress = va.animatedValue as Float
                fissionSparks.forEach { it.update(0.016f) }
                invalidate()
            }
        }
        tearAnimator?.start()
    }

    private fun spawnFissionSparks() {
        fissionSparks.clear()
        val pad = width * 0.04f
        val ticketLeft = pad
        val ticketRight = width - pad
        val splitX = ticketLeft + (ticketRight - ticketLeft) * 0.72f
        val top = height * 0.08f
        val bottom = height - height * 0.08f

        val rand = Random(System.currentTimeMillis())
        for (i in 0 until 24) {
            val py = top + rand.nextFloat() * (bottom - top)
            val angle = rand.nextDouble(-Math.PI * 0.5, Math.PI * 0.5) // 向右喷射
            val speed = rand.nextFloat() * 260f + 120f
            val vx = (cos(angle) * speed).toFloat()
            val vy = (sin(angle) * speed).toFloat()
            val sz = rand.nextFloat() * 4f + 2f
            val col = if (i % 2 == 0) currentTheme.accentColor else Color.WHITE
            fissionSparks.add(FissionSpark(splitX, py, vx, vy, col, sz))
        }
    }

    fun getSeamScreenCoordinates(): Pair<Float, Float> {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val pad = width * 0.04f
        val splitX = pad + (width - pad * 2) * 0.72f
        val cy = height * 0.5f
        return Pair(location[0] + splitX, location[1] + cy)
    }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
    }

    private fun loadCoverBitmap(url: String) {
        if (url.isBlank()) {
            movieCoverBitmap = null
            invalidate()
            return
        }

        com.example.readtrace.util.CoverImageHelper.loadCoverBitmap(context, url, 720, 1080) { bmp ->
            movieCoverBitmap = bmp
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawTicket(canvas, width.toFloat(), height.toFloat(), isExport = false)
    }

    private fun drawTicket(canvas: Canvas, w: Float, h: Float, isExport: Boolean = false) {
        // 1. 绘制背景渐变
        val bgShader = LinearGradient(0f, 0f, w, h, currentTheme.bgColors, null, Shader.TileMode.CLAMP)
        paint.shader = bgShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val movieItem = movie ?: return

        // 2. 计算票根区域 (按 2:1 或居中适配比例)
        val pad = w * 0.04f
        val ticketLeft = pad
        val ticketTop = h * 0.08f
        val ticketRight = w - pad
        val ticketBottom = h - h * 0.08f
        val ticketW = ticketRight - ticketLeft
        val ticketH = ticketBottom - ticketTop

        val splitRatio = 0.72f // 72% 主票，28% 副票
        val splitX = ticketLeft + ticketW * splitRatio
        val cornerRadius = ticketH * 0.04f
        val notchRadius = ticketH * 0.05f

        val effectiveTear = if (isExport) (if (isTorn) 1.0f else 0.0f) else tearProgress

        if (effectiveTear <= 0.001f) {
            // 未撕票状态：整体绘制
            drawIntactTicket(canvas, movieItem, ticketLeft, ticketTop, ticketRight, ticketBottom, splitX, cornerRadius, notchRadius, ticketW, ticketH)
        } else {
            // 裂变撕票状态：左右两联分离渲染
            drawTornTicketFission(canvas, movieItem, ticketLeft, ticketTop, ticketRight, ticketBottom, splitX, cornerRadius, notchRadius, ticketW, ticketH, effectiveTear, isExport)
        }
    }

    private fun drawIntactTicket(
        canvas: Canvas,
        movieItem: Book,
        ticketLeft: Float,
        ticketTop: Float,
        ticketRight: Float,
        ticketBottom: Float,
        splitX: Float,
        cornerRadius: Float,
        notchRadius: Float,
        ticketW: Float,
        ticketH: Float,
    ) {
        // 构建完整双联裁切路径
        val ticketPath = Path().apply {
            moveTo(ticketLeft + cornerRadius, ticketTop)
            lineTo(splitX - notchRadius, ticketTop)
            arcTo(splitX - notchRadius, ticketTop - notchRadius, splitX + notchRadius, ticketTop + notchRadius, 180f, -180f, false)
            lineTo(ticketRight - cornerRadius, ticketTop)
            arcTo(ticketRight - cornerRadius * 2, ticketTop, ticketRight, ticketTop + cornerRadius * 2, 270f, 90f, false)
            lineTo(ticketRight, ticketBottom - cornerRadius)
            arcTo(ticketRight - cornerRadius * 2, ticketBottom - cornerRadius * 2, ticketRight, ticketBottom, 0f, 90f, false)
            lineTo(splitX + notchRadius, ticketBottom)
            arcTo(splitX - notchRadius, ticketBottom - notchRadius, splitX + notchRadius, ticketBottom + notchRadius, 0f, -180f, false)
            lineTo(ticketLeft + cornerRadius, ticketBottom)
            arcTo(ticketLeft, ticketBottom - cornerRadius * 2, ticketLeft + cornerRadius * 2, ticketBottom, 90f, 90f, false)
            lineTo(ticketLeft, ticketTop + cornerRadius)
            arcTo(ticketLeft, ticketTop, ticketLeft + cornerRadius * 2, ticketTop + cornerRadius * 2, 180f, 90f, false)
            close()
        }

        // 辉光边框
        paint.color = currentTheme.borderGlowColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawPath(ticketPath, paint)

        // 填充主票背景
        paint.color = currentTheme.ticketBgColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(ticketPath, paint)

        // 填充副票背景
        val stubPath = Path().apply {
            moveTo(splitX, ticketTop)
            arcTo(splitX - notchRadius, ticketTop - notchRadius, splitX + notchRadius, ticketTop + notchRadius, 180f, -180f, false)
            lineTo(ticketRight - cornerRadius, ticketTop)
            arcTo(ticketRight - cornerRadius * 2, ticketTop, ticketRight, ticketTop + cornerRadius * 2, 270f, 90f, false)
            lineTo(ticketRight, ticketBottom - cornerRadius)
            arcTo(ticketRight - cornerRadius * 2, ticketBottom - cornerRadius * 2, ticketRight, ticketBottom, 0f, 90f, false)
            lineTo(splitX + notchRadius, ticketBottom)
            arcTo(splitX - notchRadius, ticketBottom - notchRadius, splitX + notchRadius, ticketBottom + notchRadius, 0f, -180f, false)
            lineTo(splitX, ticketBottom)
            close()
        }
        paint.color = currentTheme.stubBgColor
        canvas.drawPath(stubPath, paint)

        // 绘制中缝虚线
        paint.color = currentTheme.perforationColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        canvas.drawLine(splitX, ticketTop + notchRadius, splitX, ticketBottom - notchRadius, paint)
        paint.pathEffect = null

        drawMainTicketContent(canvas, movieItem, ticketLeft, ticketTop, splitX - ticketLeft, ticketH)
        drawStubTicketContent(canvas, movieItem, splitX, ticketTop, ticketRight - splitX, ticketH)
    }

    private fun drawTornTicketFission(
        canvas: Canvas,
        movieItem: Book,
        ticketLeft: Float,
        ticketTop: Float,
        ticketRight: Float,
        ticketBottom: Float,
        splitX: Float,
        cornerRadius: Float,
        notchRadius: Float,
        ticketW: Float,
        ticketH: Float,
        tear: Float,
        isExport: Boolean,
    ) {
        val toothAmp = ticketW * 0.008f
        val segments = 22

        // A. 绘制左侧主票 (带中缝撕裂毛边)
        val mainPath = Path().apply {
            moveTo(ticketLeft + cornerRadius, ticketTop)
            lineTo(splitX - notchRadius, ticketTop)
            arcTo(splitX - notchRadius, ticketTop - notchRadius, splitX + notchRadius, ticketTop + notchRadius, 180f, -90f, false)

            val startY = ticketTop + notchRadius
            val endY = ticketBottom - notchRadius
            val stepY = (endY - startY) / segments
            for (i in 1..segments) {
                val y = startY + i * stepY
                val sign = if (i % 2 == 1) -1f else 1f
                val x = if (i == segments) splitX else splitX + sign * toothAmp
                lineTo(x, y)
            }

            arcTo(splitX - notchRadius, ticketBottom - notchRadius, splitX + notchRadius, ticketBottom + notchRadius, 90f, -90f, false)
            lineTo(ticketLeft + cornerRadius, ticketBottom)
            arcTo(ticketLeft, ticketBottom - cornerRadius * 2, ticketLeft + cornerRadius * 2, ticketBottom, 90f, 90f, false)
            lineTo(ticketLeft, ticketTop + cornerRadius)
            arcTo(ticketLeft, ticketTop, ticketLeft + cornerRadius * 2, ticketTop + cornerRadius * 2, 180f, 90f, false)
            close()
        }

        // 主票阴影与边框
        paint.color = currentTheme.borderGlowColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawPath(mainPath, paint)

        paint.color = currentTheme.ticketBgColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(mainPath, paint)

        // 绘制主票内容
        drawMainTicketContent(canvas, movieItem, ticketLeft, ticketTop, splitX - ticketLeft, ticketH)

        // B. 绘制右侧副票 (带物理脱离位移与角度偏转)
        val stubDx = tear * ticketW * 0.085f
        val stubDy = tear * ticketH * 0.045f
        val stubRot = -tear * 5.8f

        canvas.save()
        val stubPivotX = splitX + (ticketRight - splitX) * 0.5f
        val stubPivotY = ticketTop + ticketH * 0.5f
        canvas.translate(stubDx, stubDy)
        canvas.rotate(stubRot, stubPivotX, stubPivotY)

        val stubPath = Path().apply {
            moveTo(splitX, ticketTop + notchRadius)
            arcTo(splitX - notchRadius, ticketTop - notchRadius, splitX + notchRadius, ticketTop + notchRadius, 90f, -90f, false)
            lineTo(ticketRight - cornerRadius, ticketTop)
            arcTo(ticketRight - cornerRadius * 2, ticketTop, ticketRight, ticketTop + cornerRadius * 2, 270f, 90f, false)
            lineTo(ticketRight, ticketBottom - cornerRadius)
            arcTo(ticketRight - cornerRadius * 2, ticketBottom - cornerRadius * 2, ticketRight, ticketBottom, 0f, 90f, false)
            lineTo(splitX + notchRadius, ticketBottom)
            arcTo(splitX - notchRadius, ticketBottom - notchRadius, splitX + notchRadius, ticketBottom + notchRadius, 0f, -90f, false)

            val startY = ticketBottom - notchRadius
            val endY = ticketTop + notchRadius
            val stepY = (endY - startY) / segments
            for (i in 1..segments) {
                val y = startY + i * stepY
                val sign = if (i % 2 == 1) 1f else -1f
                val x = if (i == segments) splitX else splitX + sign * toothAmp
                lineTo(x, y)
            }
            close()
        }

        // 副票脱落发光边框
        paint.color = currentTheme.borderGlowColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawPath(stubPath, paint)

        paint.color = currentTheme.stubBgColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(stubPath, paint)

        drawStubTicketContent(canvas, movieItem, splitX, ticketTop, ticketRight - splitX, ticketH)
        canvas.restore()

        // C. 绘制中缝裂变能量流光与自发光粒子
        if (!isExport && tear in 0.01f..0.999f) {
            val beamAlpha = (sin(tear * Math.PI.toFloat()) * 230).toInt()
            fissionPaint.color = currentTheme.accentColor
            fissionPaint.alpha = beamAlpha
            fissionPaint.strokeWidth = 3.5f
            fissionPaint.style = Paint.Style.STROKE
            canvas.drawLine(splitX + stubDx * 0.2f, ticketTop + notchRadius, splitX + stubDx * 0.4f, ticketBottom - notchRadius, fissionPaint)

            // 绘制裂变火花
            fissionSparks.forEach { sp ->
                fissionPaint.style = Paint.Style.FILL
                fissionPaint.color = sp.color
                fissionPaint.alpha = (sp.alpha * 255).toInt()
                canvas.drawCircle(sp.x, sp.y, sp.radius * sp.alpha, fissionPaint)
            }
        }
    }

    private fun drawMainTicketContent(canvas: Canvas, movie: Book, left: Float, top: Float, w: Float, h: Float) {
        val innerPad = w * 0.05f

        // A. 顶部影院放映头
        val headerY = top + h * 0.08f
        textPaint.color = currentTheme.accentColor
        textPaint.textSize = h * 0.026f
        textPaint.isFakeBoldText = true
        canvas.drawText("READTRACE CINEMA", left + innerPad, headerY, textPaint)

        textPaint.color = currentTheme.subTextColor
        textPaint.textSize = h * 0.024f
        textPaint.isFakeBoldText = false
        val seatInfo = "DOLBY ATMOS · 4K"
        canvas.drawText(seatInfo, left + w - innerPad - textPaint.measureText(seatInfo), headerY, textPaint)

        // 顶部分隔线
        paint.color = currentTheme.perforationColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawLine(left + innerPad, headerY + h * 0.02f, left + w - innerPad, headerY + h * 0.02f, paint)

        // B. 电影海报 (左侧，撑满主票高度，为票面最大元素)
        val posterTop = headerY + h * 0.05f
        val posterW = w * 0.40f
        val posterRect = RectF(left + innerPad, posterTop, left + innerPad + posterW, top + h - innerPad)

        if (movieCoverBitmap != null && !movieCoverBitmap!!.isRecycled) {
            canvas.save()
            val clipPath = Path().apply {
                addRoundRect(posterRect, 14f, 14f, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)

            val bmp = movieCoverBitmap!!
            val bmpW = bmp.width.toFloat()
            val bmpH = bmp.height.toFloat()
            val targetRatio = posterRect.width() / posterRect.height()
            val bmpRatio = bmpW / bmpH

            val srcRect = if (bmpRatio > targetRatio) {
                val cropW = bmpH * targetRatio
                val left = (bmpW - cropW) * 0.5f
                Rect(left.toInt(), 0, (left + cropW).toInt(), bmpH.toInt())
            } else {
                val cropH = bmpW / targetRatio
                val cropTop = (bmpH - cropH) * 0.5f
                Rect(0, cropTop.toInt(), bmpW.toInt(), (cropTop + cropH).toInt())
            }

            canvas.drawBitmap(bmp, srcRect, posterRect, bitmapPaint)
            canvas.restore()
        } else {
            paint.color = currentTheme.stubBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(posterRect, 14f, 14f, paint)

            textPaint.color = currentTheme.subTextColor
            textPaint.textSize = h * 0.028f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎬", posterRect.centerX(), posterRect.centerY(), textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }

        // 海报微边框
        paint.color = currentTheme.accentColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(posterRect, 14f, 14f, paint)

        // C. 电影标题与导演信息 (海报右侧竖排)
        val infoLeft = posterRect.right + innerPad * 0.8f
        val infoMaxW = left + w - innerPad - infoLeft

        var textY = posterTop + h * 0.035f
        textPaint.color = currentTheme.textColor
        textPaint.textSize = h * 0.042f
        textPaint.isFakeBoldText = true
        val title = if (movie.title.length > 8) movie.title.substring(0, 7) + "..." else movie.title
        canvas.drawText(title, infoLeft, textY, textPaint)

        // 导演 / 制作社
        textY += h * 0.045f
        textPaint.color = currentTheme.accentColor
        textPaint.textSize = h * 0.025f
        textPaint.isFakeBoldText = false
        val author = "导演: " + (movie.author.orEmpty().ifBlank { "经典名导" })
        canvas.drawText(author, infoLeft, textY, textPaint)

        // 分类标签与评分星级
        textY += h * 0.042f
        textPaint.color = currentTheme.subTextColor
        textPaint.textSize = h * 0.022f
        val ratingStr = "★★★★★  ${movie.rating ?: 5.0} 分"
        val ratingY = textY
        canvas.drawText(ratingStr, infoLeft, textY, textPaint)

        // D. 经典台词金句 (右列评分下方，延伸至票底)
        val quoteRect = RectF(infoLeft, ratingY + h * 0.05f, left + w - innerPad, top + h - innerPad)
        val quoteH = quoteRect.height()

        paint.color = Color.argb(30, Color.red(currentTheme.accentColor), Color.green(currentTheme.accentColor), Color.blue(currentTheme.accentColor))
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(quoteRect, 12f, 12f, paint)

        paint.color = currentTheme.accentColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(quoteRect, 12f, 12f, paint)

        // 金句文本
        val quote = movie.shortComment?.ifBlank { "“有些鸟儿是关不住的，它们的每一片羽毛都闪耀着自由的光辉。”" }
            ?: "“爱是唯一可以超越时间与空间维度的力量。”"
        textPaint.color = currentTheme.textColor
        textPaint.textSize = h * 0.024f
        textPaint.isFakeBoldText = false

        // 简易两行折行
        val line1 = if (quote.length > 11) quote.substring(0, 11) else quote
        val line2 = if (quote.length > 11) quote.substring(11).take(12) + if (quote.length > 23) "..." else "" else ""

        canvas.drawText(line1, quoteRect.left + innerPad * 0.6f, quoteRect.top + quoteH * 0.35f, textPaint)
        if (line2.isNotBlank()) {
            canvas.drawText(line2, quoteRect.left + innerPad * 0.6f, quoteRect.top + quoteH * 0.62f, textPaint)
        }
    }

    private fun drawStubTicketContent(canvas: Canvas, movie: Book, left: Float, top: Float, w: Float, h: Float) {
        val innerPad = w * 0.08f

        // A. 顶部 ADMIT ONE 纪念印章
        val stampCenterY = top + h * 0.16f
        val stampCenterX = left + w * 0.5f

        paint.color = currentTheme.accentColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        val stampRect = RectF(stampCenterX - w * 0.38f, stampCenterY - h * 0.045f, stampCenterX + w * 0.38f, stampCenterY + h * 0.045f)
        canvas.drawRoundRect(stampRect, 8f, 8f, paint)

        textPaint.color = currentTheme.accentColor
        textPaint.textSize = h * 0.026f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("ADMIT ONE", stampCenterX, stampCenterY + h * 0.009f, textPaint)

        // B. 观影日期与编码
        val dateY = stampRect.bottom + h * 0.06f
        textPaint.color = currentTheme.subTextColor
        textPaint.textSize = h * 0.020f
        textPaint.isFakeBoldText = false
        val curDate = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        canvas.drawText("DATE: $curDate", stampCenterX, dateY, textPaint)

        val codeY = dateY + h * 0.035f
        val movieCode = "NO." + (1000 + (movie.id % 9000)) + "-RT"
        canvas.drawText("CODE: $movieCode", stampCenterX, codeY, textPaint)

        // C. 电影标题缩写
        val titleY = codeY + h * 0.08f
        textPaint.color = currentTheme.textColor
        textPaint.textSize = h * 0.028f
        textPaint.isFakeBoldText = true
        val shortTitle = if (movie.title.length > 8) movie.title.substring(0, 7) + ".." else movie.title
        canvas.drawText("《$shortTitle》", stampCenterX, titleY, textPaint)

        // D. 拟真防伪条形码 (Barcode)
        val barcodeTop = top + h * 0.62f
        val barcodeH = h * 0.18f
        val barcodeW = w * 0.75f
        val barcodeLeft = stampCenterX - barcodeW * 0.5f

        drawBarcode(canvas, barcodeLeft, barcodeTop, barcodeW, barcodeH)

        // 底部防伪字
        textPaint.color = currentTheme.subTextColor
        textPaint.textSize = h * 0.016f
        textPaint.isFakeBoldText = false
        canvas.drawText("READTRACE CINEMA VERIFIED", stampCenterX, barcodeTop + barcodeH + h * 0.04f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawBarcode(canvas: Canvas, left: Float, top: Float, w: Float, h: Float) {
        paint.color = currentTheme.textColor
        paint.style = Paint.Style.FILL

        val barCount = 28
        val gap = w / barCount
        val hash = movie?.title?.hashCode() ?: 12345

        for (i in 0 until barCount) {
            val isThick = ((hash shr (i % 16)) and 1) == 1
            val bw = if (isThick) gap * 0.65f else gap * 0.3f
            val bx = left + i * gap
            canvas.drawRect(bx, top, bx + bw, top + h, paint)
        }
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            toggleTear(animate = true)
            return true
        }
        return super.onTouchEvent(event)
    }

    fun create1080pPosterBitmap(exportTorn: Boolean = isTorn): Bitmap {
        val targetW = 1080
        val targetH = 540 // 2:1 经典电影双联票根比例
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val prevProgress = tearProgress
        tearProgress = if (exportTorn) 1.0f else 0.0f
        drawTicket(canvas, targetW.toFloat(), targetH.toFloat(), isExport = true)
        tearProgress = prevProgress
        return bmp
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tearAnimator?.cancel()
        fissionSparks.clear()
    }
}
