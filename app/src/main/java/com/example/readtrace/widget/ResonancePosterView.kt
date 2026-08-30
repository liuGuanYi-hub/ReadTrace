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
 * 🌌 跨媒介双生共鸣典藏联票视图 (ResonancePosterView)
 *
 * 核心升级与高密度美学排版：
 * 1. 【1 : 1.65 黄金紧凑实体联票比例】：彻底消除竖向虚浮与空白死黑，内容紧凑充实；
 * 2. 【左右分栏全息作品卡盒】：
 *    - 左侧：2:3 实体精装封面 + 载体/评分/年代角标；
 *    - 右侧：作品名 + 创作者/标签 + 心智基因 4 维能量条（思想、文笔、情感、治愈）；
 *    - 底部：紧凑自适应毛玻璃金句盒（居中文本，杜绝大片空黑）；
 * 3. 【中枢高能引力双生雷达】：
 *    - 双轨发光能量光桥连接两部作品；
 *    - 双层半透明对比多边形叠加，重叠处高能交融发光；
 *    - 六顶点详细分值对比（思想、文笔、情感、逻辑、难度、治愈）；
 *    - 底部双生心智特质解析微文案；
 * 4. 【底部月牙打孔副券】：
 *    - ADMIT TWO 双生纪念印章 + 联名档案 + 防伪条形码 + 灵魂双生契印。
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
        private const val TICKET_ASPECT = 1.65f
        private const val PADDING_RATIO = 0.032f
        private const val MAIN_RATIO = 0.815f

        private const val EXPORT_WIDTH = 1080
        private const val EXPORT_HEIGHT = 1782
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
            cornerRadius = tw * 0.040f,
            notchRadius = tw * 0.048f,
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
        paint.strokeWidth = 2.2f * scale
        paint.pathEffect = DashPathEffect(floatArrayOf(12f * scale, 8f * scale), 0f)
        canvas.drawLine(box.left + box.notchRadius, box.splitY, box.right - box.notchRadius, box.splitY, paint)
        paint.pathEffect = null

        // 3. 绘制主票区域（顶部票头 + 作品 A + 中央双生雷达 + 作品 B）
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
        val pad = box.width * 0.05f
        val w = box.width

        // A. 票头：双生心智联票 放映头
        val headerY = box.top + box.height * 0.026f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = t.accentAColor
        textPaint.textSize = w * 0.030f
        textPaint.isFakeBoldText = true
        canvas.drawText("READTRACE RESONANCE · 双生联票", box.left + pad, headerY, textPaint)

        textPaint.color = t.subTextColor
        textPaint.textSize = w * 0.026f
        textPaint.isFakeBoldText = false
        val specTag = "DUAL SPECIMEN · 4K"
        canvas.drawText(specTag, box.right - pad - textPaint.measureText(specTag), headerY, textPaint)

        val lineY = headerY + 11f * scale
        paint.color = t.perforationColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.0f * scale
        canvas.drawLine(box.left + pad, lineY, box.right - pad, lineY, paint)

        // B. 灵魂契合度居中胶囊
        val badgeText = "★ $resonanceTrait · $similarity% 灵魂契合 ★"
        textPaint.textSize = w * 0.026f
        textPaint.isFakeBoldText = true
        val badgeW = textPaint.measureText(badgeText) + 32f * scale
        val badgeH = 34f * scale
        val badgeTop = lineY + 9f * scale
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

        // C. 上方作品 A 高密度卡盒 (ALPHA 维度)
        val cardLeft = box.left + pad
        val cardW = w - pad * 2
        val cardH = 385f * scale
        val cardTopA = badgeTop + badgeH + 12f * scale

        drawHighDensityWorkCard(
            canvas = canvas,
            left = cardLeft,
            top = cardTopA,
            width = cardW,
            height = cardH,
            scale = scale,
            book = bookA,
            mindprint = mindprintA,
            coverBitmap = cachedCoverA,
            accentColor = t.accentAColor,
            dimensionTag = "🪐 NO.01 · ALPHA 维度",
            t = t,
        )

        // D. 中部双生六维雷达枢纽与连接光桥
        val bridgeTop = cardTopA + cardH + 10f * scale
        val bridgeH = 285f * scale
        val radarCenterY = bridgeTop + bridgeH * 0.44f
        val radarRadius = 90f * scale

        drawCentralDualRadar(canvas, box.left + w * 0.5f, radarCenterY, radarRadius, scale, mindprintA, mindprintB, t, bridgeTop, bridgeH)

        // E. 下方作品 B 高密度卡盒 (OMEGA 维度)
        val cardTopB = bridgeTop + bridgeH + 8f * scale

        drawHighDensityWorkCard(
            canvas = canvas,
            left = cardLeft,
            top = cardTopB,
            width = cardW,
            height = cardH,
            scale = scale,
            book = bookB,
            mindprint = mindprintB,
            coverBitmap = cachedCoverB,
            accentColor = t.accentBColor,
            dimensionTag = "✨ NO.02 · OMEGA 维度",
            t = t,
        )
    }

    /**
     * 绘制高密度专业作品卡盒：
     * 1. 顶部维度胶囊、载体与评分；
     * 2. 左侧 2:3 实体精装封面 + 年代徽章；
     * 3. 右侧作品名 + 创作者/标签 + 心智基因 4 维能量条；
     * 4. 底部紧凑自适应毛玻璃金句卡盒（彻底告别大面积空黑）。
     */
    private fun drawHighDensityWorkCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        book: Book?,
        mindprint: BookMindprint?,
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
        canvas.drawRoundRect(cardRect, 14f * scale, 14f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = Color.argb(160, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(cardRect, 14f * scale, 14f * scale, paint)

        // 2. 顶部维度标识与评分
        val headerY = top + 22f * scale
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 15f * scale
        textPaint.isFakeBoldText = true
        textPaint.color = accentColor
        canvas.drawText(dimensionTag, left + 14f * scale, headerY, textPaint)

        val rating = book.rating ?: 0.0
        val ratingStr = if (rating > 0.0) String.format(Locale.US, "%.1f", rating) else "4.9"
        val headerMeta = "${book.mediaType.emoji} ${book.category ?: "典藏"} · ⭐ $ratingStr · ${book.status.displayName}"
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 13.5f * scale
        textPaint.isFakeBoldText = false
        textPaint.color = t.subTextColor
        canvas.drawText(headerMeta, cardRect.right - 14f * scale, headerY, textPaint)

        // 3. 左侧：2:3 实体精装封面
        val padX = 14f * scale
        val coverW = 135f * scale
        val coverH = 195f * scale
        val coverL = left + padX
        val coverT = top + 34f * scale
        val coverRect = RectF(coverL, coverT, coverL + coverW, coverT + coverH)

        if (coverBitmap != null && !coverBitmap.isRecycled) {
            canvas.save()
            val clipPath = Path().apply { addRoundRect(coverRect, 8f * scale, 8f * scale, Path.Direction.CW) }
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
            canvas.drawRoundRect(coverRect, 8f * scale, 8f * scale, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 38f * scale
            canvas.drawText(book.mediaType.emoji, coverRect.centerX(), coverRect.centerY() + 12f * scale, textPaint)
        }

        // 封面高光描边
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.0f * scale
        paint.color = Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(coverRect, 8f * scale, 8f * scale, paint)

        // 4. 右侧：作品大标题 + 创作者/标签 + 心智基因能量进度条
        val rightL = coverRect.right + 14f * scale
        val rightW = (cardRect.right - rightL - padX).toInt().coerceAtLeast(100)

        // 4.1 标题
        val titleY = coverT + 18f * scale
        staticTextPaint.color = t.textColor
        staticTextPaint.textSize = 21f * scale
        staticTextPaint.isFakeBoldText = true

        val fullTitle = "《${book.title}》"
        val titleLayout = StaticLayout.Builder.obtain(
            fullTitle,
            0,
            fullTitle.length,
            staticTextPaint,
            rightW,
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate(rightL, coverT)
        titleLayout.draw(canvas)
        canvas.restore()

        // 4.2 创作者与标签
        val infoY = coverT + titleLayout.height + 14f * scale
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 13.5f * scale
        textPaint.isFakeBoldText = false
        textPaint.color = t.subTextColor
        val tagStr = book.tags.filter { it.isNotBlank() }.take(2).joinToString(" ") { "#$it" }
        val authorText = if (tagStr.isNotBlank()) "创作者: ${book.author ?: "佚名"} · $tagStr" else "创作者: ${book.author ?: "佚名"}"
        val authorEllip = TextUtils.ellipsize(authorText, TextPaint(textPaint), rightW.toFloat(), TextUtils.TruncateAt.END).toString()
        canvas.drawText(authorEllip, rightL, infoY, textPaint)

        // 4.3 心智基因 4 维能量条 (思想、文笔、情感、治愈)
        val barTop = infoY + 12f * scale
        val barGap = 21f * scale
        val barW = rightW.toFloat() - 65f * scale
        val barH = 6f * scale

        val depthScore = (mindprint?.depthScore ?: 8.5).toFloat()
        val artistryScore = (mindprint?.artistryScore ?: 9.0).toFloat()
        val emotionScore = (mindprint?.emotionScore ?: 8.8).toFloat()
        val healingScore = (mindprint?.healingScore ?: 8.2).toFloat()

        val traits = listOf(
            Triple("🧠 思想", depthScore, accentColor),
            Triple("🎨 文笔", artistryScore, t.accentAColor),
            Triple("💖 情感", emotionScore, t.accentBColor),
            Triple("🍵 治愈", healingScore, Color.parseColor("#4ECCA3"))
        )

        traits.forEachIndexed { idx, (label, score, color) ->
            val curY = barTop + idx * barGap

            // 标签文本
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 12f * scale
            textPaint.color = t.subTextColor
            canvas.drawText(label, rightL, curY + 5f * scale, textPaint)

            val barStartX = rightL + 58f * scale

            // 进度条背景槽
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(35, 255, 255, 255)
            val bgBarRect = RectF(barStartX, curY, barStartX + barW, curY + barH)
            canvas.drawRoundRect(bgBarRect, barH * 0.5f, barH * 0.5f, paint)

            // 进度条填充
            val progressW = (barW * (score / 10f).coerceIn(0.1f, 1f))
            paint.color = color
            val fillBarRect = RectF(barStartX, curY, barStartX + progressW, curY + barH)
            canvas.drawRoundRect(fillBarRect, barH * 0.5f, barH * 0.5f, paint)

            // 分值
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 12f * scale
            textPaint.color = t.textColor
            textPaint.isFakeBoldText = true
            val scoreStr = String.format(Locale.US, "%.1f", score)
            canvas.drawText(scoreStr, rightL + rightW, curY + 5f * scale, textPaint)
            textPaint.isFakeBoldText = false
        }

        // 5. 底部紧凑毛玻璃金句卡盒 (自适应精致高度，杜绝大片空黑)
        val quoteBoxTop = coverRect.bottom + 10f * scale
        val quoteBoxH = 105f * scale
        val quoteBoxRect = RectF(left + padX, quoteBoxTop, cardRect.right - padX, quoteBoxTop + quoteBoxH)

        paint.style = Paint.Style.FILL
        paint.color = t.quoteBgColor
        canvas.drawRoundRect(quoteBoxRect, 10f * scale, 10f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.0f * scale
        paint.color = Color.argb(70, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(quoteBoxRect, 10f * scale, 10f * scale, paint)

        val rawQuote = book.shortComment.takeUnless { it.isNullOrBlank() }
            ?: book.review.takeUnless { it.isNullOrBlank() }
            ?: "精神印记，静默于灵魂深处。"
        val quote = if (rawQuote.startsWith("“") || rawQuote.startsWith("\"")) rawQuote else "“$rawQuote”"

        val quotePadX = 14f * scale
        val quoteW = (quoteBoxRect.width() - quotePadX * 2).toInt().coerceAtLeast(100)

        staticTextPaint.color = t.textColor
        staticTextPaint.textSize = 15f * scale
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
     * 绘制中央双生六维雷达枢纽与高能连接光桥
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
        bridgeTop: Float,
        bridgeH: Float,
    ) {
        val count = 6
        val labels = arrayOf("思想", "文笔", "情感", "逻辑", "难度", "治愈")

        // 1. 上下连接光导能量线
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f * scale
        val beamShader = LinearGradient(cx, cy - radius - 35f * scale, cx, cy + radius + 35f * scale, intArrayOf(t.accentAColor, t.accentBColor), null, Shader.TileMode.CLAMP)
        paint.shader = beamShader
        canvas.drawLine(cx, cy - radius - 28f * scale, cx, cy - radius - 4f * scale, paint)
        canvas.drawLine(cx, cy + radius + 4f * scale, cx, cy + radius + 28f * scale, paint)
        paint.shader = null

        // 2. 六维多边形网格 (3 层同心多边形)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.0f * scale
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

        textPaint.textSize = 12f * scale
        textPaint.isFakeBoldText = true

        for (i in 0 until count) {
            val angle = i * (2f * Math.PI.toFloat() / count) - Math.PI.toFloat() / 2f
            val endX = cx + radius * cos(angle)
            val endY = cy + radius * sin(angle)
            canvas.drawLine(cx, cy, endX, endY, paint)

            // 顶点标签与数值对比
            val labelR = radius + 18f * scale
            val lx = cx + labelR * cos(angle)
            val ly = cy + labelR * sin(angle)

            textPaint.color = t.textColor
            textPaint.textAlign = when {
                cos(angle) > 0.3 -> Paint.Align.LEFT
                cos(angle) < -0.3 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val valStr = "${labels[i]} ${String.format(Locale.US, "%.1f", scoresA[i])}|${String.format(Locale.US, "%.1f", scoresB[i])}"
            canvas.drawText(valStr, lx, ly + 4f * scale, textPaint)
        }

        // 3. Work A 六维多边形填充 (ALPHA 维度)
        drawPolygon(canvas, cx, cy, radius, scoresA, t.accentAColor, 75, scale)

        // 4. Work B 六维多边形填充 (OMEGA 维度)
        drawPolygon(canvas, cx, cy, radius, scoresB, t.accentBColor, 75, scale)

        // 5. 中心共鸣星核胶囊
        val centerText = "⚡ $similarity% 共鸣星核"
        textPaint.textSize = 14f * scale
        val cW = textPaint.measureText(centerText) + 20f * scale
        val cH = 28f * scale
        val cRect = RectF(cx - cW * 0.5f, cy - cH * 0.5f, cx + cW * 0.5f, cy + cH * 0.5f)

        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(cRect, cH * 0.5f, cH * 0.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * scale
        paint.color = t.accentAColor
        canvas.drawRoundRect(cRect, cH * 0.5f, cH * 0.5f, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 13.5f * scale
        textPaint.color = t.textColor
        textPaint.isFakeBoldText = true
        canvas.drawText(centerText, cx, cy + 4.5f * scale, textPaint)

        // 6. 双生共鸣心智特质解析微文案
        val insightText = "「 跨越媒介叙事容器 · 殊途同归的精神共鸣 」"
        textPaint.color = t.accentAColor
        textPaint.textSize = 13f * scale
        textPaint.isFakeBoldText = false
        canvas.drawText(insightText, cx, bridgeTop + bridgeH - 6f * scale, textPaint)
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
        val pad = w * 0.05f
        val top = box.splitY

        // 1. 左列：ADMIT TWO 双生纪念印章 + 联名作品
        val stampW = w * 0.45f
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
