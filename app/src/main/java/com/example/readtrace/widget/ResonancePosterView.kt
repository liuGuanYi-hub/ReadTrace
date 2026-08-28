package com.example.readtrace.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class ResonancePosterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class PosterTheme(
        val displayName: String,
        val bgColors: IntArray,
        val cardBgColor: Int,
        val textColor: Int,
        val subTextColor: Int,
        val accentAColor: Int,
        val accentBColor: Int,
        val radarGridColor: Int,
        val quoteBgColor: Int,
        val borderGlowColor: Int,
    ) {
        OBSIDIAN(
            "🌌 黑曜星云",
            intArrayOf(Color.parseColor("#0E0C0B"), Color.parseColor("#17131A"), Color.parseColor("#0C0E14")),
            Color.parseColor("#18141C"),
            Color.parseColor("#F5EFE6"),
            Color.parseColor("#9E988F"),
            Color.parseColor("#F4A261"), // 琥珀金
            Color.parseColor("#A855F7"), // 幻夜紫
            Color.parseColor("#33FFFFFF"),
            Color.parseColor("#1AFFFFFF"),
            Color.parseColor("#4DF4A261"),
        ),
        RICE_PAPER(
            "📜 宣纸朱砂",
            intArrayOf(Color.parseColor("#FAF8F3"), Color.parseColor("#F3ECE0"), Color.parseColor("#EAE1D0")),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#1F1C18"),
            Color.parseColor("#666666"),
            Color.parseColor("#C84B31"), // 朱砂红
            Color.parseColor("#2D4263"), // 霁蓝
            Color.parseColor("#26000000"),
            Color.parseColor("#F0EAE1"),
            Color.parseColor("#33C84B31"),
        ),
        CYBER(
            "⚡ 赛博双生",
            intArrayOf(Color.parseColor("#080B10"), Color.parseColor("#0D1424"), Color.parseColor("#140A1E")),
            Color.parseColor("#111A2E"),
            Color.parseColor("#E0F7FA"),
            Color.parseColor("#80DEEA"),
            Color.parseColor("#00F5D4"), // 荧光青
            Color.parseColor("#F72585"), // 霓虹粉
            Color.parseColor("#3300F5D4"),
            Color.parseColor("#1A00F5D4"),
            Color.parseColor("#66F72585"),
        ),
        SUNSET(
            "🌅 落日余晖",
            intArrayOf(Color.parseColor("#1E1110"), Color.parseColor("#2C1518"), Color.parseColor("#14101A")),
            Color.parseColor("#2B181B"),
            Color.parseColor("#FFF3E0"),
            Color.parseColor("#FFCCBC"),
            Color.parseColor("#FF7A00"), // 晚霞橙
            Color.parseColor("#FF007F"), // 玫瑰红
            Color.parseColor("#33FFCCBC"),
            Color.parseColor("#1AFF7A00"),
            Color.parseColor("#66FF7A00"),
        )
    }

    private var currentTheme: PosterTheme = PosterTheme.OBSIDIAN
    private var bookA: Book? = null
    private var mindprintA: BookMindprint? = null
    private var bookB: Book? = null
    private var mindprintB: BookMindprint? = null
    private var similarity: Int = 94
    private var resonanceTrait: String = "存在主义思辨 · 终极孤独"

    private var cachedCoverA: Bitmap? = null
    private var cachedCoverB: Bitmap? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val staticTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 1.62f).toInt()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    fun setData(
        bookA: Book,
        mindprintA: BookMindprint,
        bookB: Book,
        mindprintB: BookMindprint,
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
        loadBitmap(bookA?.coverUrl) { bmp ->
            cachedCoverA = bmp
            invalidate()
        }
        loadBitmap(bookB?.coverUrl) { bmp ->
            cachedCoverB = bmp
            invalidate()
        }
    }

    private fun loadBitmap(url: String?, onLoaded: (Bitmap?) -> Unit) {
        if (url.isNullOrBlank()) {
            onLoaded(null)
            return
        }
        Thread {
            try {
                val bmp = if (url.startsWith("/")) {
                    val file = File(url)
                    if (file.exists()) BitmapFactory.decodeFile(url) else null
                } else if (url.startsWith("http")) {
                    val stream = java.net.URL(url).openStream()
                    BitmapFactory.decodeStream(stream)
                } else null

                post { onLoaded(bmp) }
            } catch (_: Exception) {
                post { onLoaded(null) }
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPoster(canvas, width.toFloat(), height.toFloat())
    }

    fun exportUltraHdBitmap(width: Int = 1080, height: Int = 1750): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawPoster(canvas, width.toFloat(), height.toFloat())
        return bitmap
    }

    private fun drawPoster(canvas: Canvas, w: Float, h: Float) {
        val t = currentTheme
        val scale = w / 1080f

        val bgShader = LinearGradient(0f, 0f, w, h, t.bgColors, null, Shader.TileMode.CLAMP)
        paint.shader = bgShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * scale
        paint.color = t.borderGlowColor
        val outerMargin = 24f * scale
        canvas.drawRoundRect(
            RectF(outerMargin, outerMargin, w - outerMargin, h - outerMargin),
            24f * scale,
            24f * scale,
            paint,
        )

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.textSize = 36f * scale
        textPaint.color = t.textColor
        canvas.drawText("✨ 跨 媒 介 灵 魂 共 鸣 · 双 生 印 记", w / 2f, 75f * scale, textPaint)

        val badgeText = "★ $resonanceTrait · $similarity% 灵魂契合 ★"
        textPaint.textSize = 21f * scale
        val badgeW = textPaint.measureText(badgeText)
        val badgeH = 46f * scale
        val badgeTop = 98f * scale
        val badgeRect = RectF(
            w / 2f - badgeW / 2f - 24f * scale,
            badgeTop,
            w / 2f + badgeW / 2f + 24f * scale,
            badgeTop + badgeH,
        )
        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = t.accentAColor
        canvas.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, paint)

        textPaint.color = t.accentAColor
        canvas.drawText(badgeText, w / 2f, badgeTop + badgeH * 0.68f, textPaint)

        val cardTop = 168f * scale
        val cardH = 430f * scale
        val cardW = 440f * scale
        val cardLeftA = 60f * scale
        val cardLeftB = w - 60f * scale - cardW

        drawDualStarCard(canvas, cardLeftA, cardTop, cardW, cardH, scale, bookA, cachedCoverA, t.accentAColor, t)
        drawDualStarCard(canvas, cardLeftB, cardTop, cardW, cardH, scale, bookB, cachedCoverB, t.accentBColor, t)

        val beamY = cardTop + cardH * 0.38f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * scale
        val beamShader = LinearGradient(cardLeftA + cardW, beamY, cardLeftB, beamY, intArrayOf(t.accentAColor, t.accentBColor), null, Shader.TileMode.CLAMP)
        paint.shader = beamShader
        canvas.drawLine(cardLeftA + cardW - 8f * scale, beamY, cardLeftB + 8f * scale, beamY, paint)
        paint.shader = null

        val centerPulseRadius = 30f * scale
        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawCircle(w / 2f, beamY, centerPulseRadius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = t.textColor
        canvas.drawCircle(w / 2f, beamY, centerPulseRadius, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 20f * scale
        textPaint.color = t.textColor
        textPaint.isFakeBoldText = true
        canvas.drawText("⚡", w / 2f, beamY + 7f * scale, textPaint)

        val radarCenterY = 825f * scale
        val radarRadius = 160f * scale

        drawCentralDualRadar(canvas, w / 2f, radarCenterY, radarRadius, scale, mindprintA, mindprintB, t)

        val quoteW = w - 120f * scale
        val quoteH = 175f * scale
        val quoteTopA = 1040f * scale
        val quoteTopB = quoteTopA + quoteH + 20f * scale

        drawFullWidthQuoteCard(canvas, 60f * scale, quoteTopA, quoteW, quoteH, scale, bookA, t.accentAColor, t)
        drawFullWidthQuoteCard(canvas, 60f * scale, quoteTopB, quoteW, quoteH, scale, bookB, t.accentBColor, t)

        val footerY = h - 60f * scale
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 19f * scale
        textPaint.color = t.subTextColor
        textPaint.isFakeBoldText = false
        val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(Date())
        canvas.drawText("✦ READTRACE · 跨媒介精神印记共鸣库 · $dateStr ✦", w / 2f, footerY - 32f * scale, textPaint)

        val sealW = 120f * scale
        val sealH = 36f * scale
        val sealRect = RectF(w / 2f - sealW / 2f, footerY - 20f * scale, w / 2f + sealW / 2f, footerY - 20f * scale + sealH)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = Color.parseColor("#C84B31")
        canvas.drawRoundRect(sealRect, 6f * scale, 6f * scale, paint)

        textPaint.textSize = 17f * scale
        textPaint.color = Color.parseColor("#C84B31")
        textPaint.isFakeBoldText = true
        canvas.drawText("双 生 契 印", w / 2f, footerY + 5f * scale, textPaint)
    }

    private fun drawDualStarCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        book: Book?,
        coverBitmap: Bitmap?,
        accentColor: Int,
        t: PosterTheme,
    ) {
        if (book == null) return
        val cardRect = RectF(left, top, left + width, top + height)

        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * scale
        paint.color = accentColor
        canvas.drawRoundRect(cardRect, 18f * scale, 18f * scale, paint)

        val coverMargin = 16f * scale
        val coverW = width - coverMargin * 2
        val coverH = height * 0.65f
        val coverRect = RectF(left + coverMargin, top + coverMargin, left + coverMargin + coverW, top + coverMargin + coverH)

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
                val tp = (bmpH - cropH) * 0.5f
                Rect(0, tp.toInt(), bmpW.toInt(), (tp + cropH).toInt())
            }
            canvas.drawBitmap(coverBitmap, srcRect, coverRect, paint)
            canvas.restore()
        } else {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(40, 255, 255, 255)
            canvas.drawRoundRect(coverRect, 12f * scale, 12f * scale, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 48f * scale
            canvas.drawText(book.mediaType.emoji, coverRect.centerX(), coverRect.centerY() + 16f * scale, textPaint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * scale
        paint.color = Color.argb(80, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(coverRect, 12f * scale, 12f * scale, paint)

        val titleY = coverRect.bottom + 38f * scale
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.isFakeBoldText = true
        textPaint.textSize = 27f * scale
        textPaint.color = t.textColor
        val title = if (book.title.length > 11) book.title.take(10) + "…" else book.title
        canvas.drawText(title, left + coverMargin, titleY, textPaint)

        val metaY = titleY + 30f * scale
        textPaint.textSize = 19f * scale
        textPaint.isFakeBoldText = false
        textPaint.color = accentColor
        val meta = "${book.mediaType.emoji} ${book.mediaType.displayName} · ${book.category ?: "典藏"}"
        canvas.drawText(meta, left + coverMargin, metaY, textPaint)

        val authorY = metaY + 26f * scale
        textPaint.textSize = 17f * scale
        textPaint.color = t.subTextColor
        val author = "创作者: " + (book.author ?: "佚名").let { if (it.length > 12) it.take(11) + "…" else it }
        canvas.drawText(author, left + coverMargin, authorY, textPaint)
    }

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

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * scale
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

        for (i in 0 until count) {
            val angle = i * (2f * Math.PI.toFloat() / count) - Math.PI.toFloat() / 2f
            val px = cx + radius * cos(angle)
            val py = cy + radius * sin(angle)
            canvas.drawLine(cx, cy, px, py, paint)

            val lx = cx + (radius + 28f * scale) * cos(angle)
            val ly = cy + (radius + 28f * scale) * sin(angle)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 19f * scale
            textPaint.isFakeBoldText = true
            textPaint.color = t.subTextColor
            canvas.drawText(labels[i], lx, ly + 7f * scale, textPaint)
        }

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

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 21f * scale
        textPaint.color = t.textColor
        textPaint.isFakeBoldText = true
        canvas.drawText("⚡ $similarity% 共鸣星核", cx, cy + 8f * scale, textPaint)
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
        paint.strokeWidth = 2.8f * scale
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun drawFullWidthQuoteCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        book: Book?,
        accentColor: Int,
        t: PosterTheme,
    ) {
        if (book == null) return
        val rect = RectF(left, top, left + width, top + height)

        paint.style = Paint.Style.FILL
        paint.color = t.quoteBgColor
        canvas.drawRoundRect(rect, 14f * scale, 14f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * scale
        paint.color = accentColor
        canvas.drawRoundRect(rect, 14f * scale, 14f * scale, paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 21f * scale
        textPaint.color = accentColor
        textPaint.isFakeBoldText = true
        canvas.drawText("${book.mediaType.emoji} 《${book.title}》· 核心金句印记", left + 20f * scale, top + 36f * scale, textPaint)

        val rawQuote = book.shortComment.takeUnless { it.isNullOrBlank() }
            ?: book.review.takeUnless { it.isNullOrBlank() }
            ?: "精神印记，静默于灵魂深处。"
        val quote = if (rawQuote.startsWith("“") || rawQuote.startsWith("\"")) rawQuote else "“$rawQuote”"

        val padX = 20f * scale
        val contentW = (width - padX * 2).toInt().coerceAtLeast(100)

        staticTextPaint.color = t.textColor
        staticTextPaint.textSize = 21f * scale
        staticTextPaint.isFakeBoldText = false

        val staticLayout = StaticLayout.Builder.obtain(
            quote,
            0,
            quote.length,
            staticTextPaint,
            contentW,
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setLineSpacing(2f * scale, 1.15f)
            .build()

        canvas.save()
        canvas.translate(left + padX, top + 54f * scale)
        staticLayout.draw(canvas)
        canvas.restore()

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 17f * scale
        textPaint.color = t.subTextColor
        textPaint.isFakeBoldText = false
        canvas.drawText("—— ${book.author ?: "佚名"}", rect.right - 20f * scale, rect.bottom - 16f * scale, textPaint)
    }
}
