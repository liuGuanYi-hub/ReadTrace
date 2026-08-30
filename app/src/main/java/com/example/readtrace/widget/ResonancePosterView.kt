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
 * 🌌 跨媒介双生共鸣典藏长卷视图 (ResonancePosterView)
 *
 * 核心架构：【三幕式纵向双生艺术展卷 (Three-Act Grand Curation Scroll)】
 * 设计参考：Awwwards / Land-book / Siteinspire 顶级数字展厅美学
 *
 * 1. 【第一幕 · 顶部】：NO.01 · ALPHA 纵向神殿大卡 (Full-Width 2:3 巨幅实体大封面 440x640 + 典雅排版 + 金句铭刻)；
 * 2. 【第二幕 · 中部】：NO.02 · OMEGA 纵向神殿大卡 (Full-Width 2:3 巨幅实体大封面 440x640 + 典雅排版 + 金句铭刻)；
 * 3. 【第三幕 · 底部】：双星交汇 · 深度共鸣数据与六维雷达大中枢：
 *    - 4 维心智基因能量双轨对比矩阵 (思想、文笔、情感、治愈)；
 *    - 160px 超大双层叠加六维引力雷达枢纽；
 *    - 共鸣星核与特质解析箴言；
 *    - 实体复古打孔撕票副券 (ADMIT TWO + 联名档案 + 条形码 + 契印)。
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
            Color.parseColor("#221B29"),
            Color.parseColor("#F5EFE6"),
            Color.parseColor("#A8A196"),
            Color.parseColor("#F4A261"), // 琥珀金
            Color.parseColor("#A855F7"), // 幻夜紫
            Color.parseColor("#131017"),
            Color.parseColor("#55F4A261"),
            Color.parseColor("#33FFFFFF"),
            Color.parseColor("#2C2236"),
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
            Color.parseColor("#F3ECE0"),
            Color.parseColor("#33C84B31"),
        ),
        CYBER(
            "⚡ 赛博双生",
            intArrayOf(Color.parseColor("#080B10"), Color.parseColor("#0D1424"), Color.parseColor("#140A1E")),
            Color.parseColor("#0F172A"),
            Color.parseColor("#16223A"),
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
            Color.parseColor("#381E23"),
            Color.parseColor("#FFF3E0"),
            Color.parseColor("#FFCCBC"),
            Color.parseColor("#FF7A00"), // 晚霞橙
            Color.parseColor("#FF007F"), // 玫瑰红
            Color.parseColor("#201215"),
            Color.parseColor("#55FF7A00"),
            Color.parseColor("#33FFCCBC"),
            Color.parseColor("#42232A"),
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
        private const val TICKET_ASPECT = 3.12f
        private const val PADDING_RATIO = 0.035f
        private const val MAIN_RATIO = 0.908f

        private const val EXPORT_WIDTH = 1080
        private const val EXPORT_HEIGHT = 3370
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
            cornerRadius = tw * 0.038f,
            notchRadius = tw * 0.046f,
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

        // 1. 全屏流光背景
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
        paint.strokeWidth = 3.5f * scale
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
        paint.strokeWidth = 2.4f * scale
        paint.pathEffect = DashPathEffect(floatArrayOf(14f * scale, 10f * scale), 0f)
        canvas.drawLine(box.left + box.notchRadius, box.splitY, box.right - box.notchRadius, box.splitY, paint)
        paint.pathEffect = null

        // 3. 绘制主票区域（三幕式纵向双生艺术展卷）
        drawThreeActMainSection(canvas, box, scale, t)

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

    /**
     * 三幕式纵向双生艺术展卷：
     * Act 1: 作品 A · ALPHA 纵向神殿大卡 (巨幅封面 440x640 + 居中标题 + 金句)
     * Act 2: 作品 B · OMEGA 纵向神殿大卡 (巨幅封面 440x640 + 居中标题 + 金句)
     * Act 3: 双星交汇 · 深度共鸣数据与六维雷达大中枢 (4维能量对比矩阵 + 160px 超大六维雷达 + 箴言)
     */
    private fun drawThreeActMainSection(canvas: Canvas, box: TicketBox, scale: Float, t: PosterTheme) {
        val pad = box.width * 0.045f
        val w = box.width
        val cardW = w - pad * 2
        val cardLeft = box.left + pad

        // A. 票头：双生心智联票 放映头
        val headerY = box.top + 48f * scale
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = t.accentAColor
        textPaint.textSize = 28f * scale
        textPaint.isFakeBoldText = true
        canvas.drawText("READTRACE RESONANCE · 双生心智联票", box.left + pad, headerY, textPaint)

        textPaint.color = t.subTextColor
        textPaint.textSize = 23f * scale
        textPaint.isFakeBoldText = false
        val specTag = "DUAL SPECIMEN · 4K ULTRA HD"
        canvas.drawText(specTag, box.right - pad - textPaint.measureText(specTag), headerY, textPaint)

        val lineY = headerY + 14f * scale
        paint.color = t.perforationColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * scale
        canvas.drawLine(box.left + pad, lineY, box.right - pad, lineY, paint)

        // B. 顶部契合度微胶囊
        val badgeText = "★ $resonanceTrait · $similarity% 灵魂契合 ★"
        textPaint.textSize = 22f * scale
        textPaint.isFakeBoldText = true
        val badgeW = textPaint.measureText(badgeText) + 36f * scale
        val badgeH = 38f * scale
        val badgeTop = lineY + 12f * scale
        val badgeRect = RectF(box.left + (w - badgeW) * 0.5f, badgeTop, box.left + (w + badgeW) * 0.5f, badgeTop + badgeH)

        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * scale
        paint.color = t.accentAColor
        canvas.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = t.accentAColor
        canvas.drawText(badgeText, box.left + w * 0.5f, badgeTop + badgeH * 0.68f, textPaint)

        // =========================================================================
        // 【第一幕】：作品 A · ALPHA 纵向神殿大卡 (Full-Width 纵向巨幅封面 440x640)
        // =========================================================================
        val act1Top = badgeTop + badgeH + 18f * scale
        val act1H = 920f * scale
        drawGrandHeroCard(
            canvas = canvas,
            left = cardLeft,
            top = act1Top,
            width = cardW,
            height = act1H,
            scale = scale,
            book = bookA,
            coverBitmap = cachedCoverA,
            accentColor = t.accentAColor,
            dimensionTag = "🪐 NO.01 · ALPHA 维度神殿",
            t = t,
        )

        // 贯通过渡连接光束 1 ➔ 2
        val trans1_Y1 = act1Top + act1H + 6f * scale
        val trans1_Y2 = trans1_Y1 + 24f * scale
        val beamCX = box.left + w * 0.5f
        drawEnergyConnector(canvas, beamCX, trans1_Y1, trans1_Y2, t.accentAColor, t.accentBColor, scale)

        // =========================================================================
        // 【第二幕】：作品 B · OMEGA 纵向神殿大卡 (Full-Width 纵向巨幅封面 440x640)
        // =========================================================================
        val act2Top = trans1_Y2 + 6f * scale
        val act2H = 920f * scale
        drawGrandHeroCard(
            canvas = canvas,
            left = cardLeft,
            top = act2Top,
            width = cardW,
            height = act2H,
            scale = scale,
            book = bookB,
            coverBitmap = cachedCoverB,
            accentColor = t.accentBColor,
            dimensionTag = "✨ NO.02 · OMEGA 维度神殿",
            t = t,
        )

        // 贯通过渡连接光束 2 ➔ 3
        val trans2_Y1 = act2Top + act2H + 6f * scale
        val trans2_Y2 = trans2_Y1 + 24f * scale
        drawEnergyConnector(canvas, beamCX, trans2_Y1, trans2_Y2, t.accentBColor, t.accentAColor, scale)

        // =========================================================================
        // 【第三幕】：双星交汇 · 深度共鸣数据与六维雷达大中枢
        // =========================================================================
        val act3Top = trans2_Y2 + 6f * scale
        val act3H = 1050f * scale
        drawResonanceNexusSection(
            canvas = canvas,
            left = cardLeft,
            top = act3Top,
            width = cardW,
            height = act3H,
            scale = scale,
            mpA = mindprintA,
            mpB = mindprintB,
            t = t,
        )
    }

    /**
     * 绘制第一幕 / 第二幕：单作品全景纵向神殿大卡盒 (Awwwards 级别视觉排版)
     */
    private fun drawGrandHeroCard(
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

        // 1. 卡片底色与高光边框
        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * scale
        paint.color = Color.argb(160, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, paint)

        // 2. 顶部维度胶囊与媒介评分
        val headerY = top + 34f * scale
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 20f * scale
        textPaint.isFakeBoldText = true
        textPaint.color = accentColor
        canvas.drawText(dimensionTag, left + 22f * scale, headerY, textPaint)

        val rating = book.rating ?: 0.0
        val ratingStr = if (rating > 0.0) String.format(Locale.US, "%.1f", rating) else "4.9"
        val headerMeta = "${book.mediaType.emoji} ${book.category ?: "典藏"} · ⭐ $ratingStr · ${book.status.displayName}"
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 18f * scale
        textPaint.isFakeBoldText = false
        textPaint.color = t.subTextColor
        canvas.drawText(headerMeta, cardRect.right - 22f * scale, headerY, textPaint)

        // 3. 居中 2:3 巨幅实体精装大封面 (宽 440 * 高 620)
        val coverW = 440f * scale
        val coverH = 620f * scale
        val coverL = cardRect.centerX() - coverW * 0.5f
        val coverT = top + 52f * scale
        val coverRect = RectF(coverL, coverT, coverL + coverW, coverT + coverH)

        if (coverBitmap != null && !coverBitmap.isRecycled) {
            canvas.save()
            val clipPath = Path().apply { addRoundRect(coverRect, 16f * scale, 16f * scale, Path.Direction.CW) }
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
            canvas.drawRoundRect(coverRect, 16f * scale, 16f * scale, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 72f * scale
            canvas.drawText(book.mediaType.emoji, coverRect.centerX(), coverRect.centerY() + 24f * scale, textPaint)
        }

        // 封面高光描边
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * scale
        paint.color = Color.argb(160, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(coverRect, 16f * scale, 16f * scale, paint)

        // 4. 作品大标题 (居中加粗，大字号排版)
        val textPad = 24f * scale
        val contentW = (width - textPad * 2).toInt().coerceAtLeast(100)

        val titleY = coverRect.bottom + 22f * scale
        staticTextPaint.color = t.textColor
        staticTextPaint.textSize = 30f * scale
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
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate(left + textPad, titleY)
        titleLayout.draw(canvas)
        canvas.restore()

        // 5. 创作者与标签
        val infoY = titleY + titleLayout.height + 12f * scale
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 18f * scale
        textPaint.isFakeBoldText = false
        textPaint.color = t.subTextColor
        val tagStr = book.tags.filter { it.isNotBlank() }.take(2).joinToString(" ") { "#$it" }
        val authorText = if (tagStr.isNotBlank()) "创作者: ${book.author ?: "佚名"} · $tagStr" else "创作者: ${book.author ?: "佚名"}"
        val authorEllip = TextUtils.ellipsize(authorText, TextPaint(textPaint), width - textPad * 2, TextUtils.TruncateAt.END).toString()
        canvas.drawText(authorEllip, cardRect.centerX(), infoY, textPaint)

        // 6. 底部精选金句铭刻卡盒 (自适应居中排版)
        val quoteBoxTop = infoY + 16f * scale
        val quoteBoxH = (cardRect.bottom - 20f * scale - quoteBoxTop).coerceAtLeast(85f * scale)
        val quoteBoxRect = RectF(left + textPad, quoteBoxTop, cardRect.right - textPad, quoteBoxTop + quoteBoxH)

        paint.style = Paint.Style.FILL
        paint.color = t.quoteBgColor
        canvas.drawRoundRect(quoteBoxRect, 12f * scale, 12f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.0f * scale
        paint.color = Color.argb(80, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(quoteBoxRect, 12f * scale, 12f * scale, paint)

        val rawQuote = book.shortComment.takeUnless { it.isNullOrBlank() }
            ?: book.review.takeUnless { it.isNullOrBlank() }
            ?: "精神印记，静默于灵魂深处。"
        val quote = if (rawQuote.startsWith("“") || rawQuote.startsWith("\"")) rawQuote else "“$rawQuote”"

        val quotePadX = 18f * scale
        val quoteW = (quoteBoxRect.width() - quotePadX * 2).toInt().coerceAtLeast(100)

        staticTextPaint.color = t.textColor
        staticTextPaint.textSize = 17.5f * scale
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

        val textOffsetY = (quoteBoxH - quoteLayout.height) * 0.5f
        canvas.save()
        canvas.translate(quoteBoxRect.left + quotePadX, quoteBoxRect.top + textOffsetY.coerceAtLeast(6f * scale))
        quoteLayout.draw(canvas)
        canvas.restore()
    }

    /**
     * 绘制过渡连接光束
     */
    private fun drawEnergyConnector(canvas: Canvas, cx: Float, y1: Float, y2: Float, color1: Int, color2: Int, scale: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f * scale
        val beamShader = LinearGradient(cx, y1, cx, y2, intArrayOf(color1, color2), null, Shader.TileMode.CLAMP)
        paint.shader = beamShader
        canvas.drawLine(cx, y1, cx, y2, paint)
        paint.shader = null

        // 中心光点
        paint.style = Paint.Style.FILL
        paint.color = color1
        canvas.drawCircle(cx, (y1 + y2) * 0.5f, 3.5f * scale, paint)
    }

    /**
     * 绘制第三幕：双星交汇 · 深度共鸣数据与六维雷达大中枢
     */
    private fun drawResonanceNexusSection(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        mpA: BookMindprint?,
        mpB: BookMindprint?,
        t: PosterTheme,
    ) {
        val rect = RectF(left, top, left + width, top + height)

        // 1. 中枢大背景与流光边框
        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(rect, 18f * scale, 18f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * scale
        paint.color = Color.argb(120, 255, 255, 255)
        canvas.drawRoundRect(rect, 18f * scale, 18f * scale, paint)

        // 2. 中枢大标题与图例
        val headY = top + 34f * scale
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 20f * scale
        textPaint.isFakeBoldText = true
        textPaint.color = t.textColor
        canvas.drawText("🌌 双星深度心智共鸣与六维雷达", left + 22f * scale, headY, textPaint)

        // 图例：ALPHA 琥珀金 vs OMEGA 幻夜紫
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 15f * scale
        textPaint.isFakeBoldText = true
        textPaint.color = t.accentAColor
        val legendA = "■ ALPHA"
        val legendB = "■ OMEGA"
        canvas.drawText(legendA, rect.right - 22f * scale - 95f * scale, headY, textPaint)
        textPaint.color = t.accentBColor
        canvas.drawText(legendB, rect.right - 22f * scale, headY, textPaint)

        // 3. 4 维心智基因能量双轨对比矩阵 (思想、文笔、情感、治愈)
        val matrixTop = headY + 24f * scale
        val rowGap = 44f * scale
        val traits = listOf(
            Triple("🧠 思想", (mpA?.depthScore ?: 8.5).toFloat(), (mpB?.depthScore ?: 8.2).toFloat()),
            Triple("🎨 文笔", (mpA?.artistryScore ?: 9.2).toFloat(), (mpB?.artistryScore ?: 8.6).toFloat()),
            Triple("💖 情感", (mpA?.emotionScore ?: 8.8).toFloat(), (mpB?.emotionScore ?: 9.0).toFloat()),
            Triple("🍵 治愈", (mpA?.healingScore ?: 8.2).toFloat(), (mpB?.healingScore ?: 8.5).toFloat())
        )

        traits.forEachIndexed { idx, (label, scoreA, scoreB) ->
            val curY = matrixTop + idx * rowGap

            // 标签
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 15.5f * scale
            textPaint.color = t.textColor
            textPaint.isFakeBoldText = true
            canvas.drawText(label, left + 22f * scale, curY + 14f * scale, textPaint)

            val barStartX = left + 96f * scale
            val barW = width - 118f * scale - 130f * scale
            val barH = 7.5f * scale

            // 轨 A (ALPHA)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(35, 255, 255, 255)
            val bgRectA = RectF(barStartX, curY + 1f * scale, barStartX + barW, curY + 1f * scale + barH)
            canvas.drawRoundRect(bgRectA, barH * 0.5f, barH * 0.5f, paint)

            paint.color = t.accentAColor
            val fillW_A = barW * (scoreA / 10f).coerceIn(0.1f, 1f)
            val fillRectA = RectF(barStartX, curY + 1f * scale, barStartX + fillW_A, curY + 1f * scale + barH)
            canvas.drawRoundRect(fillRectA, barH * 0.5f, barH * 0.5f, paint)

            // 轨 B (OMEGA)
            paint.color = Color.argb(35, 255, 255, 255)
            val bgRectB = RectF(barStartX, curY + 13f * scale, barStartX + barW, curY + 13f * scale + barH)
            canvas.drawRoundRect(bgRectB, barH * 0.5f, barH * 0.5f, paint)

            paint.color = t.accentBColor
            val fillW_B = barW * (scoreB / 10f).coerceIn(0.1f, 1f)
            val fillRectB = RectF(barStartX, curY + 13f * scale, barStartX + fillW_B, curY + 13f * scale + barH)
            canvas.drawRoundRect(fillRectB, barH * 0.5f, barH * 0.5f, paint)

            // 数值对比标签
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 14.5f * scale
            textPaint.color = t.accentAColor
            val scoreStrA = String.format(Locale.US, "%.1f", scoreA)
            val scoreStrB = String.format(Locale.US, "%.1f", scoreB)
            canvas.drawText(scoreStrA, rect.right - 22f * scale - 60f * scale, curY + 14f * scale, textPaint)
            textPaint.color = t.subTextColor
            canvas.drawText("vs", rect.right - 22f * scale - 40f * scale, curY + 14f * scale, textPaint)
            textPaint.color = t.accentBColor
            canvas.drawText(scoreStrB, rect.right - 22f * scale, curY + 14f * scale, textPaint)
        }

        // 4. 宏大 160px 超大双层叠加六维雷达
        val radarTop = matrixTop + 4 * rowGap + 20f * scale
        val radarH = 500f * scale
        val radarCenterY = radarTop + radarH * 0.44f
        val radarRadius = 160f * scale

        drawCentralDualRadar(canvas, rect.centerX(), radarCenterY, radarRadius, scale, mpA, mpB, t, radarTop, radarH)
    }

    /**
     * 绘制 160px 超大双层叠加六维雷达与共鸣星核
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
        radarTop: Float,
        radarH: Float,
    ) {
        val count = 6
        val labels = arrayOf("思想", "文笔", "情感", "逻辑", "难度", "治愈")

        // 1. 上下连接光导能量线
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.6f * scale
        val beamShader = LinearGradient(cx, cy - radius - 40f * scale, cx, cy + radius + 40f * scale, intArrayOf(t.accentAColor, t.accentBColor), null, Shader.TileMode.CLAMP)
        paint.shader = beamShader
        canvas.drawLine(cx, cy - radius - 32f * scale, cx, cy - radius - 6f * scale, paint)
        canvas.drawLine(cx, cy + radius + 6f * scale, cx, cy + radius + 32f * scale, paint)
        paint.shader = null

        // 2. 六维多边形网格 (3 层同心多边形)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * scale
        paint.color = t.radarGridColor

        val levels = floatArrayOf(0.33f, 0.66f, 1.0f)
        for (level in levels) {
            val gridPath = Path()
            for (i in 0 until count) {
                val r = radius * level
                val angle = i * (2f * Math.PI.toFloat() / count) - Math.PI.toFloat() / 2f
                val px = cx + r * cos(angle)
                val py = cy + r * sin(angle)
                if (i == 0) gridPath.moveTo(px, py) else gridPath.lineTo(px, py)
            }
            gridPath.close()
            canvas.drawPath(gridPath, paint)
        }

        // 放射轴线与顶点标签数值
        val scoresA = floatArrayOf(
            (mpA?.depthScore ?: 8.5).toFloat(),
            (mpA?.artistryScore ?: 9.2).toFloat(),
            (mpA?.emotionScore ?: 8.8).toFloat(),
            (mpA?.logicScore ?: 7.5).toFloat(),
            (mpA?.difficultyScore ?: 6.8).toFloat(),
            (mpA?.healingScore ?: 8.2).toFloat(),
        )

        val scoresB = floatArrayOf(
            (mpB?.depthScore ?: 8.2).toFloat(),
            (mpB?.artistryScore ?: 8.6).toFloat(),
            (mpB?.emotionScore ?: 9.0).toFloat(),
            (mpB?.logicScore ?: 8.0).toFloat(),
            (mpB?.difficultyScore ?: 6.2).toFloat(),
            (mpB?.healingScore ?: 8.5).toFloat(),
        )

        textPaint.textSize = 15.5f * scale
        textPaint.isFakeBoldText = true

        for (i in 0 until count) {
            val angle = i * (2f * Math.PI.toFloat() / count) - Math.PI.toFloat() / 2f
            val endX = cx + radius * cos(angle)
            val endY = cy + radius * sin(angle)
            canvas.drawLine(cx, cy, endX, endY, paint)

            // 顶点标签与数值对比
            val labelR = radius + 26f * scale
            val lx = cx + labelR * cos(angle)
            val ly = cy + labelR * sin(angle)

            textPaint.color = t.textColor
            textPaint.textAlign = when {
                cos(angle) > 0.3 -> Paint.Align.LEFT
                cos(angle) < -0.3 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val valStr = "${labels[i]} ${String.format(Locale.US, "%.1f", scoresA[i])}|${String.format(Locale.US, "%.1f", scoresB[i])}"
            canvas.drawText(valStr, lx, ly + 5.5f * scale, textPaint)
        }

        // 3. Work A 六维多边形填充 (ALPHA 维度)
        drawPolygon(canvas, cx, cy, radius, scoresA, t.accentAColor, 75, scale)

        // 4. Work B 六维多边形填充 (OMEGA 维度)
        drawPolygon(canvas, cx, cy, radius, scoresB, t.accentBColor, 75, scale)

        // 5. 中心共鸣星核胶囊
        val centerText = "⚡ $similarity% 共鸣星核"
        textPaint.textSize = 16.5f * scale
        val cW = textPaint.measureText(centerText) + 26f * scale
        val cH = 34f * scale
        val cRect = RectF(cx - cW * 0.5f, cy - cH * 0.5f, cx + cW * 0.5f, cy + cH * 0.5f)

        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(cRect, cH * 0.5f, cH * 0.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * scale
        paint.color = t.accentAColor
        canvas.drawRoundRect(cRect, cH * 0.5f, cH * 0.5f, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 15.5f * scale
        textPaint.color = t.textColor
        textPaint.isFakeBoldText = true
        canvas.drawText(centerText, cx, cy + 5.5f * scale, textPaint)

        // 6. 双生共鸣心智特质解析微文案
        val insightText = "「 跨越媒介叙事容器 · 殊途同归的精神共鸣 」"
        textPaint.color = t.accentAColor
        textPaint.textSize = 15.5f * scale
        textPaint.isFakeBoldText = false
        canvas.drawText(insightText, cx, radarTop + radarH - 12f * scale, textPaint)
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
        paint.strokeWidth = 2.4f * scale
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
        val pad = w * 0.045f
        val top = box.splitY

        // 1. 左列：ADMIT TWO 双生纪念印章 + 联名作品
        val stampW = w * 0.44f
        val stampH = h * 0.28f
        val stampRect = RectF(box.left + pad, top + h * 0.12f, box.left + pad + stampW, top + h * 0.12f + stampH)

        paint.color = t.accentAColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * scale
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
        textPaint.textSize = h * 0.10f
        textPaint.isFakeBoldText = true
        val pairStr = "《$titleA》×《$titleB》"
        val shortPair = TextUtils.ellipsize(pairStr, TextPaint(textPaint), stampW + 20f * scale, TextUtils.TruncateAt.END).toString()
        canvas.drawText(shortPair, stampRect.centerX(), top + h * 0.52f, textPaint)

        // 日期与唯一编码
        textPaint.color = t.subTextColor
        textPaint.textSize = h * 0.080f
        textPaint.isFakeBoldText = false
        val curDate = SimpleDateFormat("yyyy.MM.dd · 精神印记", Locale.getDefault()).format(Date())
        canvas.drawText(curDate, stampRect.centerX(), top + h * 0.70f, textPaint)

        val codeNum = ((bookA?.id ?: 100) * 1000 + (bookB?.id ?: 200)) % 9000 + 1000
        val codeStr = "NO.$codeNum-RT-RESONANCE"
        canvas.drawText(codeStr, stampRect.centerX(), top + h * 0.86f, textPaint)

        // 2. 右列：防伪条形码与双生契印
        val barcodeW = w * 0.36f
        val barcodeH = h * 0.36f
        val barcodeL = box.right - pad - barcodeW
        val barcodeT = top + h * 0.12f

        drawBarcode(canvas, barcodeL, barcodeT, barcodeW, barcodeH, t.textColor, scale)

        textPaint.color = t.subTextColor
        textPaint.textSize = h * 0.072f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        canvas.drawText("READTRACE VERIFIED", barcodeL + barcodeW * 0.5f, barcodeT + barcodeH + 14f * scale, textPaint)

        // 双生契印圆章
        val sealR = h * 0.18f
        val sealCX = barcodeL + barcodeW * 0.5f
        val sealCY = barcodeT + barcodeH + 46f * scale

        paint.color = t.accentBColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * scale
        canvas.drawCircle(sealCX, sealCY, sealR, paint)

        textPaint.color = t.accentBColor
        textPaint.textSize = sealR * 0.65f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("双生契印", sealCX, sealCY + sealR * 0.28f, textPaint)
    }

    private fun drawBarcode(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int, scale: Float) {
        val barPattern = booleanArrayOf(
            true, false, true, true, false, true, false, false, true, true, true, false, true, false,
            true, true, false, true, true, true, false, false, true, false, true, true, false, true,
            false, true, true, true, false, true, false, true, true, false, false, true, true, true,
        )
        val unitW = w / barPattern.size
        paint.color = color
        paint.style = Paint.Style.FILL
        for (i in barPattern.indices) {
            if (barPattern[i]) {
                val bx = x + i * unitW
                canvas.drawRect(bx, y, bx + unitW * 0.85f, y + h, paint)
            }
        }
    }
}
