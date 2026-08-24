package com.example.readtrace.widget

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
import android.view.View
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
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
            intArrayOf(Color.parseColor("#12100E"), Color.parseColor("#1C171E"), Color.parseColor("#0F1318")),
            Color.parseColor("#1F1C24"),
            Color.parseColor("#F5EFE6"),
            Color.parseColor("#9E988F"),
            Color.parseColor("#F4A261"), // 琥珀金
            Color.parseColor("#9B5DE5"), // 幻夜紫
            Color.parseColor("#33FFFFFF"),
            Color.parseColor("#1AFFFFFF"),
            Color.parseColor("#4DF4A261"),
        ),
        RICE_PAPER(
            "📜 宣纸朱砂",
            intArrayOf(Color.parseColor("#FAF8F3"), Color.parseColor("#F3ECE0"), Color.parseColor("#EAE1D0")),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#1F1C18"),
            Color.parseColor("#6B6255"),
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

    // 画笔
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // 强制 1:1 方形
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
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

    private fun loadCoverBitmaps() {
        cachedCoverA = decodeCoverFile(bookA?.coverUrl)
        cachedCoverB = decodeCoverFile(bookB?.coverUrl)
    }

    private fun decodeCoverFile(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val file = File(path)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } else null
        }.getOrNull()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPoster(canvas, width.toFloat(), height.toFloat())
    }

    fun exportUltraHdBitmap(size: Int = 1080): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawPoster(canvas, size.toFloat(), size.toFloat())
        return bitmap
    }

    private fun drawPoster(canvas: Canvas, w: Float, h: Float) {
        val t = currentTheme
        val scale = w / 1080f

        // 1. 绘制背景渐变
        val bgShader = LinearGradient(0f, 0f, w, h, t.bgColors, null, Shader.TileMode.CLAMP)
        paint.shader = bgShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 2. 绘制边框与双生装饰微线
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = t.borderGlowColor
        canvas.drawRoundRect(RectF(24f * scale, 24f * scale, w - 24f * scale, h - 24f * scale), 24f * scale, 24f * scale, paint)

        // 3. 顶部 Header
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.textSize = 28f * scale
        textPaint.color = t.textColor
        canvas.drawText("✨ 跨 媒 介 灵 魂 共 鸣 · 双 生 印 记", w / 2f, 75f * scale, textPaint)

        // 共鸣度徽章胶囊
        val badgeText = "★ $resonanceTrait · $similarity% 契合度 ★"
        textPaint.textSize = 20f * scale
        val badgeW = textPaint.measureText(badgeText)
        val badgeRect = RectF(
            w / 2f - badgeW / 2f - 24f * scale,
            96f * scale,
            w / 2f + badgeW / 2f + 24f * scale,
            138f * scale,
        )
        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(badgeRect, 21f * scale, 21f * scale, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = t.accentAColor
        canvas.drawRoundRect(badgeRect, 21f * scale, 21f * scale, paint)

        textPaint.color = t.accentAColor
        canvas.drawText(badgeText, w / 2f, 124f * scale, textPaint)

        // 4. 核心三联布局：左书卡片 (180w) | 中枢双层雷达 (320w) | 右番卡片 (180w)
        val centerY = 370f * scale

        // 左侧卡片
        drawSideBookCard(
            canvas = canvas,
            centerX = 180f * scale,
            centerY = centerY,
            scale = scale,
            book = bookA,
            coverBitmap = cachedCoverA,
            accentColor = t.accentAColor,
            t = t,
        )

        // 中枢双层雷达
        drawCentralDualRadar(
            canvas = canvas,
            cx = w / 2f,
            cy = centerY,
            radius = 140f * scale,
            scale = scale,
            mpA = mindprintA,
            mpB = mindprintB,
            t = t,
        )

        // 右侧卡片
        drawSideBookCard(
            canvas = canvas,
            centerX = w - 180f * scale,
            centerY = centerY,
            scale = scale,
            book = bookB,
            coverBitmap = cachedCoverB,
            accentColor = t.accentBColor,
            t = t,
        )

        // 5. 底部高光双生金句合璧
        val quoteBoxTop = 575f * scale
        val quoteBoxHeight = 360f * scale

        drawSideQuoteCard(
            canvas = canvas,
            left = 50f * scale,
            top = quoteBoxTop,
            width = 465f * scale,
            height = quoteBoxHeight,
            scale = scale,
            book = bookA,
            accentColor = t.accentAColor,
            t = t,
        )

        drawSideQuoteCard(
            canvas = canvas,
            left = w - 515f * scale,
            top = quoteBoxTop,
            width = 465f * scale,
            height = quoteBoxHeight,
            scale = scale,
            book = bookB,
            accentColor = t.accentBColor,
            t = t,
        )

        // 6. 底部 Footer 与朱砂契印
        textPaint.textSize = 18f * scale
        textPaint.color = t.subTextColor
        val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(Date())
        canvas.drawText("READTRACE · 精神印记共鸣库 · $dateStr", w / 2f, 985f * scale, textPaint)

        // 朱砂小印章
        val sealW = 100f * scale
        val sealH = 34f * scale
        val sealRect = RectF(w / 2f - sealW / 2f, 1005f * scale, w / 2f + sealW / 2f, 1005f * scale + sealH)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = Color.parseColor("#C84B31")
        canvas.drawRoundRect(sealRect, 6f * scale, 6f * scale, paint)

        textPaint.textSize = 16f * scale
        textPaint.color = Color.parseColor("#C84B31")
        textPaint.isFakeBoldText = true
        canvas.drawText("双 生 契 印", w / 2f, 1028f * scale, textPaint)
    }

    private fun drawSideBookCard(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        scale: Float,
        book: Book?,
        coverBitmap: Bitmap?,
        accentColor: Int,
        t: PosterTheme,
    ) {
        if (book == null) return
        val cardW = 190f * scale
        val cardH = 285f * scale
        val rect = RectF(centerX - cardW / 2f, centerY - cardH / 2f, centerX + cardW / 2f, centerY + cardH / 2f)

        // 卡片底色
        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(rect, 16f * scale, 16f * scale, paint)

        // 边框微光
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * scale
        paint.color = accentColor
        canvas.drawRoundRect(rect, 16f * scale, 16f * scale, paint)

        // 封面图 (2:3)
        val coverW = 160f * scale
        val coverH = 200f * scale
        val coverRect = RectF(centerX - coverW / 2f, rect.top + 15f * scale, centerX + coverW / 2f, rect.top + 15f * scale + coverH)

        if (coverBitmap != null) {
            canvas.save()
            val path = Path().apply { addRoundRect(coverRect, 10f * scale, 10f * scale, Path.Direction.CW) }
            canvas.clipPath(path)
            canvas.drawBitmap(coverBitmap, null, coverRect, paint)
            canvas.restore()
        } else {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#282522")
            canvas.drawRoundRect(coverRect, 10f * scale, 10f * scale, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 36f * scale
            canvas.drawText(book.mediaType.emoji, centerX, coverRect.centerY() + 12f * scale, textPaint)
        }

        // 标题与媒介
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.textSize = 19f * scale
        textPaint.color = t.textColor
        val title = if (book.title.length > 7) book.title.take(6) + "…" else book.title
        canvas.drawText(title, centerX, rect.bottom - 36f * scale, textPaint)

        textPaint.textSize = 14f * scale
        textPaint.color = accentColor
        val sub = "${book.mediaType.emoji} ${book.mediaType.displayName} · ${book.category ?: "典藏"}"
        canvas.drawText(sub, centerX, rect.bottom - 14f * scale, textPaint)
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

        // 1. 绘制网格线
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * scale
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

        // 轴线
        for (i in 0 until count) {
            val angle = i * (2f * Math.PI.toFloat() / count) - Math.PI.toFloat() / 2f
            val px = cx + radius * cos(angle)
            val py = cy + radius * sin(angle)
            canvas.drawLine(cx, cy, px, py, paint)

            // 绘制标签
            val lx = cx + (radius + 22f * scale) * cos(angle)
            val ly = cy + (radius + 22f * scale) * sin(angle)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 14f * scale
            textPaint.color = t.subTextColor
            canvas.drawText(labels[i], lx, ly + 5f * scale, textPaint)
        }

        // 2. 绘制多边形 A (琥珀色/主色A)
        mpA?.let { mp ->
            val scores = floatArrayOf(
                mp.depthScore.toFloat(),
                mp.artistryScore.toFloat(),
                mp.emotionScore.toFloat(),
                mp.logicScore.toFloat(),
                mp.difficultyScore.toFloat(),
                mp.healingScore.toFloat(),
            )
            drawPolygon(canvas, cx, cy, radius, scores, t.accentAColor, 90, scale)
        }

        // 3. 绘制多边形 B (幻夜紫/主色B)
        mpB?.let { mp ->
            val scores = floatArrayOf(
                mp.depthScore.toFloat(),
                mp.artistryScore.toFloat(),
                mp.emotionScore.toFloat(),
                mp.logicScore.toFloat(),
                mp.difficultyScore.toFloat(),
                mp.healingScore.toFloat(),
            )
            drawPolygon(canvas, cx, cy, radius, scores, t.accentBColor, 90, scale)
        }

        // 中枢 VS 标志
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 15f * scale
        textPaint.color = t.textColor
        textPaint.isFakeBoldText = true
        canvas.drawText("⚡ 6D 共鸣", cx, cy + 5f * scale, textPaint)
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
            val scoreRatio = (scores[i] / 10f).coerceIn(0.1f, 1f)
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

    private fun drawSideQuoteCard(
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
        canvas.drawRoundRect(rect, 16f * scale, 16f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * scale
        paint.color = accentColor
        canvas.drawRoundRect(rect, 16f * scale, 16f * scale, paint)

        // 顶部小题头
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 17f * scale
        textPaint.color = accentColor
        textPaint.isFakeBoldText = true
        canvas.drawText("${book.mediaType.emoji} 《${book.title}》· 高光金句", left + 18f * scale, top + 34f * scale, textPaint)

        // 金句正文
        val quote = book.shortComment.takeUnless { it.isNullOrBlank() }
            ?: book.review.takeUnless { it.isNullOrBlank() }
            ?: "精神印记，静默于灵魂深处。"

        textPaint.textSize = 15.5f * scale
        textPaint.color = t.textColor
        textPaint.isFakeBoldText = false

        // 优雅多行断句绘制
        val textW = width - 36f * scale
        val lines = splitTextToLines(quote, textPaint, textW, maxLines = 7)
        var lineY = top + 72f * scale
        lines.forEach { line ->
            canvas.drawText(line, left + 18f * scale, lineY, textPaint)
            lineY += 28f * scale
        }

        // 作者归属
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 13.5f * scale
        textPaint.color = t.subTextColor
        canvas.drawText("—— ${book.author ?: "未知"}", rect.right - 18f * scale, rect.bottom - 16f * scale, textPaint)
    }

    private fun splitTextToLines(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length && result.size < maxLines) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null)
            if (count <= 0) break
            var line = text.substring(start, start + count)
            if (result.size == maxLines - 1 && start + count < text.length) {
                line = if (line.length > 2) line.dropLast(1) + "…" else line + "…"
            }
            result.add(line)
            start += count
        }
        return result
    }
}
