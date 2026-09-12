package com.example.readtrace.widget

import android.content.Context
import android.graphics.*
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * 全息藏书长卷：把精神藏库当前筛选结果绘制成宣纸质感的双列藏书画卷。
 * 与 MediaTimelineScrollView 同族——支持现场绘制与离屏导出 1080P 超清长图两条路径；
 * 卡片为封面在左、文字在右的横向布局，每行两张；
 * 封面复用 CoverImageHelper 三级缓存，导出前以闩锁阻塞预热（仅限后台线程调用导出）。
 */
class LibraryScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var isDarkMode: Boolean = false

    var bookList: List<Book> = emptyList()
        private set

    /** 筛选摘要（调用方传入，如「📖 书籍 · 已读 · 评分 8.0~9.0」），展示于副标题 */
    var filterSummary: String = ""
        private set

    /** 封面位图内存缓存：按字节计量的 LRU 上限（约堆 1/8），与时间轴画卷同策略 */
    private val coverBitmaps = object : android.util.LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
    }

    // 绘制画笔
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private class MediaSection(val media: MediaType, val books: List<Book>)
    private var sections: List<MediaSection> = emptyList()

    /** 藏品跨媒介时按固定媒介顺序分章，单一媒介时整卷一体不分章 */
    private fun rebuildSections() {
        val distinctMedia = bookList.map { it.mediaType }.distinct()
        sections = if (distinctMedia.size <= 1) {
            listOf(MediaSection(distinctMedia.firstOrNull() ?: MediaType.BOOK, bookList))
        } else {
            listOf(MediaType.BOOK, MediaType.ANIME, MediaType.MOVIE, MediaType.GAME, MediaType.MUSIC)
                .mapNotNull { media ->
                    bookList.filter { it.mediaType == media }
                        .takeIf { it.isNotEmpty() }
                        ?.let { MediaSection(media, it) }
                }
        }
    }

    /**
     * 设置藏书长卷数据
     * @param books 藏品列表（当前筛选结果全量，顺序沿用调用方现有排序）
     * @param summary 筛选摘要文案
     * @param darkMode 暗夜和纸 / 白晶宣纸
     */
    fun setLibraryData(books: List<Book>, summary: String, darkMode: Boolean) {
        bookList = books
        filterSummary = summary
        isDarkMode = darkMode
        rebuildSections()

        // 异步预加载封面（现场绘制路径）；导出路径另有阻塞预热兜底
        bookList.forEach { book ->
            val url = book.coverUrl?.trim().orEmpty()
            if (url.isNotBlank() && coverBitmaps.get(url) == null) {
                CoverImageHelper.loadCoverBitmap(context, url, 200, 300) { bmp ->
                    if (bmp != null) {
                        coverBitmaps.put(url, bmp)
                        postInvalidate()
                    }
                }
            }
        }

        requestLayout()
        invalidate()
    }

    /** 导出结束后释放封面缓存（位图可能仍被 CoverImageHelper 共享持有，只逐出不 recycle） */
    fun releaseCovers() {
        coverBitmaps.evictAll()
    }

    /**
     * 离屏生成 1080P 超清宣纸藏书长图 Bitmap。
     * 内部会阻塞预热全部封面，必须在后台线程调用。
     */
    fun exportUltraHdBitmap(): Bitmap {
        preloadCoversBlocking()
        val targetWidth = 1080
        val targetHeight = calculateContentHeight(targetWidth.toFloat()).toInt().coerceAtLeast(800)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawScrollContent(canvas, targetWidth.toFloat(), targetHeight.toFloat())
        return bitmap
    }

    /**
     * 阻塞预热全部封面：CoverImageHelper 回调固定经主线程 Handler 派发，
     * 后台线程闩锁等待即可；主线程调用时直接跳过（不阻塞 UI）。
     */
    private fun preloadCoversBlocking(timeoutMs: Long = 20_000L) {
        if (Looper.myLooper() == Looper.getMainLooper()) return
        val urls = bookList.mapNotNull { it.coverUrl?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { coverBitmaps.get(it) == null }
        if (urls.isEmpty()) return

        val latch = CountDownLatch(urls.size)
        urls.forEach { url ->
            CoverImageHelper.loadCoverBitmap(context, url, 200, 300) { bmp ->
                if (bmp != null && !bmp.isRecycled) {
                    coverBitmaps.put(url, bmp)
                }
                latch.countDown()
            }
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    // ---------------------------------------------------------------- 卡片度量

    private class GridMetrics(
        val columns: Int,
        val margin: Float,
        val gap: Float,
        val rowGap: Float,
        val cardWidth: Float,
        val padX: Float,
        val padY: Float,
        val coverWidth: Float,
        val coverHeight: Float,
        val titleSize: Float,
        val metaSize: Float,
    ) {
        /** 封面右侧文本区可用宽度 */
        val textWidth: Float = cardWidth - padX * 2f - coverWidth - 14f
    }

    /**
     * 固定双列排布；仅当藏品规模极大时升到 3 列，
     * 防止长图高度失控导致 Bitmap 分配超限。
     */
    private fun gridMetricsFor(canvasWidth: Float): GridMetrics {
        val columns = if (bookList.size > 160) 3 else 2
        val margin = 55f
        val gap = 22f
        val rowGap = 24f
        val cardWidth = (canvasWidth - margin * 2f - gap * (columns - 1)) / columns
        val coverWidth = (cardWidth * 0.30f).coerceIn(96f, 190f)
        val titleSize = (cardWidth * 0.056f).coerceIn(20f, 32f)
        val metaSize = (cardWidth * 0.046f).coerceIn(16f, 24f)
        return GridMetrics(
            columns, margin, gap, rowGap, cardWidth,
            padX = 14f, padY = 16f,
            coverWidth = coverWidth, coverHeight = coverWidth * 1.5f,
            titleSize = titleSize, metaSize = metaSize,
        )
    }

    private class CardLayout(
        val titleLayout: StaticLayout,
        val coverWidth: Float,
        val coverHeight: Float,
        val height: Float,
    )

    private fun titleLayoutFor(book: Book, m: GridMetrics): StaticLayout {
        titlePaint.textSize = m.titleSize
        titlePaint.isFakeBoldText = true
        return StaticLayout.Builder.obtain(book.title, 0, book.title.length, titlePaint, m.textWidth.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3f, 1f)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    private fun cardLayoutFor(book: Book, m: GridMetrics): CardLayout {
        val titleLayout = titleLayoutFor(book, m)
        // 文本区：标题（≤2 行）+ 评分·状态行 + 作者·分类行，行进公式与 drawBookCard 保持一致
        val textH = titleLayout.height + 8f + m.metaSize * 1.25f + 6f + m.metaSize * 1.2f
        val height = max(m.coverHeight, textH) + m.padY * 2f
        return CardLayout(titleLayout, m.coverWidth, m.coverHeight, height)
    }

    private fun sectionHeaderHeight(): Float = 64f

    private fun calculateContentHeight(canvasWidth: Float): Float {
        val m = gridMetricsFor(canvasWidth)
        var y = 270f // 头部题字与印章区域
        val grouped = sections.size > 1
        sections.forEach { section ->
            if (grouped) y += sectionHeaderHeight()
            section.books.chunked(m.columns).forEach { rowBooks ->
                var rowMax = 0f
                rowBooks.forEach { rowMax = max(rowMax, cardLayoutFor(it, m).height) }
                y += rowMax + m.rowGap
            }
            if (grouped) y += 26f // 章节底部留白
        }
        y += 210f // 底部结语与印章区域
        return max(y, 800f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: 1080
        setMeasuredDimension(width, calculateContentHeight(width.toFloat()).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawScrollContent(canvas, width.toFloat(), height.toFloat())
    }

    /**
     * 绘制整个藏书长卷内容（支持屏幕绘制与离屏超清导出）
     */
    fun drawScrollContent(canvas: Canvas, canvasWidth: Float, canvasHeight: Float) {
        val dark = isDarkMode

        // 1. 宣纸 / 和纸质感基底与典雅双重金线
        canvas.drawColor(if (dark) Color.parseColor("#151210") else Color.parseColor("#FAF8F3"))
        val goldColor = if (dark) Color.parseColor("#D4AF37") else Color.parseColor("#8C6D46")
        linePaint.style = Paint.Style.STROKE
        linePaint.color = goldColor
        linePaint.strokeWidth = 2f
        linePaint.alpha = if (dark) 80 else 60
        canvas.drawRect(24f, 24f, canvasWidth - 24f, canvasHeight - 24f, linePaint)
        linePaint.alpha = if (dark) 40 else 30
        canvas.drawRect(30f, 30f, canvasWidth - 30f, canvasHeight - 30f, linePaint)

        val primaryText = if (dark) Color.parseColor("#F5F0E6") else Color.parseColor("#2C241E")
        val secondaryText = if (dark) Color.parseColor("#A89F91") else Color.parseColor("#7A6E65")
        val accentGold = if (dark) Color.parseColor("#E6C265") else Color.parseColor("#996515")

        // 2. 头部题字与朱砂印章
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.textSize = canvasWidth * 0.052f
        textPaint.color = primaryText
        canvas.drawText("「 全 息 藏 书 长 卷 」", canvasWidth / 2f, 100f, textPaint)
        drawChinatownSeal(canvas, canvasWidth - 110f, 65f, "藏库", "典藏")

        textPaint.isFakeBoldText = false
        textPaint.textSize = canvasWidth * 0.030f
        textPaint.color = secondaryText
        val subtitle = "${filterSummary} · 共沉淀 ${bookList.size} 座精神坐标"
        val subtitleCs = TextUtils.ellipsize(subtitle, textPaint, canvasWidth - 180f, TextUtils.TruncateAt.END)
        canvas.drawText(subtitleCs.toString(), canvasWidth / 2f, 150f, textPaint)

        drawCapsuleBadge(canvas, canvasWidth / 2f, 205f, buildStatLine(), accentGold, dark, canvasWidth - 140f)

        // 3. 双列藏品卡片网格（跨媒介时按媒介分章）
        val m = gridMetricsFor(canvasWidth)
        var currentY = 270f
        val grouped = sections.size > 1

        sections.forEach { section ->
            if (grouped) {
                // 章节题头：金点节点 + 媒介名与数量
                linePaint.style = Paint.Style.FILL
                linePaint.color = accentGold
                canvas.drawCircle(70f, currentY, 6f, linePaint)
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = 2f
                canvas.drawCircle(70f, currentY, 10f, linePaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.isFakeBoldText = true
                textPaint.textSize = canvasWidth * 0.036f
                textPaint.color = accentGold
                canvas.drawText(
                    "${section.media.emoji} ${section.media.displayName} · ${section.books.size} ${unitFor(section.media)}",
                    92f, currentY + 8f, textPaint,
                )
                currentY += sectionHeaderHeight()
            }

            section.books.chunked(m.columns).forEach { rowBooks ->
                val layouts = rowBooks.map { cardLayoutFor(it, m) }
                val rowHeight = layouts.maxOf { it.height }
                rowBooks.forEachIndexed { col, book ->
                    val cardLeft = m.margin + col * (m.cardWidth + m.gap)
                    drawBookCard(
                        canvas, book, cardLeft, currentY, rowHeight, layouts[col],
                        m, primaryText, secondaryText, accentGold, dark,
                    )
                }
                currentY += rowHeight + m.rowGap
            }
            if (grouped) currentY += 26f
        }

        // 4. 底部结语与印章
        currentY += 24f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.textSize = canvasWidth * 0.034f
        textPaint.color = accentGold
        canvas.drawText("「 收 藏 即 是 热 爱 · 阅 痕 常 留 」", canvasWidth / 2f, currentY, textPaint)

        currentY += 38f
        textPaint.isFakeBoldText = false
        textPaint.textSize = canvasWidth * 0.025f
        textPaint.color = secondaryText
        val dateStr = SimpleDateFormat("yyyy' 年 'MM' 月 'dd' 日 · 生成于《阅痕 ReadTrace》'", Locale.CHINA).format(Date())
        canvas.drawText(dateStr, canvasWidth / 2f, currentY, textPaint)

        drawChinatownSeal(canvas, canvasWidth / 2f - 28f, currentY + 20f, "阅痕", "永驻")
    }

    // ---------------------------------------------------------------- 单卡与部件绘制

    private fun drawBookCard(
        canvas: Canvas,
        book: Book,
        left: Float,
        top: Float,
        cardHeight: Float,
        layout: CardLayout,
        m: GridMetrics,
        primaryText: Int,
        secondaryText: Int,
        accentGold: Int,
        dark: Boolean,
    ) {
        // 1. 卡片底色与微边框
        val cardRect = RectF(left, top, left + m.cardWidth, top + cardHeight)
        cardPaint.style = Paint.Style.FILL
        cardPaint.color = if (dark) Color.parseColor("#221D1A") else Color.parseColor("#FFFFFF")
        cardPaint.alpha = if (dark) 220 else 240
        canvas.drawRoundRect(cardRect, 14f, 14f, cardPaint)

        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = 1.2f
        cardPaint.color = if (dark) Color.parseColor("#3D342E") else Color.parseColor("#E8E2D9")
        canvas.drawRoundRect(cardRect, 14f, 14f, cardPaint)

        // 2. 左侧封面（圆角裁切 + 微金边）
        val coverRect = RectF(left + m.padX, top + m.padY, left + m.padX + layout.coverWidth, top + m.padY + layout.coverHeight)
        val bmp = book.coverUrl?.trim()?.let { coverBitmaps.get(it) }
        if (bmp != null && !bmp.isRecycled) {
            val roundPath = Path().apply {
                addRoundRect(coverRect, 8f, 8f, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(roundPath)
            canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), coverRect, imagePaint)
            canvas.restore()
        } else {
            // 优雅古风占位底色：居中媒介 Emoji 与藏品名前两字
            cardPaint.style = Paint.Style.FILL
            cardPaint.alpha = 255
            cardPaint.color = if (dark) Color.parseColor("#2A2420") else Color.parseColor("#EFECE6")
            canvas.drawRoundRect(coverRect, 8f, 8f, cardPaint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.isFakeBoldText = false
            textPaint.color = secondaryText
            textPaint.textSize = layout.coverWidth * 0.30f
            canvas.drawText(book.mediaType.emoji, coverRect.centerX(), coverRect.centerY() - 4f, textPaint)

            textPaint.textSize = layout.coverWidth * 0.17f
            canvas.drawText(book.title.take(2), coverRect.centerX(), coverRect.centerY() + layout.coverWidth * 0.26f, textPaint)
        }
        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = 1f
        cardPaint.color = if (dark) Color.parseColor("#443830") else Color.parseColor("#DDD4C7")
        canvas.drawRoundRect(coverRect, 8f, 8f, cardPaint)

        // 3. 右侧文本区
        val textLeft = left + m.padX + layout.coverWidth + 14f
        var textY = top + m.padY

        // 藏品名（StaticLayout 多行自动换行，最多 2 行）
        titlePaint.color = primaryText
        canvas.save()
        canvas.translate(textLeft, textY)
        layout.titleLayout.draw(canvas)
        canvas.restore()
        textY += layout.titleLayout.height + 8f

        // 评分（金）+ 状态（素）同行混排
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = m.metaSize
        var cursorX = textLeft
        val metaBaseline = textY + m.metaSize * 0.95f
        book.rating?.let { rating ->
            textPaint.color = accentGold
            textPaint.isFakeBoldText = true
            val ratingStr = "★ ${RATING_FORMAT.format(rating)}"
            canvas.drawText(ratingStr, cursorX, metaBaseline, textPaint)
            cursorX += textPaint.measureText(ratingStr) + m.metaSize * 0.5f
        }
        textPaint.isFakeBoldText = false
        textPaint.color = secondaryText
        val statusMaxW = (left + m.cardWidth - m.padX - cursorX).coerceAtLeast(0f)
        if (statusMaxW > 0f) {
            val statusCs = TextUtils.ellipsize(
                book.status.getDisplayName(book.mediaType),
                textPaint, statusMaxW, TextUtils.TruncateAt.END,
            )
            canvas.drawText(statusCs.toString(), cursorX, metaBaseline, textPaint)
        }
        textY += m.metaSize * 1.25f + 6f

        // 作者 / 分类（单行，超宽省略）
        textPaint.color = secondaryText
        val authorStr = book.author?.trim().orEmpty()
            .ifEmpty { book.category?.trim().orEmpty() }
            .ifEmpty { book.mediaType.displayName }
        val authorCs = TextUtils.ellipsize(authorStr, textPaint, m.textWidth, TextUtils.TruncateAt.END)
        canvas.drawText(authorCs.toString(), textLeft, textY + m.metaSize * 0.95f, textPaint)
    }

    /** 头部统计胶囊：单一媒介按状态细分，跨媒介按媒介分布统计 */
    private fun buildStatLine(): String {
        val rated = bookList.mapNotNull { it.rating }
        val avgSuffix = if (rated.size >= 3 && bookList.map { it.mediaType }.distinct().size <= 1) {
            " · 均分 ★ ${RATING_FORMAT.format(rated.sum() / rated.size)}"
        } else {
            ""
        }

        val distinctMedia = bookList.map { it.mediaType }.distinct()
        if (distinctMedia.size > 1) {
            val mediaParts = listOf(MediaType.BOOK, MediaType.ANIME, MediaType.MOVIE, MediaType.GAME, MediaType.MUSIC)
                .mapNotNull { media ->
                    bookList.count { it.mediaType == media }
                        .takeIf { it > 0 }
                        ?.let { "${media.emoji}$it" }
                }
            return "✨ 全量典藏 ${bookList.size} 部 · ${mediaParts.joinToString("  ")}"
        }

        val media = distinctMedia.firstOrNull() ?: MediaType.BOOK
        val counts = bookList.groupBy { it.status }
        val parts = mutableListOf<String>()
        fun addPart(status: BookStatus, emoji: String, label: String) {
            counts[status]?.size?.takeIf { it > 0 }?.let { parts += "$emoji $label $it" }
        }
        val finishedEmoji = when (media) {
            MediaType.BOOK -> "📚"
            MediaType.ANIME -> "🌸"
            MediaType.MOVIE -> "🎬"
            MediaType.GAME -> "🏆"
            MediaType.MUSIC -> "💿"
        }
        addPart(BookStatus.FINISHED, finishedEmoji, media.finishedLabel)
        addPart(BookStatus.READING, "📖", media.ongoingLabel)
        addPart(BookStatus.WISHLIST, "🌟", media.wishlistLabel)
        addPart(BookStatus.PAUSED, "⏸️", media.pausedLabel)
        addPart(BookStatus.DROPPED, "✖️", media.droppedLabel)

        if (parts.isEmpty()) return "🌱 新卷待启 · 静候藏品沉淀"
        return "${parts.joinToString(" · ")}$avgSuffix"
    }

    private fun unitFor(media: MediaType): String = when (media) {
        MediaType.BOOK -> "本藏书"
        MediaType.ANIME -> "部番剧"
        MediaType.MOVIE -> "部光影"
        MediaType.GAME -> "款神作"
        MediaType.MUSIC -> "首曲目"
    }

    private fun drawCapsuleBadge(canvas: Canvas, cx: Float, cy: Float, text: String, color: Int, dark: Boolean, maxWidth: Float) {
        var textSize = 28f
        textPaint.textSize = textSize
        textPaint.isFakeBoldText = false
        val naturalWidth = textPaint.measureText(text)
        if (naturalWidth > maxWidth) {
            textSize = (textSize * maxWidth / naturalWidth).coerceAtLeast(16f)
            textPaint.textSize = textSize
        }
        val textWidth = textPaint.measureText(text)
        val rect = RectF(cx - textWidth / 2f - 24f, cy - 20f, cx + textWidth / 2f + 24f, cy + 20f)

        cardPaint.style = Paint.Style.FILL
        cardPaint.color = color
        cardPaint.alpha = if (dark) 35 else 25
        canvas.drawRoundRect(rect, 20f, 20f, cardPaint)

        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = 1.5f
        cardPaint.alpha = if (dark) 120 else 90
        canvas.drawRoundRect(rect, 20f, 20f, cardPaint)

        textPaint.color = color
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, cy + 9f, textPaint)
    }

    /**
     * 绘制古典朱砂方形印章
     */
    private fun drawChinatownSeal(canvas: Canvas, x: Float, y: Float, line1: String, line2: String) {
        val size = 56f
        val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        sealPaint.style = Paint.Style.STROKE
        sealPaint.strokeWidth = 2.5f
        sealPaint.color = Color.parseColor("#C62828")
        val rect = RectF(x, y, x + size, y + size)
        canvas.drawRoundRect(rect, 8f, 8f, sealPaint)

        sealPaint.style = Paint.Style.FILL
        sealPaint.textSize = 19f
        sealPaint.isFakeBoldText = true
        sealPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(line1, x + size / 2f, y + 24f, sealPaint)
        canvas.drawText(line2, x + size / 2f, y + 46f, sealPaint)
    }

    companion object {
        private val RATING_FORMAT = DecimalFormat("0.#")
    }
}
