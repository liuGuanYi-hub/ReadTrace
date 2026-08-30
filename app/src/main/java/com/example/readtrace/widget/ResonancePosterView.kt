package com.example.readtrace.widget

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
import android.view.View
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🌌 跨媒介双生共鸣复古票根微卡视图 (ResonancePosterView)
 *
 * 版式设计：
 * 1. 采用类似复古电影票根的 1 : 2.25 竖向实体联票结构（Ticket Stub Architecture）；
 * 2. 具有标志性的打孔月牙凹槽（Perforation Notches）与横向微缝虚线；
 * 3. 纯竖向紧凑流动排版：
 *    - 票头：READTRACE RESONANCE MATRIX · 双生心智联票 + 灵魂契合度胶囊
 *    - 上半部：ALPHA 维度作品卡盒（2:3 纵向封面 + 大标题 + 标签 + 核心金句卡盒）
 *    - 中枢部：六维双生共鸣雷达枢纽与能量连接光桥 + 灵魂共鸣星核
 *    - 下半部：OMEGA 维度作品卡盒（2:3 纵向封面 + 大标题 + 标签 + 核心金句卡盒）
 *    - 底部副券（打孔撕票联）：ADMIT TWO 双生印章 + 作品联名 + 防伪条形码 + 朱砂契印
 */
class ResonancePosterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class PosterTheme(
        val displayName: String,
        val bgColors: IntArray,
        val ticketBgColor: Int,
        val cardBgColor: Int,
        val textColor: Int,
        val subTextColor: Int,
        val accentAColor: Int,
        val accentBColor: Int,
        val stubBgColor: Int,
        val perforationColor: Int,
        val radarGridColor: Int,
        val quoteBgColor: Int,
        val borderGlowColor: Int,
    ) {
        OBSIDIAN(
            "🌌 黑曜星云",
            intArrayOf(Color.parseColor("#0E0C0B"), Color.parseColor("#17131A"), Color.parseColor("#0C0E14")),
            Color.parseColor("#18141C"),
            Color.parseColor("#211B27"),
            Color.parseColor("#F5EFE6"),
            Color.parseColor("#9E988F"),
            Color.parseColor("#F4A261"), // 琥珀金
            Color.parseColor("#A855F7"), // 幻夜紫
            Color.parseColor("#131017"),
            Color.parseColor("#55F4A261"),
            Color.parseColor("#33FFFFFF"),
            Color.parseColor("#2B2233"),
            Color.parseColor("#4DF4A261"),
        ),
        RICE_PAPER(
            "📜 宣纸朱砂",
            intArrayOf(Color.parseColor("#FAF8F3"), Color.parseColor("#F3ECE0"), Color.parseColor("#EAE1D0")),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#F8F3EA"),
            Color.parseColor("#1F1C18"),
            Color.parseColor("#666666"),
            Color.parseColor("#C84B31"), // 朱砂红
            Color.parseColor("#2D4263"), // 霁蓝
            Color.parseColor("#F0E6D8"),
            Color.parseColor("#55C84B31"),
            Color.parseColor("#26000000"),
            Color.parseColor("#F5EFE4"),
            Color.parseColor("#33C84B31"),
        ),
        CYBER(
            "⚡ 赛博双生",
            intArrayOf(Color.parseColor("#080B10"), Color.parseColor("#0D1424"), Color.parseColor("#140A1E")),
            Color.parseColor("#0F172A"),
            Color.parseColor("#162038"),
            Color.parseColor("#E0F7FA"),
            Color.parseColor("#80DEEA"),
            Color.parseColor("#00F5D4"), // 荧光青
            Color.parseColor("#F72585"), // 霓虹粉
            Color.parseColor("#0A101E"),
            Color.parseColor("#5500F5D4"),
            Color.parseColor("#3300F5D4"),
            Color.parseColor("#1E293B"),
            Color.parseColor("#66F72585"),
        ),
        SUNSET(
            "🌅 落日余晖",
            intArrayOf(Color.parseColor("#1E1110"), Color.parseColor("#2C1518"), Color.parseColor("#14101A")),
            Color.parseColor("#2B181B"),
            Color.parseColor("#361F23"),
            Color.parseColor("#FFF3E0"),
            Color.parseColor("#FFCCBC"),
            Color.parseColor("#FF7A00"), // 晚霞橙
            Color.parseColor("#FF007F"), // 玫瑰红
            Color.parseColor("#201215"),
            Color.parseColor("#55FF7A00"),
            Color.parseColor("#33FFCCBC"),
            Color.parseColor("#3B2227"),
            Color.parseColor("#66FF7A00"),
        ),
    }

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

    private var currentTheme: PosterTheme = PosterTheme.OBSIDIAN
    private var bookA: Book? = null
    private var mindprintA: BookMindprint? = null
    private var bookB: Book? = null
    private var mindprintB: BookMindprint? = null
    private var similarity: Int = 96
    private var resonanceTrait: String = "跨媒介共鸣 · 灵魂契合"

    private var cachedCoverA: Bitmap? = null
    private var cachedCoverB: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val staticTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
    }

    companion object {
        private const val TICKET_ASPECT = 2.25f
        private const val PADDING_RATIO = 0.038f
        private const val MAIN_RATIO = 0.855f

        private const val EXPORT_WIDTH = 1080
        private const val EXPORT_HEIGHT = 2430
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * TICKET_ASPECT).toInt()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    fun setData(
        bookA: Book,
        mindprintA: BookMindprint?,
        bookB: Book,
        mindprintB: BookMindprint?,
        similarity: Int,
        resonanceTrait: String,
    ) {
        this.bookA = bookA
        this.mindprintA = mindprintA
        this.bookB = bookB
        this.mindprintB = mindprintB
        this.similarity = similarity
        this.resonanceTrait = resonanceTrait

        loadCoverBitmaps()
        requestLayout()
        invalidate()
    }

    fun setTheme(theme: PosterTheme) {
        this.currentTheme = theme
        invalidate()
    }

    fun getTheme(): PosterTheme = currentTheme

    private fun loadCoverBitmaps() {
        com.example.readtrace.util.CoverImageHelper.loadCoverBitmap(context, bookA?.coverUrl, 720, 1080) { bmp ->
            cachedCoverA = bmp
            invalidate()
        }
        com.example.readtrace.util.CoverImageHelper.loadCoverBitmap(context, bookB?.coverUrl, 720, 1080) { bmp ->
            cachedCoverB = bmp
            invalidate()
        }
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
            cornerRadius = tw * 0.042f,
            notchRadius = tw * 0.050f,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPoster(canvas, width.toFloat(), height.toFloat())
    }

    fun exportUltraHdBitmap(width: Int = EXPORT_WIDTH, height: Int = EXPORT_HEIGHT): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawPoster(canvas, width.toFloat(), height.toFloat())
        return bitmap
    }

    private fun drawPoster(canvas: Canvas, w: Float, h: Float) {
        val t = currentTheme
        val scale = w / 1080f

        // 1. 全屏流光渐变背景
        val bgShader = LinearGradient(0f, 0f, w, h, t.bgColors, null, Shader.TileMode.CLAMP)
        paint.shader = bgShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val box = computeTicketBox(w, h)

        // 2. 绘制实体复古票根外形路径（含月牙打孔缺口）
        val ticketPath = buildTicketOutline(box)

        // 2.1 票身外发光微边框
        paint.color = t.borderGlowColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * scale
        canvas.drawPath(ticketPath, paint)

        // 2.2 票身底色
        paint.color = t.ticketBgColor
        paint.style = Paint.Style.FILL
        canvas.drawPath(ticketPath, paint)

        // 2.3 副券区域底色微对比
        canvas.save()
        canvas.clipPath(ticketPath)
        val stubRect = RectF(box.left - 2f, box.splitY, box.right + 2f, box.bottom + 2f)
        paint.color = t.stubBgColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(stubRect, paint)
        canvas.restore()

        // 2.4 横向虚线撕缝 (Perforation Dashed Line)
        paint.color = t.perforationColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * scale
        paint.pathEffect = DashPathEffect(floatArrayOf(12f * scale, 8f * scale), 0f)
        canvas.drawLine(box.left + box.notchRadius, box.splitY, box.right - box.notchRadius, box.splitY, paint)
        paint.pathEffect = null

        // 3. 绘制主票区域（顶部放映头 + 作品 A + 中央双生雷达 + 作品 B）
        drawMainTicketSection(canvas, box, scale, t)

        // 4. 绘制底部副券区域（ADMIT TWO 印章 + 联名作品 + 条形码 + 契印）
        drawStubTicketSection(canvas, box, scale, t)
    }

    private fun buildTicketOutline(box: TicketBox): Path {
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

    private fun drawMainTicketSection(canvas: Canvas, box: TicketBox, scale: Float, t: PosterTheme) {
        val pad = box.width * 0.055f
        val w = box.width

        // A. 票头：双生心智联票 放映头
        val headerY = box.top + box.height * 0.024f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = t.accentAColor
        textPaint.textSize = w * 0.034f
        textPaint.isFakeBoldText = true
        canvas.drawText("READTRACE RESONANCE · 双生心智联票", box.left + pad, headerY, textPaint)

        textPaint.color = t.subTextColor
        textPaint.textSize = w * 0.028f
        textPaint.isFakeBoldText = false
        val specTag = "DUAL SPECIMEN · 4K"
        canvas.drawText(specTag, box.right - pad - textPaint.measureText(specTag), headerY, textPaint)

        val lineY = headerY + 14f * scale
        paint.color = t.perforationColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * scale
        canvas.drawLine(box.left + pad, lineY, box.right - pad, lineY, paint)

        // B. 灵魂契合度居中胶囊
        val badgeText = "★ $resonanceTrait · $similarity% 灵魂契合 ★"
        textPaint.textSize = w * 0.027f
        textPaint.isFakeBoldText = true
        val badgeW = textPaint.measureText(badgeText) + 36f * scale
        val badgeH = 40f * scale
        val badgeTop = lineY + 12f * scale
        val badgeRect = RectF(box.left + (w - badgeW) * 0.5f, badgeTop, box.left + (w + badgeW) * 0.5f, badgeTop + badgeH)

        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = t.accentAColor
        canvas.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = t.accentAColor
        canvas.drawText(badgeText, box.left + w * 0.5f, badgeTop + badgeH * 0.68f, textPaint)

        // C. 上方作品 A 纯竖排卡盒 (ALPHA 维度)
        val cardLeft = box.left + pad
        val cardW = w - pad * 2
        val cardH = 680f * scale
        val cardTopA = badgeTop + badgeH + 18f * scale

        drawPureVerticalWorkCard(
            canvas = canvas,
            left = cardLeft,
            top = cardTopA,
            width = cardW,
            height = cardH,
            scale = scale,
            book = bookA,
            coverBitmap = cachedCoverA,
            accentColor = t.accentAColor,
            dimensionTag = "NO.01 · ALPHA 维度",
            t = t,
        )

        // D. 中部双生六维雷达枢纽与连接光桥
        val bridgeTop = cardTopA + cardH + 10f * scale
        val bridgeH = 340f * scale
        val radarCenterY = bridgeTop + bridgeH * 0.5f
        val radarRadius = 120f * scale

        drawCentralDualRadar(canvas, box.left + w * 0.5f, radarCenterY, radarRadius, scale, mindprintA, mindprintB, t)

        // E. 下方作品 B 纯竖排卡盒 (OMEGA 维度)
        val cardTopB = bridgeTop + bridgeH + 10f * scale

        drawPureVerticalWorkCard(
            canvas = canvas,
            left = cardLeft,
            top = cardTopB,
            width = cardW,
            height = cardH,
            scale = scale,
            book = bookB,
            coverBitmap = cachedCoverB,
            accentColor = t.accentBColor,
            dimensionTag = "NO.02 · OMEGA 维度",
            t = t,
        )
    }

    /**
     * 绘制作品卡盒：
     * 1. 顶部维度胶囊与评分信息；
     * 2. 居中 2:3 纵向实体高清封面（不裁切变形，真实书籍/剧照比例）；
     * 3. 完整居中作品名《书名》；
     * 4. 创作者与标签流；
     * 5. 全宽嵌入式毛玻璃核心金句卡盒。
     */
    private fun drawPureVerticalWorkCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        book: Book?,
        coverBitmap: Bitmap?,
        accentColor: Int,
        dimensionTag: String,
        t: PosterTheme,
    ) {
        if (book == null) return
        val cardRect = RectF(left, top, left + width, top + height)

        // 1. 卡片底色与微光边框
        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * scale
        paint.color = accentColor
        canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, paint)

        // 2. 顶部维度标识与评分
        val headerY = top + 30f * scale
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 17f * scale
        textPaint.isFakeBoldText = true
        textPaint.color = accentColor
        canvas.drawText(dimensionTag, left + 20f * scale, headerY, textPaint)

        val rating = book.rating ?: 0.0
        val ratingStr = if (rating > 0.0) String.format(Locale.US, "%.1f", rating) else "4.9"
        val headerMeta = "${book.mediaType.emoji} ${book.category ?: "典藏"} · ⭐ $ratingStr · ${book.status.displayName}"
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 15.5f * scale
        textPaint.isFakeBoldText = false
        textPaint.color = t.subTextColor
        canvas.drawText(headerMeta, cardRect.right - 20f * scale, headerY, textPaint)

        // 3. 居中 2:3 纵向标准海报封面 (宽 200 * 高 290)
        val coverW = 200f * scale
        val coverH = 290f * scale
        val coverL = cardRect.centerX() - coverW * 0.5f
        val coverT = top + 46f * scale
        val coverRect = RectF(coverL, coverT, coverL + coverW, coverT + coverH)

        if (coverBitmap != null && !coverBitmap.isRecycled) {
            canvas.save()
            val clipPath = Path().apply { addRoundRect(coverRect, 12f * scale, 12f * scale, Path.Direction.CW) }
            canvas.clipPath(clipPath)

            val bmpW = coverBitmap.width.toFloat()
            val bmpH = coverBitmap.height.toFloat()
            val targetRatio = coverW / coverH
            val bmpRatio = bmpW / bmpH

            val srcRect = if (bmpRatio > targetRatio) {
                val cropW = bmpH * targetRatio
                val l = (bmpW - cropW) * 0.5f
                Rect(l.toInt(), 0, (l + cropW).toInt(), bmpH.toInt())
            } else {
                val cropH = bmpW / targetRatio
                val tp = (bmpH - cropH) * 0.25f
                Rect(0, tp.toInt(), bmpW.toInt(), (tp + cropH).toInt())
            }
            canvas.drawBitmap(coverBitmap, srcRect, coverRect, bitmapPaint)
            canvas.restore()
        } else {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(40, 255, 255, 255)
            canvas.drawRoundRect(coverRect, 12f * scale, 12f * scale, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 48f * scale
            canvas.drawText(book.mediaType.emoji, coverRect.centerX(), coverRect.centerY() + 16f * scale, textPaint)
        }

        // 封面发光描边
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * scale
        paint.color = Color.argb(140, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(coverRect, 12f * scale, 12f * scale, paint)

        // 4. 纵向文本流：作品标题
        val textPad = 20f * scale
        val contentW = (width - textPad * 2).toInt().coerceAtLeast(100)

        val titleY = coverRect.bottom + 16f * scale
        staticTextPaint.color = t.textColor
        staticTextPaint.textSize = 27f * scale
        staticTextPaint.isFakeBoldText = true

        val fullTitle = "《${book.title}》"
        val titleLayout = StaticLayout.Builder.obtain(
            fullTitle,
            0,
            fullTitle.length,
            staticTextPaint,
            contentW,
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate(left + textPad, titleY)
        titleLayout.draw(canvas)
        canvas.restore()

        // 5. 创作者与标签
        val infoY = titleY + titleLayout.height + 14f * scale
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 16.5f * scale
        textPaint.isFakeBoldText = false
        textPaint.color = t.subTextColor
        val tagStr = book.tags.filter { it.isNotBlank() }.take(2).joinToString("  ") { "#$it" }
        val authorText = if (tagStr.isNotBlank()) "创作者: ${book.author ?: "佚名"}   $tagStr" else "创作者: ${book.author ?: "佚名"}"
        val authorEllip = TextUtils.ellipsize(authorText, TextPaint(textPaint), width - textPad * 2, TextUtils.TruncateAt.END).toString()
        canvas.drawText(authorEllip, cardRect.centerX(), infoY, textPaint)

        // 6. 底部全宽嵌入式毛玻璃金句卡盒
        val quoteBoxTop = infoY + 12f * scale
        val quoteBoxH = (cardRect.bottom - 16f * scale - quoteBoxTop).coerceAtLeast(100f * scale)
        val quoteBoxRect = RectF(left + textPad, quoteBoxTop, cardRect.right - textPad, quoteBoxTop + quoteBoxH)

        paint.style = Paint.Style.FILL
        paint.color = t.quoteBgColor
        canvas.drawRoundRect(quoteBoxRect, 14f * scale, 14f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.0f * scale
        paint.color = Color.argb(80, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(quoteBoxRect, 14f * scale, 14f * scale, paint)

        val rawQuote = book.shortComment.takeUnless { it.isNullOrBlank() }
            ?: book.review.takeUnless { it.isNullOrBlank() }
            ?: "精神印记，静默于灵魂深处。"
        val quote = if (rawQuote.startsWith("“") || rawQuote.startsWith("\"")) rawQuote else "“$rawQuote”"

        val quotePadX = 16f * scale
        val quoteW = (quoteBoxRect.width() - quotePadX * 2).toInt().coerceAtLeast(100)

        staticTextPaint.color = t.textColor
        staticTextPaint.textSize = 18f * scale
        staticTextPaint.isFakeBoldText = false

        val quoteLayout = StaticLayout.Builder.obtain(
            quote,
            0,
            quote.length,
            staticTextPaint,
            quoteW,
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate(quoteBoxRect.left + quotePadX, quoteBoxRect.top + 12f * scale)
        quoteLayout.draw(canvas)
        canvas.restore()
    }

    /**
     * 绘制中央双生六维雷达枢纽与连接光桥
     */
    private fun drawCentralDualRadar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        scale: Float,
        mpA: BookMindprint?,
        mpB: BookMindprint?,
        t: PosterTheme,
    ) {
        val count = 6
        val labels = arrayOf("思想", "文笔", "情感", "逻辑", "难度", "治愈")

        // 1. 上下连接光导能量线
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * scale
        val beamShader = LinearGradient(cx, cy - radius - 45f * scale, cx, cy + radius + 45f * scale, intArrayOf(t.accentAColor, t.accentBColor), null, Shader.TileMode.CLAMP)
        paint.shader = beamShader
        canvas.drawLine(cx, cy - radius - 38f * scale, cx, cy - radius - 6f * scale, paint)
        canvas.drawLine(cx, cy + radius + 6f * scale, cx, cy + radius + 38f * scale, paint)
        paint.shader = null

        // 2. 六维多边形网格
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.0f * scale
        paint.color = t.radarGridColor

        for (ring in 1..4) {
            val r = radius * (ring / 4f)
            val ringPath = Path()
            for (i in 0 until count) {
                val angle = i * (2f * Math.PI.toFloat() / count) - Math.PI.toFloat() / 2f
                val px = cx + r * cos(angle)
                val py = cy + r * sin(angle)
                if (i == 0) ringPath.moveTo(px, py) else ringPath.lineTo(px, py)
            }
            ringPath.close()
            canvas.drawPath(ringPath, paint)
        }

        // 3. 轴线与维度标签
        for (i in 0 until count) {
            val angle = i * (2f * Math.PI.toFloat() / count) - Math.PI.toFloat() / 2f
            val px = cx + radius * cos(angle)
            val py = cy + radius * sin(angle)
            canvas.drawLine(cx, cy, px, py, paint)

            val lx = cx + (radius + 22f * scale) * cos(angle)
            val ly = cy + (radius + 22f * scale) * sin(angle)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 15.5f * scale
            textPaint.isFakeBoldText = true
            textPaint.color = t.subTextColor
            canvas.drawText(labels[i], lx, ly + 5f * scale, textPaint)
        }

        // 4. Work A 六维多边形填充
        mpA?.let { mp ->
            val scores = floatArrayOf(
                mp.depthScore.toFloat(),
                mp.artistryScore.toFloat(),
                mp.emotionScore.toFloat(),
                mp.logicScore.toFloat(),
                mp.difficultyScore.toFloat(),
                mp.healingScore.toFloat(),
            )
            drawPolygon(canvas, cx, cy, radius, scores, t.accentAColor, 85, scale)
        }

        // 5. Work B 六维多边形填充
        mpB?.let { mp ->
            val scores = floatArrayOf(
                mp.depthScore.toFloat(),
                mp.artistryScore.toFloat(),
                mp.emotionScore.toFloat(),
                mp.logicScore.toFloat(),
                mp.difficultyScore.toFloat(),
                mp.healingScore.toFloat(),
            )
            drawPolygon(canvas, cx, cy, radius, scores, t.accentBColor, 85, scale)
        }

        // 6. 中心共鸣星核胶囊
        val centerText = "⚡ $similarity% 共鸣星核"
        textPaint.textSize = 16.5f * scale
        val cW = textPaint.measureText(centerText) + 20f * scale
        val cH = 32f * scale
        val cRect = RectF(cx - cW * 0.5f, cy - cH * 0.5f, cx + cW * 0.5f, cy + cH * 0.5f)

        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(cRect, cH * 0.5f, cH * 0.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * scale
        paint.color = t.accentAColor
        canvas.drawRoundRect(cRect, cH * 0.5f, cH * 0.5f, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 15.5f * scale
        textPaint.color = t.textColor
        textPaint.isFakeBoldText = true
        canvas.drawText(centerText, cx, cy + 5f * scale, textPaint)
    }

    private fun drawPolygon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        scores: FloatArray,
        color: Int,
        alphaFill: Int,
        scale: Float,
    ) {
        val count = 6
        val path = Path()
        for (i in 0 until count) {
            val scoreRatio = (scores[i] / 10f).coerceIn(0.15f, 1f)
            val r = radius * scoreRatio
            val angle = i * (2f * Math.PI.toFloat() / count) - Math.PI.toFloat() / 2f
            val px = cx + r * cos(angle)
            val py = cy + r * sin(angle)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(alphaFill, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * scale
        paint.color = color
        canvas.drawPath(path, paint)
    }

    /**
     * 绘制底部副券（打孔撕票联）：
     * 左列：ADMIT TWO 纪念印章 + 双生作品联名 + 日期与编号
     * 右列：防伪条形码 + READTRACE VERIFIED + 双生契印
     */
    private fun drawStubTicketSection(canvas: Canvas, box: TicketBox, scale: Float, t: PosterTheme) {
        val w = box.width
        val h = box.stubHeight
        val pad = w * 0.055f
        val top = box.splitY

        // 1. 左列：ADMIT TWO 双生纪念印章 + 联名作品
        val stampW = w * 0.44f
        val stampH = h * 0.26f
        val stampRect = RectF(box.left + pad, top + h * 0.12f, box.left + pad + stampW, top + h * 0.12f + stampH)

        paint.color = t.accentAColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f * scale
        canvas.drawRoundRect(stampRect, 6f * scale, 6f * scale, paint)

        textPaint.color = t.accentAColor
        textPaint.textSize = stampH * 0.44f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("ADMIT TWO", stampRect.centerX(), stampRect.centerY() + stampH * 0.14f, textPaint)

        // 联名作品名
        val titleA = bookA?.title ?: "作品A"
        val titleB = bookB?.title ?: "作品B"
        textPaint.color = t.textColor
        textPaint.textSize = h * 0.105f
        textPaint.isFakeBoldText = true
        val pairStr = "《$titleA》×《$titleB》"
        val shortPair = TextUtils.ellipsize(pairStr, TextPaint(textPaint), stampW + 20f * scale, TextUtils.TruncateAt.END).toString()
        canvas.drawText(shortPair, stampRect.centerX(), top + h * 0.52f, textPaint)

        // 日期与唯一编码
        textPaint.color = t.subTextColor
        textPaint.textSize = h * 0.082f
        textPaint.isFakeBoldText = false
        val curDate = SimpleDateFormat("yyyy.MM.dd · 精神印记", Locale.getDefault()).format(Date())
        canvas.drawText(curDate, stampRect.centerX(), top + h * 0.70f, textPaint)

        val codeNum = ((bookA?.id ?: 100) * 1000 + (bookB?.id ?: 200)) % 9000 + 1000
        val codeStr = "NO.$codeNum-RT-RESONANCE"
        canvas.drawText(codeStr, stampRect.centerX(), top + h * 0.86f, textPaint)

        // 2. 右列：防伪条形码与双生契印
        val barcodeW = w * 0.36f
        val barcodeH = h * 0.30f
        val barcodeLeft = box.right - pad - barcodeW
        drawBarcode(canvas, barcodeLeft, top + h * 0.18f, barcodeW, barcodeH, t)

        textPaint.color = t.subTextColor
        textPaint.textSize = h * 0.070f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("READTRACE VERIFIED", barcodeLeft + barcodeW * 0.5f, top + h * 0.60f, textPaint)

        // 3. 朱砂古典印章 (双生契印)
        val sealSize = h * 0.28f
        val sealLeft = barcodeLeft + (barcodeW - sealSize) * 0.5f
        val sealTop = top + h * 0.66f
        drawChinatownSeal(canvas, sealLeft, sealTop, sealSize, "双生", "契印")
    }

    private fun drawBarcode(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, t: PosterTheme) {
        paint.color = t.textColor
        paint.style = Paint.Style.FILL

        val barCount = 28
        val gap = w / barCount
        val hash = (bookA?.title?.hashCode() ?: 123) xor (bookB?.title?.hashCode() ?: 456)

        for (i in 0 until barCount) {
            val isThick = ((hash shr (i % 16)) and 1) == 1
            val bw = if (isThick) gap * 0.68f else gap * 0.32f
            val bx = left + i * gap
            canvas.drawRect(bx, top, bx + bw, top + h, paint)
        }
    }

    private fun drawChinatownSeal(canvas: Canvas, x: Float, y: Float, size: Float, line1: String, line2: String) {
        val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        sealPaint.style = Paint.Style.STROKE
        sealPaint.strokeWidth = 2.0f
        sealPaint.color = Color.parseColor("#C62828")

        val rect = RectF(x, y, x + size, y + size)
        canvas.drawRoundRect(rect, 6f, 6f, sealPaint)

        sealPaint.style = Paint.Style.FILL
        sealPaint.textSize = size * 0.34f
        sealPaint.isFakeBoldText = true
        sealPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(line1, x + size * 0.5f, y + size * 0.42f, sealPaint)
        canvas.drawText(line2, x + size * 0.5f, y + size * 0.82f, sealPaint)
    }
}
