package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🎟️ 竖向复古电影票根海报视图 (MovieTicketPosterView)
 *
 * 版式（v4.2.13 重排）：
 * 1. 锁定 1 : 2.2 竖向票根比例，不再随父容器拉伸，屏幕渲染与导出图完全一致；
 * 2. 上半部为主票：影院放映头 → 2:3 剧照 → 居中大标题 → 导演/类型/评分 → 底部金句；
 * 3. 下半部为副券：横向虚线撕开，左列 ADMIT ONE 印章与片名，右列防伪条形码；
 * 4. 撕票时副券向右下脱落并轻微偏转，中缝为程序化锯齿纸质裂痕。
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

    /** 票根几何：left/top/right/bottom 为外框，splitY 为主票与副券的横向撕线 */
    private class TicketBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val splitY: Float,
        val cornerRadius: Float,
        val notchRadius: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val stubHeight: Float get() = bottom - splitY
    }

    private var currentTheme: TicketTheme = TicketTheme.NOIR_CINEMA
    private var movie: Book? = null
    private var mindprint: BookMindprint? = null

    private var movieCoverBitmap: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
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
        requestLayout()
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
        val box = computeTicketBox(width.toFloat(), height.toFloat())
        val rand = Random(System.currentTimeMillis())
        for (i in 0 until 28) {
            val px = box.left + box.notchRadius + rand.nextFloat() * (box.width - box.notchRadius * 2)
            // 沿横向缝隙向上下两侧喷射
            val angle = if (rand.nextBoolean()) {
                rand.nextDouble(-Math.PI * 0.85, -Math.PI * 0.15)
            } else {
                rand.nextDouble(Math.PI * 0.15, Math.PI * 0.85)
            }
            val speed = rand.nextFloat() * 240f + 110f
            val vx = (cos(angle) * speed).toFloat()
            val vy = (sin(angle) * speed).toFloat()
            val sz = rand.nextFloat() * 4f + 2f
            val col = if (i % 2 == 0) currentTheme.accentColor else Color.WHITE
            fissionSparks.add(FissionSpark(px, box.splitY, vx, vy, col, sz))
        }
    }

    fun getSeamScreenCoordinates(): Pair<Float, Float> {
        val location = IntArray(2)
        getLocationOnScreen(location)
        val box = computeTicketBox(width.toFloat(), height.toFloat())
        return Pair(location[0] + box.left + box.width * 0.5f, location[1] + box.splitY)
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val availH = MeasureSpec.getSize(heightMeasureSpec)
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val desired = desiredHeightFor(w.toFloat())
        val h = when (mode) {
            MeasureSpec.UNSPECIFIED -> desired
            else -> minOf(availH.toFloat(), desired)
        }
        setMeasuredDimension(w, h.toInt())
    }

    private fun desiredHeightFor(w: Float): Float {
        val pad = w * PADDING_RATIO
        return (w - pad * 2) * TICKET_ASPECT + pad * 2
    }

    private fun computeTicketBox(w: Float, h: Float): TicketBox {
        val pad = w * PADDING_RATIO
        val availW = w - pad * 2
        val availH = h - pad * 2

        var tw = availW
        var th = tw * TICKET_ASPECT
        if (th > availH) {
            th = availH
            tw = th / TICKET_ASPECT
        }

        val left = (w - tw) * 0.5f
        val top = (h - th) * 0.5f
        return TicketBox(
            left = left,
            top = top,
            right = left + tw,
            bottom = top + th,
            splitY = top + th * MAIN_RATIO,
            cornerRadius = tw * 0.045f,
            notchRadius = tw * 0.055f,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawTicket(canvas, width.toFloat(), height.toFloat(), isExport = false)
    }

    private fun drawTicket(canvas: Canvas, w: Float, h: Float, isExport: Boolean = false) {
        // 1. 影院暗房背景
        val bgShader = LinearGradient(0f, 0f, w, h, currentTheme.bgColors, null, Shader.TileMode.CLAMP)
        paint.shader = bgShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val movieItem = movie ?: return
        val box = computeTicketBox(w, h)
        val effectiveTear = if (isExport) (if (isTorn) 1.0f else 0.0f) else tearProgress

        if (effectiveTear <= 0.001f) {
            drawIntactTicket(canvas, movieItem, box)
        } else {
            drawTornTicketFission(canvas, movieItem, box, effectiveTear, isExport)
        }
    }

    // ------------------------------------------------------------------ 路径构建

    private fun buildIntactOutline(box: TicketBox): Path {
        val cr = box.cornerRadius
        val nr = box.notchRadius
        return Path().apply {
            moveTo(box.left + cr, box.top)
            lineTo(box.right - cr, box.top)
            arcTo(box.right - cr * 2, box.top, box.right, box.top + cr * 2, 270f, 90f, false)
            lineTo(box.right, box.splitY - nr)
            arcTo(box.right - nr, box.splitY - nr, box.right + nr, box.splitY + nr, 270f, -180f, false)
            lineTo(box.right, box.bottom - cr)
            arcTo(box.right - cr * 2, box.bottom - cr * 2, box.right, box.bottom, 0f, 90f, false)
            lineTo(box.left + cr, box.bottom)
            arcTo(box.left, box.bottom - cr * 2, box.left + cr * 2, box.bottom, 90f, 90f, false)
            lineTo(box.left, box.splitY + nr)
            arcTo(box.left - nr, box.splitY - nr, box.left + nr, box.splitY + nr, 90f, -180f, false)
            lineTo(box.left, box.top + cr)
            arcTo(box.left, box.top, box.left + cr * 2, box.top + cr * 2, 180f, 90f, false)
            close()
        }
    }

    private fun buildTornMainPath(box: TicketBox): Path {
        val cr = box.cornerRadius
        val nr = box.notchRadius
        return Path().apply {
            moveTo(box.left + cr, box.top)
            lineTo(box.right - cr, box.top)
            arcTo(box.right - cr * 2, box.top, box.right, box.top + cr * 2, 270f, 90f, false)
            lineTo(box.right, box.splitY - nr)
            arcTo(box.right - nr, box.splitY - nr, box.right + nr, box.splitY + nr, 270f, -90f, false)
            appendTearZigzag(box, rightToLeft = true)
            arcTo(box.left - nr, box.splitY - nr, box.left + nr, box.splitY + nr, 0f, -90f, false)
            lineTo(box.left, box.top + cr)
            arcTo(box.left, box.top, box.left + cr * 2, box.top + cr * 2, 180f, 90f, false)
            close()
        }
    }

    private fun buildTornStubPath(box: TicketBox): Path {
        val cr = box.cornerRadius
        val nr = box.notchRadius
        return Path().apply {
            moveTo(box.left, box.splitY + nr)
            arcTo(box.left - nr, box.splitY - nr, box.left + nr, box.splitY + nr, 90f, -90f, false)
            appendTearZigzag(box, rightToLeft = false)
            arcTo(box.right - nr, box.splitY - nr, box.right + nr, box.splitY + nr, 180f, -90f, false)
            lineTo(box.right, box.bottom - cr)
            arcTo(box.right - cr * 2, box.bottom - cr * 2, box.right, box.bottom, 0f, 90f, false)
            lineTo(box.left + cr, box.bottom)
            arcTo(box.left, box.bottom - cr * 2, box.left + cr * 2, box.bottom, 90f, 90f, false)
            close()
        }
    }

    /** 沿撕线生成程序化锯齿；两半使用同一组 y 值，保证撕开后边缘严丝合缝 */
    private fun Path.appendTearZigzag(box: TicketBox, rightToLeft: Boolean) {
        val nr = box.notchRadius
        val amp = box.height * 0.006f
        val segments = 24
        val startX = if (rightToLeft) box.right - nr else box.left + nr
        val endX = if (rightToLeft) box.left + nr else box.right - nr
        val stepX = (endX - startX) / segments
        for (i in 1..segments) {
            val x = startX + i * stepX
            val y = if (i == segments) box.splitY else box.splitY + (if (i % 2 == 1) amp else -amp)
            lineTo(x, y)
        }
    }

    // ------------------------------------------------------------------ 完整票根

    private fun drawIntactTicket(canvas: Canvas, movieItem: Book, box: TicketBox) {
        val outline = buildIntactOutline(box)

        paint.color = currentTheme.borderGlowColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawPath(outline, paint)

        paint.color = currentTheme.ticketBgColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(outline, paint)

        canvas.save()
        canvas.clipPath(outline)
        val stubRect = RectF(box.left - 1f, box.splitY, box.right + 1f, box.bottom + 1f)
        paint.color = currentTheme.stubBgColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(stubRect, paint)
        canvas.restore()

        // 横向撕线虚线
        paint.color = currentTheme.perforationColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        canvas.drawLine(box.left + box.notchRadius, box.splitY, box.right - box.notchRadius, box.splitY, paint)
        paint.pathEffect = null

        drawMainTicketContent(canvas, movieItem, box)
        drawStubTicketContent(canvas, movieItem, box)
    }

    // ------------------------------------------------------------------ 撕票裂变

    private fun drawTornTicketFission(
        canvas: Canvas,
        movieItem: Book,
        box: TicketBox,
        tear: Float,
        isExport: Boolean,
    ) {
        // A. 上半主票
        val mainPath = buildTornMainPath(box)
        paint.color = currentTheme.borderGlowColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawPath(mainPath, paint)

        paint.color = currentTheme.ticketBgColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(mainPath, paint)

        drawMainTicketContent(canvas, movieItem, box)

        // B. 下半副券（物理脱落：右下位移 + 轻微偏转）
        val stubDx = tear * box.width * 0.045f
        val stubDy = tear * box.height * 0.055f
        val stubRot = -tear * 2.6f

        canvas.save()
        val pivotX = box.left + box.width * 0.5f
        val pivotY = box.splitY + box.stubHeight * 0.5f
        canvas.translate(stubDx, stubDy)
        canvas.rotate(stubRot, pivotX, pivotY)

        val stubPath = buildTornStubPath(box)
        paint.color = currentTheme.borderGlowColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawPath(stubPath, paint)

        paint.color = currentTheme.stubBgColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(stubPath, paint)

        drawStubTicketContent(canvas, movieItem, box)
        canvas.restore()

        // C. 中缝裂变流光与自发光粒子
        if (!isExport && tear in 0.01f..0.999f) {
            val beamAlpha = (sin(tear * Math.PI.toFloat()) * 230).toInt()
            fissionPaint.color = currentTheme.accentColor
            fissionPaint.alpha = beamAlpha
            fissionPaint.strokeWidth = 3.5f
            fissionPaint.style = Paint.Style.STROKE
            val beamY = box.splitY + stubDy * 0.35f
            canvas.drawLine(box.left + box.notchRadius, beamY, box.right - box.notchRadius, beamY, fissionPaint)

            fissionSparks.forEach { sp ->
                fissionPaint.style = Paint.Style.FILL
                fissionPaint.color = sp.color
                fissionPaint.alpha = (sp.alpha * 255).toInt()
                canvas.drawCircle(sp.x, sp.y, sp.radius * sp.alpha, fissionPaint)
            }
        }
    }

    // ------------------------------------------------------------------ 主票内容

    private fun drawMainTicketContent(canvas: Canvas, movie: Book, box: TicketBox) {
        val w = box.width
        val mainH = box.splitY - box.top
        val pad = w * 0.07f
        val unit = w // 所有字号以票面宽度为基准，保证导出与屏幕一致

        // A. 影院放映头
        textPaint.textAlign = Paint.Align.LEFT
        val headerY = box.top + mainH * 0.055f
        textPaint.color = currentTheme.accentColor
        textPaint.textSize = unit * 0.038f
        textPaint.isFakeBoldText = true
        canvas.drawText("READTRACE CINEMA", box.left + pad, headerY, textPaint)

        textPaint.color = currentTheme.subTextColor
        textPaint.textSize = unit * 0.032f
        textPaint.isFakeBoldText = false
        val seat = "DOLBY ATMOS · 4K"
        canvas.drawText(seat, box.right - pad - textPaint.measureText(seat), headerY, textPaint)

        val lineY = headerY + mainH * 0.022f
        paint.color = currentTheme.perforationColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawLine(box.left + pad, lineY, box.right - pad, lineY, paint)

        // B. 文本区预留高度（标题 + 元信息 + 金句）
        val titleSize = unit * 0.072f
        val metaSize = unit * 0.034f
        val textBlockH = titleSize * 1.5f + metaSize * 2.2f + unit * 0.12f

        // C. 2:3 剧照（居中，占据主票视觉主体）
        val posterTop = lineY + mainH * 0.045f
        val posterMaxH = (box.splitY - pad * 0.8f - textBlockH - posterTop).coerceAtLeast(unit * 0.3f)
        var posterH = posterMaxH
        var posterW = posterH / 1.45f
        if (posterW > w - pad * 2) {
            posterW = w - pad * 2
            posterH = posterW * 1.45f
        }
        val posterLeft = box.left + (w - posterW) * 0.5f
        val posterRect = RectF(posterLeft, posterTop, posterLeft + posterW, posterTop + posterH)
        drawPoster(canvas, movie, posterRect)

        // D. 片名（居中大字，单行省略而非硬截断）
        val centerX = box.left + w * 0.5f
        val titleY = posterRect.bottom + titleSize * 1.2f
        textPaint.color = currentTheme.textColor
        textPaint.textSize = titleSize
        textPaint.isFakeBoldText = true
        textPaint.textAlign = Paint.Align.CENTER
        val titleText = TextUtils.ellipsize(movie.title, textPaint, w - pad * 2, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleText, centerX, titleY, textPaint)

        // E. 元信息：导演 · 类型 · 评分
        val metaY = titleY + metaSize * 1.9f
        textPaint.textSize = metaSize
        textPaint.isFakeBoldText = false
        textPaint.color = currentTheme.subTextColor
        val director = movie.author?.takeIf { it.isNotBlank() } ?: "经典名导"
        val category = movie.category?.takeIf { it.isNotBlank() }
        val rating = movie.rating?.div(2.0) ?: 4.0
        val metaText = buildString {
            append("导演 ").append(director)
            category?.let { append(" · ").append(it) }
            append(" · ★").append(String.format(Locale.US, "%.1f", rating))
        }
        val metaFit = TextUtils.ellipsize(metaText, textPaint, w - pad * 2, TextUtils.TruncateAt.END).toString()
        canvas.drawText(metaFit, centerX, metaY, textPaint)

        // F. 底部金句（底对齐，自动折行，最多三行）
        val quote = movie.shortComment?.takeIf { it.isNotBlank() }
            ?: "有些鸟儿是关不住的，它们的每一片羽毛都闪耀着自由的光辉。"
        val quoteText = "“$quote”"
        textPaint.color = currentTheme.textColor
        textPaint.textSize = unit * 0.032f
        // StaticLayout 自行按 setAlignment 计算行偏移，画笔必须回到 LEFT，
        // 否则继承到的 CENTER 会把每行再居中一次，整段向左偏半行宽
        val quotePaint = TextPaint(textPaint).apply { textAlign = Paint.Align.LEFT }
        val quoteLayout = StaticLayout.Builder
            .obtain(quoteText, 0, quoteText.length, quotePaint, (w - pad * 2).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val quoteBottom = box.splitY - pad * 0.7f
        val quoteTop = quoteBottom - quoteLayout.height
        if (quoteTop > metaY + metaSize * 0.8f) {
            canvas.save()
            canvas.translate(box.left + pad, quoteTop)
            quoteLayout.draw(canvas)
            canvas.restore()
        }

        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawPoster(canvas: Canvas, movie: Book, rect: RectF) {
        val rx = rect.width() * 0.035f
        val ry = rect.height() * 0.025f
        val bmp = movieCoverBitmap

        if (bmp != null && !bmp.isRecycled) {
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(rect, rx, ry, Path.Direction.CW) })

            val bmpW = bmp.width.toFloat()
            val bmpH = bmp.height.toFloat()
            val targetRatio = rect.width() / rect.height()
            val bmpRatio = bmpW / bmpH
            val srcRect = if (bmpRatio > targetRatio) {
                val cropW = bmpH * targetRatio
                val l = (bmpW - cropW) * 0.5f
                Rect(l.toInt(), 0, (l + cropW).toInt(), bmpH.toInt())
            } else {
                val cropH = bmpW / targetRatio
                // 竖向海报优先保留上部（片名与主体通常在上三分之一）
                val t = (bmpH - cropH) * 0.28f
                Rect(0, t.toInt(), bmpW.toInt(), (t + cropH).toInt())
            }
            canvas.drawBitmap(bmp, srcRect, rect, bitmapPaint)
            canvas.restore()
        } else {
            paint.color = currentTheme.stubBgColor
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, rx, ry, paint)

            textPaint.color = currentTheme.subTextColor
            textPaint.textSize = rect.width() * 0.16f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎬", rect.centerX(), rect.centerY() + rect.width() * 0.06f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }

        paint.color = currentTheme.accentColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(rect, rx, ry, paint)
    }

    // ------------------------------------------------------------------ 副券内容

    private fun drawStubTicketContent(canvas: Canvas, movie: Book, box: TicketBox) {
        val w = box.width
        val h = box.stubHeight
        val pad = w * 0.06f
        val top = box.splitY

        // 左列：ADMIT ONE 纪念印章 + 片名 + 日期 + 编码
        val stampW = w * 0.42f
        val stampH = h * 0.26f
        val stampRect = RectF(box.left + pad, top + h * 0.12f, box.left + pad + stampW, top + h * 0.12f + stampH)

        paint.color = currentTheme.accentColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawRoundRect(stampRect, 8f, 8f, paint)

        textPaint.color = currentTheme.accentColor
        textPaint.textSize = stampH * 0.40f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("ADMIT ONE", stampRect.centerX(), stampRect.centerY() + stampH * 0.14f, textPaint)

        textPaint.color = currentTheme.textColor
        textPaint.textSize = h * 0.115f
        val shortTitle = TextUtils.ellipsize(movie.title, textPaint, stampW, TextUtils.TruncateAt.END).toString()
        canvas.drawText("《$shortTitle》", stampRect.centerX(), top + h * 0.52f, textPaint)

        textPaint.color = currentTheme.subTextColor
        textPaint.textSize = h * 0.09f
        textPaint.isFakeBoldText = false
        val curDate = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        canvas.drawText(curDate, stampRect.centerX(), top + h * 0.70f, textPaint)
        val movieCode = "NO." + (1000 + (movie.id % 9000)) + "-RT"
        canvas.drawText(movieCode, stampRect.centerX(), top + h * 0.86f, textPaint)

        // 右列：防伪条形码
        val barcodeW = w * 0.38f
        val barcodeH = h * 0.34f
        val barcodeLeft = box.right - pad - barcodeW
        drawBarcode(canvas, barcodeLeft, top + h * 0.22f, barcodeW, barcodeH)

        textPaint.color = currentTheme.subTextColor
        textPaint.textSize = h * 0.075f
        canvas.drawText("READTRACE VERIFIED", barcodeLeft + barcodeW * 0.5f, top + h * 0.72f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawBarcode(canvas: Canvas, left: Float, top: Float, w: Float, h: Float) {
        paint.color = currentTheme.textColor
        paint.style = Paint.Style.FILL

        val barCount = 30
        val gap = w / barCount
        val hash = movie?.title?.hashCode() ?: 12345

        for (i in 0 until barCount) {
            val isThick = ((hash shr (i % 16)) and 1) == 1
            val bw = if (isThick) gap * 0.68f else gap * 0.32f
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
        val bmp = Bitmap.createBitmap(EXPORT_WIDTH, EXPORT_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val prevProgress = tearProgress
        tearProgress = if (exportTorn) 1.0f else 0.0f
        drawTicket(canvas, EXPORT_WIDTH.toFloat(), EXPORT_HEIGHT.toFloat(), isExport = true)
        tearProgress = prevProgress
        return bmp
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tearAnimator?.cancel()
        fissionSparks.clear()
    }

    companion object {
        /** 票根高宽比（竖向票根，接近真实入场券 1:2.2） */
        private const val TICKET_ASPECT = 2.2f
        /** 票根相对视图宽度两侧的留白比例 */
        private const val PADDING_RATIO = 0.05f
        /** 主票占票根总高度的比例，其余为副券 */
        private const val MAIN_RATIO = 0.78f

        private const val EXPORT_WIDTH = 1080
        private const val EXPORT_HEIGHT = 2376
    }
}
