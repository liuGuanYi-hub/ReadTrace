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

/**
 * 🌌 跨媒介双生共鸣先锋微卡视图 (ResonancePosterView)
 * 采用先锋「上下纵向双联对峙 / 灵魂共鸣对决」Bento 架构。
 * 完美适配 9:16 手机全屏黄金比例，彻底消除横向压缩与文字截断。
 */
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
            Color.parseColor("#1F222E"),
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
            Color.parseColor("#F7F2EB"),
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
            Color.parseColor("#33FF7A00"),
            Color.parseColor("#66FF7A00"),
        )
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 1.7777778f).toInt() // 9:16 全屏标准比例
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

    fun exportUltraHdBitmap(width: Int = 1080, height: Int = 1920): Bitmap {
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

        // 2. 外层微光流体边框
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * scale
        paint.color = t.borderGlowColor
        val outerMargin = 24f * scale
        canvas.drawRoundRect(
            RectF(outerMargin, outerMargin, w - outerMargin, h - outerMargin),
            28f * scale,
            28f * scale,
            paint,
        )

        // 3. 顶部主标题与灵魂契合度胶囊
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.textSize = 36f * scale
        textPaint.color = t.textColor
        canvas.drawText("✨ 跨 媒 介 灵 魂 共 鸣 · 双 生 印 记", w / 2f, 75f * scale, textPaint)

        val badgeText = "★ $resonanceTrait · $similarity% 灵魂契合 ★"
        textPaint.textSize = 21f * scale
        val badgeW = textPaint.measureText(badgeText)
        val badgeH = 46f * scale
        val badgeTop = 100f * scale
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

        // 4. 上方作品 A Bento 卡盒 (Full Width, 2:3 大封面 + 完整标题 + 创作者 + 内嵌金句)
        val cardLeft = 60f * scale
        val cardW = w - 120f * scale
        val cardH = 500f * scale
        val cardTopA = 175f * scale

        drawVerticalWorkBentoCard(
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

        // 5. 中间共鸣枢纽与六维灵魂雷达 (Central Resonance Hub)
        val radarCenterY = 955f * scale
        val radarRadius = 150f * scale

        drawCentralDualRadar(canvas, w / 2f, radarCenterY, radarRadius, scale, mindprintA, mindprintB, t)

        // 6. 下方作品 B Bento 卡盒 (Full Width, 2:3 大封面 + 完整标题 + 创作者 + 内嵌金句)
        val cardTopB = 1220f * scale

        drawVerticalWorkBentoCard(
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

        // 7. 底部印记与日期落款
        val footerY = h - 65f * scale
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

    /**
     * 绘制全宽纵向作品卡盒 (Full-Width Work Bento Card)
     * 左侧：2:3 黄金比例高清立体封面
     * 右侧：顶部标签 + 完整大标题 + 创作者 + 评分芯片 + 内嵌毛玻璃金句气泡
     */
    private fun drawVerticalWorkBentoCard(
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

        // 卡片底色与流光外边框
        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(cardRect, 22f * scale, 22f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * scale
        paint.color = accentColor
        canvas.drawRoundRect(cardRect, 22f * scale, 22f * scale, paint)

        // 左侧 2:3 竖版大封面 (宽:高 = 230:345)
        val coverMarginL = 20f * scale
        val coverMarginT = 20f * scale
        val coverW = 230f * scale
        val coverH = 345f * scale
        val coverRect = RectF(left + coverMarginL, top + coverMarginT, left + coverMarginL + coverW, top + coverMarginT + coverH)

        if (coverBitmap != null && !coverBitmap.isRecycled) {
            canvas.save()
            val clipPath = Path().apply { addRoundRect(coverRect, 14f * scale, 14f * scale, Path.Direction.CW) }
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
            canvas.drawRoundRect(coverRect, 14f * scale, 14f * scale, paint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 54f * scale
            canvas.drawText(book.mediaType.emoji, coverRect.centerX(), coverRect.centerY() + 18f * scale, textPaint)
        }

        // 封面发光描边
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * scale
        paint.color = Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(coverRect, 14f * scale, 14f * scale, paint)

        // 封面左上角媒介角标
        val mediaBadgeText = "${book.mediaType.emoji} ${book.mediaType.displayName}"
        textPaint.textSize = 15f * scale
        textPaint.isFakeBoldText = true
        val mbW = textPaint.measureText(mediaBadgeText) + 16f * scale
        val mbH = 26f * scale
        val mbRect = RectF(coverRect.left + 6f * scale, coverRect.top + 6f * scale, coverRect.left + 6f * scale + mbW, coverRect.top + 6f * scale + mbH)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(200, 16, 16, 24)
        canvas.drawRoundRect(mbRect, 6f * scale, 6f * scale, paint)
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(mediaBadgeText, mbRect.left + 8f * scale, mbRect.top + 18f * scale, textPaint)

        // 封面底部维度角标 (例如：NO.01 · ALPHA 维度)
        val dimY = coverRect.bottom + 36f * scale
        textPaint.textSize = 17f * scale
        textPaint.isFakeBoldText = true
        textPaint.color = accentColor
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(dimensionTag, coverRect.centerX(), dimY, textPaint)

        // 右侧信息区域
        val rightLeft = coverRect.right + 22f * scale
        val rightW = (cardRect.right - 20f * scale - rightLeft).toInt().coerceAtLeast(100)

        // 1. 顶部元数据行
        val metaY = top + 36f * scale
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 17f * scale
        textPaint.isFakeBoldText = false
        val rating = book.rating ?: 0.0
        val ratingStr = if (rating > 0.0) String.format(Locale.US, "%.1f", rating) else "4.9"
        val meta = "${book.mediaType.emoji} ${book.category ?: "典藏"} · ⭐ 评分 $ratingStr · ${book.status.displayName}"
        canvas.drawText(meta, rightLeft, metaY, textPaint)

        // 2. 完整作品大标题 (使用 StaticLayout 支持长标题优雅换行)
        staticTextPaint.color = t.textColor
        staticTextPaint.textSize = 30f * scale
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
            .setMaxLines(2)
            .setLineSpacing(2f * scale, 1.1f)
            .build()

        canvas.save()
        canvas.translate(rightLeft, top + 48f * scale)
        titleLayout.draw(canvas)
        canvas.restore()

        val titleHeight = titleLayout.height.toFloat()

        // 3. 创作者与标签
        val authorY = top + 52f * scale + titleHeight + 20f * scale
        textPaint.textSize = 19f * scale
        textPaint.color = t.subTextColor
        textPaint.isFakeBoldText = false
        val author = "创作者: ${book.author ?: "未知"}"
        canvas.drawText(author, rightLeft, authorY, textPaint)

        val tagsY = authorY + 24f * scale
        val tagList = book.tags.filter { it.isNotBlank() }.take(3)
        if (tagList.isNotEmpty()) {
            textPaint.textSize = 16f * scale
            textPaint.color = accentColor
            textPaint.isFakeBoldText = false
            val tagStr = "🏷️ " + tagList.joinToString("  ") { "#$it" }
            canvas.drawText(tagStr, rightLeft, tagsY, textPaint)
        }

        // 4. 内嵌毛玻璃核心金句卡盒 (Embedded Soul Quote Box)
        val quoteBoxTop = (if (tagList.isNotEmpty()) tagsY else authorY) + 14f * scale
        val quoteBoxH = (cardRect.bottom - 18f * scale - quoteBoxTop).coerceAtLeast(130f * scale)
        val quoteBoxRect = RectF(rightLeft, quoteBoxTop, cardRect.right - 18f * scale, quoteBoxTop + quoteBoxH)

        paint.style = Paint.Style.FILL
        paint.color = t.quoteBgColor
        canvas.drawRoundRect(quoteBoxRect, 14f * scale, 14f * scale, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * scale
        paint.color = Color.argb(90, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        canvas.drawRoundRect(quoteBoxRect, 14f * scale, 14f * scale, paint)

        // 金句内容
        val rawQuote = book.shortComment.takeUnless { it.isNullOrBlank() }
            ?: book.review.takeUnless { it.isNullOrBlank() }
            ?: "精神印记，静默于灵魂深处。"
        val quote = if (rawQuote.startsWith("“") || rawQuote.startsWith("\"")) rawQuote else "“$rawQuote”"

        val quotePadX = 16f * scale
        val quoteContentW = (quoteBoxRect.width() - quotePadX * 2).toInt().coerceAtLeast(100)

        staticTextPaint.color = t.textColor
        staticTextPaint.textSize = 21f * scale
        staticTextPaint.isFakeBoldText = false

        val quoteLayout = StaticLayout.Builder.obtain(
            quote,
            0,
            quote.length,
            staticTextPaint,
            quoteContentW,
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(3)
            .setLineSpacing(3f * scale, 1.2f)
            .build()

        canvas.save()
        canvas.translate(quoteBoxRect.left + quotePadX, quoteBoxRect.top + 16f * scale)
        quoteLayout.draw(canvas)
        canvas.restore()

        // 金句底部署名
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 16f * scale
        textPaint.color = t.subTextColor
        textPaint.isFakeBoldText = false
        canvas.drawText("—— ${book.author ?: "佚名"}", quoteBoxRect.right - 14f * scale, quoteBoxRect.bottom - 12f * scale, textPaint)
    }

    /**
     * 绘制中央灵魂共鸣六维雷达与连接光桥
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
        paint.strokeWidth = 2f * scale
        val beamShader = LinearGradient(cx, cy - radius - 60f * scale, cx, cy + radius + 60f * scale, intArrayOf(t.accentAColor, t.accentBColor), null, Shader.TileMode.CLAMP)
        paint.shader = beamShader
        canvas.drawLine(cx, cy - radius - 50f * scale, cx, cy - radius - 8f * scale, paint)
        canvas.drawLine(cx, cy + radius + 8f * scale, cx, cy + radius + 50f * scale, paint)
        paint.shader = null

        // 2. 六维多边形网格
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

        // 3. 轴线与维度标签
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
        textPaint.textSize = 20f * scale
        val cW = textPaint.measureText(centerText) + 24f * scale
        val cH = 38f * scale
        val cRect = RectF(cx - cW / 2f, cy - cH / 2f, cx + cW / 2f, cy + cH / 2f)

        paint.style = Paint.Style.FILL
        paint.color = t.cardBgColor
        canvas.drawRoundRect(cRect, cH * 0.5f, cH * 0.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * scale
        paint.color = t.accentAColor
        canvas.drawRoundRect(cRect, cH * 0.5f, cH * 0.5f, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 19f * scale
        textPaint.color = t.textColor
        textPaint.isFakeBoldText = true
        canvas.drawText(centerText, cx, cy + 6f * scale, textPaint)
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
}
