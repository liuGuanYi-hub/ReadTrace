package com.example.readtrace.widget

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import com.example.readtrace.util.HapticFeedbackEngine
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

open class MediaTimelineScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var isDarkMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var currentMediaType: MediaType? = null
        protected set
    var bookList: List<Book> = emptyList()
        protected set
    var groupedByYear: Map<String, List<Book>> = emptyMap()
        protected set

    /** 封面位图内存缓存：按字节计量的 LRU 上限（约堆 1/8），防止长时间浏览无限累积导致 OOM */
    private val coverBitmaps = object : android.util.LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
    }

    override fun onDetachedFromWindow() {
        // 脱离窗口时安全清空缓存（位图可能仍被 CoverImageHelper 共享持有，只逐出不 recycle）
        coverBitmaps.evictAll()
        super.onDetachedFromWindow()
    }

    /** 点击与长按监听器 */
    var onBookClickListener: ((Book) -> Unit)? = null
    var onBookLongClickListener: ((Book) -> Unit)? = null

    /** 点击检测热区记录 (RectF to Book) */
    private val cardHitboxes = mutableListOf<Pair<RectF, Book>>()
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private val touchSlop = 20f

    // 绘制画笔
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    companion object {
        /** 标题字号相对画布宽度的比例 */
        private const val TITLE_TEXT_RATIO = 0.030f

        /** meta / 短评字号相对画布宽度的比例 */
        private const val BODY_TEXT_RATIO = 0.022f

        /** 封面宽度相对卡片宽度的比例（约 18% ~ 22%） */
        private const val COVER_WIDTH_RATIO = 0.18f

        /** 封面标准 2:3 高宽比 */
        private const val COVER_ASPECT_RATIO = 1.48f

        /** 卡片内边距 */
        private const val CARD_PAD_X = 14f
        private const val CARD_PAD_Y = 14f

        /** 封面与右侧文本的间距 */
        private const val COVER_TEXT_GAP = 16f

        /** 卡片之间的垂直间距 */
        private const val CARD_GAP = 14f

        /** 标题最多显示的行数，超出截断加省略号 */
        private const val TITLE_MAX_LINES = 2

        /** 短评最多显示的行数，超出截断加省略号 */
        private const val QUOTE_MAX_LINES = 2

        /** StaticLayout 行间距附加值（标题/meta/短评） */
        private const val TITLE_LINE_SPACING = 3f
        private const val BODY_LINE_SPACING = 2f
    }

    private val yearRegex = Regex("""\b(19\d{2}|20\d{2}|21\d{2})\b""")
    private val yearWithSuffixRegex = Regex("""(19\d{2}|20\d{2}|21\d{2})年""")

    /**
     * 设置通用编年长卷数据
     * @param list 作品列表
     * @param mediaType 目标媒介类型，null 表示全媒介综合长卷
     */
    open fun setTimelineData(list: List<Book>, mediaType: MediaType? = null) {
        this.currentMediaType = mediaType
        this.bookList = if (mediaType != null) {
            list.filter { it.mediaType == mediaType }
        } else {
            list
        }

        // 异步预加载所有封面图片
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

        val groups = LinkedHashMap<String, MutableList<Book>>()

        val nonWishlist = bookList.filter { it.status != BookStatus.WISHLIST }
        val wishlist = bookList.filter { it.status == BookStatus.WISHLIST }

        // 年份分组映射 (按 Int 升序自动排序，不设 1995-2024 限制，早期与未来年份作品均可自然纳入)
        val yearGroups = java.util.TreeMap<Int, MutableList<Book>>()
        val noYearList = mutableListOf<Book>()

        nonWishlist.forEach { book ->
            val year = extractYearInt(book)
            if (year != null) {
                yearGroups.getOrPut(year) { mutableListOf() }.add(book)
            } else {
                noYearList.add(book)
            }
        }

        // 1. 年份编年组（按年份升序严格排列）
        yearGroups.forEach { (year, books) ->
            groups["$year 年"] = books
        }

        // 2. 无明确年份的已读/进行中作品
        if (noYearList.isNotEmpty()) {
            val noYearLabel = when (mediaType) {
                MediaType.BOOK -> "经典藏书 · 岁月流金"
                MediaType.MOVIE -> "经典光影 · 岁月流金"
                MediaType.GAME -> "经典神作 · 岁月流金"
                MediaType.MUSIC -> "经典黑胶 · 岁月流金"
                MediaType.ANIME -> "经典追番 · 岁月流金"
                null -> "经典收录 · 岁月流金"
            }
            groups[noYearLabel] = noYearList
        }

        // 3. 愿望单待看/待读作品
        if (wishlist.isNotEmpty()) {
            val wishlistLabel = when (mediaType) {
                MediaType.BOOK -> "待读清单 · 想读"
                MediaType.MOVIE -> "待看清单 · 想看"
                MediaType.GAME -> "愿望清单 · 想玩"
                MediaType.MUSIC -> "待听清单 · 想听"
                MediaType.ANIME -> "待看清单 · 想追"
                null -> "精神愿望清单"
            }
            groups[wishlistLabel] = wishlist.toMutableList()
        }

        this.groupedByYear = groups
        requestLayout()
        invalidate()
    }

    /** 向后兼容的番剧数据设置方法 */
    open fun setAnimeData(list: List<Book>) {
        setTimelineData(list, MediaType.ANIME)
    }

    fun extractYearInt(book: Book): Int? {
        // 1. 从 tags 中查找，如 "1988年", "1979", "2026年"
        for (tag in book.tags) {
            val matchSuffix = yearWithSuffixRegex.find(tag)
            if (matchSuffix != null) return matchSuffix.groupValues[1].toIntOrNull()
            val match = yearRegex.find(tag)
            if (match != null) return match.groupValues[1].toIntOrNull()
        }
        // 2. 从 startDate (如 "1984-03-11" 或 "2025")
        val startYear = yearRegex.find(book.startDate.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        if (startYear != null) return startYear

        // 3. 从 finishDate (如 "2026-05-01")
        val finishYear = yearRegex.find(book.finishDate.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        if (finishYear != null) return finishYear

        // 4. 从 category (如 "1980年代机甲", "90年代经典")
        val catYear = yearRegex.find(book.category.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        if (catYear != null) return catYear

        // 5. 从 title (如 "1984", "2001太空漫游")
        val titleYear = yearRegex.find(book.title)?.groupValues?.get(1)?.toIntOrNull()
        if (titleYear != null) return titleYear

        return null
    }

    private fun getCoverDimensions(cardWidth: Float): Pair<Float, Float> {
        val coverW = (cardWidth * COVER_WIDTH_RATIO).coerceIn(60f, 160f)
        val coverH = coverW * COVER_ASPECT_RATIO
        return coverW to coverH
    }

    private fun rightContentMaxWidth(canvasWidth: Float): Float {
        val axisX = 90f
        val cardLeft = axisX + 24f
        val cardRight = canvasWidth - 45f
        val cardWidth = cardRight - cardLeft
        val (coverW, _) = getCoverDimensions(cardWidth)
        return (cardWidth - CARD_PAD_X * 2f - coverW - COVER_TEXT_GAP).coerceAtLeast(80f)
    }

    /** 标题在卡片内的最大可绘宽度（右侧预留评分标签区域） */
    private fun titleMaxWidth(canvasWidth: Float): Float {
        val contentW = rightContentMaxWidth(canvasWidth)
        return (contentW - 90f).coerceAtLeast(60f)
    }

    /** meta 行最大可绘宽度 */
    private fun metaMaxWidth(canvasWidth: Float): Float = rightContentMaxWidth(canvasWidth)

    /** 短评金句在卡片内的最大可绘宽度 */
    private fun quoteMaxWidth(canvasWidth: Float): Float = rightContentMaxWidth(canvasWidth)

    /**
     * 为某部作品构建短评金句的多行 [StaticLayout]。
     */
    private fun quoteLayoutFor(book: Book, canvasWidth: Float): StaticLayout? {
        val comment = book.shortComment?.trim()
        if (comment.isNullOrEmpty()) return null
        val quote = if (comment.startsWith("“")) comment else "“$comment”"

        quotePaint.textSize = canvasWidth * BODY_TEXT_RATIO
        val maxWidth = quoteMaxWidth(canvasWidth).toInt().coerceAtLeast(1)

        fun build(text: String): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, quotePaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(BODY_LINE_SPACING, 1f)
                .build()

        val layout = build(quote)
        if (layout.lineCount <= QUOTE_MAX_LINES) return layout

        val end = layout.getLineEnd(QUOTE_MAX_LINES - 1).coerceAtMost(quote.length)
        val truncated = quote.substring(0, end).trimEnd().trimEnd('”') + "…”"
        return build(truncated)
    }

    /**
     * 为某部作品构建标题的多行 [StaticLayout]（加粗）。
     */
    private fun titleLayoutFor(book: Book, canvasWidth: Float): StaticLayout {
        titlePaint.textSize = canvasWidth * TITLE_TEXT_RATIO
        titlePaint.isFakeBoldText = true
        val maxWidth = titleMaxWidth(canvasWidth).toInt().coerceAtLeast(1)

        val displayTitle = if (currentMediaType == null) {
            "${book.mediaType.emoji} ${book.title}"
        } else {
            book.title
        }

        fun build(text: String): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, titlePaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(TITLE_LINE_SPACING, 1f)
                .build()

        val layout = build(displayTitle)
        if (layout.lineCount <= TITLE_MAX_LINES) return layout

        val end = layout.getLineEnd(TITLE_MAX_LINES - 1).coerceAtMost(displayTitle.length)
        val truncated = displayTitle.substring(0, end).trimEnd() + "…"
        return build(truncated)
    }

    /**
     * 为某部作品构建 meta 行（分类 · 作者）的单行 [StaticLayout]。
     */
    private fun metaLayoutFor(book: Book, canvasWidth: Float): StaticLayout {
        metaPaint.textSize = canvasWidth * BODY_TEXT_RATIO
        val categoryStr = book.category?.trim().orEmpty()
        val authorStr = book.author?.trim().orEmpty()

        val text = when {
            categoryStr.isNotEmpty() && authorStr.isNotEmpty() -> "$categoryStr · $authorStr"
            categoryStr.isNotEmpty() -> categoryStr
            authorStr.isNotEmpty() -> authorStr
            else -> book.mediaType.displayName
        }

        val maxWidth = metaMaxWidth(canvasWidth).toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(text, 0, text.length, metaPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(BODY_LINE_SPACING, 1f)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(1)
            .build()
    }

    private class CardLayout(
        val titleLayout: StaticLayout,
        val metaLayout: StaticLayout,
        val quoteLayout: StaticLayout?,
        val coverWidth: Float,
        val coverHeight: Float,
        val height: Float,
    )

    private fun cardLayoutFor(book: Book, canvasWidth: Float): CardLayout {
        val axisX = 90f
        val cardLeft = axisX + 24f
        val cardRight = canvasWidth - 45f
        val cardWidth = cardRight - cardLeft
        val (coverW, coverH) = getCoverDimensions(cardWidth)

        val titleLayout = titleLayoutFor(book, canvasWidth)
        val metaLayout = metaLayoutFor(book, canvasWidth)
        val quoteLayout = quoteLayoutFor(book, canvasWidth)

        var textContentH = titleLayout.height + 4f + metaLayout.height
        if (quoteLayout != null) {
            textContentH += 6f + quoteLayout.height
        }

        val height = max(coverH, textContentH) + CARD_PAD_Y * 2f
        return CardLayout(titleLayout, metaLayout, quoteLayout, coverW, coverH, height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: 1080
        val calculatedHeight = calculateContentHeight(width.toFloat())
        setMeasuredDimension(width, calculatedHeight.toInt())
    }

    private fun calculateContentHeight(canvasWidth: Float): Float {
        var y = 280f // 头部标题与印章区域
        groupedByYear.forEach { (_, books) ->
            y += 70f // 年份标题与轴节点
            books.forEach { book ->
                y += cardLayoutFor(book, canvasWidth).height + CARD_GAP
            }
            y += 30f // 年份组底部留白
        }
        y += 220f // 底部结语与印章区域
        return max(y, 800f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawScrollContent(canvas, width.toFloat(), height.toFloat())
    }

    /**
     * 绘制整个画卷内容（支持屏幕绘制与离屏超清导出）
     */
    fun drawScrollContent(canvas: Canvas, canvasWidth: Float, canvasHeight: Float) {
        val dark = isDarkMode
        val media = currentMediaType

        // 1. 绘制宣纸 / 和纸质感基底
        val bgColor = if (dark) Color.parseColor("#151210") else Color.parseColor("#FAF8F3")
        canvas.drawColor(bgColor)

        // 绘制宣纸微边框与典雅双重金线
        val goldColor = if (dark) Color.parseColor("#D4AF37") else Color.parseColor("#8C6D46")
        linePaint.style = Paint.Style.STROKE
        linePaint.color = goldColor
        linePaint.strokeWidth = 2f
        linePaint.alpha = if (dark) 80 else 60
        canvas.drawRect(24f, 24f, canvasWidth - 24f, canvasHeight - 24f, linePaint)
        linePaint.alpha = if (dark) 40 else 30
        canvas.drawRect(30f, 30f, canvasWidth - 30f, canvasHeight - 30f, linePaint)

        // 2. 头部：题字与印章配置
        val primaryText = if (dark) Color.parseColor("#F5F0E6") else Color.parseColor("#2C241E")
        val secondaryText = if (dark) Color.parseColor("#A89F91") else Color.parseColor("#7A6E65")
        val accentGold = if (dark) Color.parseColor("#E6C265") else Color.parseColor("#996515")
        val highlightColor = when (media) {
            MediaType.BOOK -> Color.parseColor("#5C6BC0")
            MediaType.ANIME -> Color.parseColor("#E57373")
            MediaType.MOVIE -> Color.parseColor("#FFB74D")
            MediaType.GAME -> Color.parseColor("#4DB6AC")
            MediaType.MUSIC -> Color.parseColor("#BA68C8")
            null -> Color.parseColor("#E57373")
        }

        val (mainTitle, sealLine1, sealLine2, bottomSlogan, bottomSeal1, bottomSeal2) = when (media) {
            MediaType.BOOK -> HexaTheme(
                "「 阅 读 编 年 史 · 阅 历 画 卷 」",
                "书香", "墨痕",
                "「 读 万 卷 书 · 行 万 里 路 」",
                "开卷", "有益",
            )
            MediaType.MOVIE -> HexaTheme(
                "「 光 影 编 年 史 · 影 画 长 卷 」",
                "光影", "留痕",
                "「 光 影 斑 驳 · 岁 月 留 痕 」",
                "银幕", "永恒",
            )
            MediaType.GAME -> HexaTheme(
                "「 游 戏 编 年 史 · 征 程 画 卷 」",
                "第九", "艺术",
                "「 虚 拟 征 程 · 真 实 感 动 」",
                "通关", "留念",
            )
            MediaType.MUSIC -> HexaTheme(
                "「 乐 音 编 年 史 · 旋 律 画 卷 」",
                "天籁", "心音",
                "「 旋 律 悠 扬 · 声 音 永 存 」",
                "黑胶", "留声",
            )
            MediaType.ANIME -> HexaTheme(
                "「 追 番 编 年 史 · 心 智 画 卷 」",
                "追番", "印记",
                "「 人 生 如 旅 · 阅 痕 常 留 」",
                "阅痕", "永驻",
            )
            null -> HexaTheme(
                "「 全 景 编 年 史 · 阅 痕 长 卷 」",
                "阅痕", "全景",
                "「 人 生 如 旅 · 阅 痕 常 留 」",
                "阅痕", "永驻",
            )
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.textSize = canvasWidth * 0.052f
        textPaint.color = primaryText
        canvas.drawText(mainTitle, canvasWidth / 2f, 100f, textPaint)

        // 红色朱砂印章
        drawChinatownSeal(canvas, canvasWidth - 110f, 65f, sealLine1, sealLine2)

        // 动态计算年份跨度与副标题
        val allYears = bookList.mapNotNull { extractYearInt(it) }
        val minYear = allYears.minOrNull()
        val maxYear = allYears.maxOrNull()

        val unitName = when (media) {
            MediaType.BOOK -> "本藏书"
            MediaType.MOVIE -> "部光影"
            MediaType.GAME -> "款神作"
            MediaType.MUSIC -> "首曲目"
            MediaType.ANIME -> "部番剧"
            null -> "座精神坐标"
        }

        val spanSubtitle = if (minYear != null && maxYear != null) {
            if (minYear == maxYear) {
                "$minYear 年 · 沉淀 ${bookList.size} $unitName"
            } else {
                val span = maxYear - minYear + 1
                "$minYear ~ $maxYear · 历经 $span 载光阴 · 沉淀 ${bookList.size} $unitName"
            }
        } else {
            "全景时光 · 沉淀 ${bookList.size} $unitName"
        }

        textPaint.isFakeBoldText = false
        textPaint.textSize = canvasWidth * 0.030f
        textPaint.color = secondaryText
        canvas.drawText(spanSubtitle, canvasWidth / 2f, 150f, textPaint)

        // 统计胶囊条
        val finishedCount = bookList.count { it.status == BookStatus.FINISHED }
        val readingCount = bookList.count { it.status == BookStatus.READING }
        val wishlistCount = bookList.count { it.status == BookStatus.WISHLIST }

        val statText = when (media) {
            MediaType.BOOK -> "📚 读完 $finishedCount 本   ·   📖 在读 $readingCount 本   ·   🌟 想读 $wishlistCount 本"
            MediaType.MOVIE -> "🎬 已看 $finishedCount 部   ·   🍿 在看 $readingCount 部   ·   🌟 想看 $wishlistCount 部"
            MediaType.GAME -> "🎮 白金 $finishedCount 款   ·   🕹️ 游玩 $readingCount 款   ·   🌟 想玩 $wishlistCount 款"
            MediaType.MUSIC -> "💿 听完 $finishedCount 首   ·   🎧 在听 $readingCount 首   ·   🌟 想听 $wishlistCount 首"
            MediaType.ANIME -> if (readingCount > 0) {
                "🌸 补完 $finishedCount 部   ·   📖 在追 $readingCount 部   ·   🌟 想追 $wishlistCount 部"
            } else {
                "🌸 补完 $finishedCount 部   ·   🌟 想追 $wishlistCount 部   ·   💮 精神印记共鸣"
            }
            null -> {
                val bCount = bookList.count { it.mediaType == MediaType.BOOK }
                val aCount = bookList.count { it.mediaType == MediaType.ANIME }
                val mCount = bookList.count { it.mediaType == MediaType.MOVIE }
                val gCount = bookList.count { it.mediaType == MediaType.GAME }
                "✨ 全量典藏 ${bookList.size} 部   ·   📚 $bCount   🌸 $aCount   🎬 $mCount   🎮 $gCount"
            }
        }
        drawCapsuleBadge(canvas, canvasWidth / 2f, 205f, statText, accentGold, dark)

        // 3. 中轴时光线与各年份作品卡片
        val axisX = 90f
        var currentY = 270f
        cardHitboxes.clear()

        groupedByYear.forEach { (year, books) ->
            // 年份标题与发光时间节点
            linePaint.style = Paint.Style.FILL
            linePaint.color = accentGold
            canvas.drawCircle(axisX, currentY, 7f, linePaint)
            linePaint.style = Paint.Style.STROKE
            linePaint.strokeWidth = 2f
            canvas.drawCircle(axisX, currentY, 12f, linePaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.isFakeBoldText = true
            textPaint.textSize = canvasWidth * 0.038f
            textPaint.color = accentGold
            canvas.drawText(year, axisX + 26f, currentY + 7f, textPaint)

            currentY += 45f

            // 绘制该年份下的卡片
            books.forEach { book ->
                val cardLayout = cardLayoutFor(book, canvasWidth)
                val cardLeft = axisX + 24f
                val cardRight = canvasWidth - 45f
                val cardTop = currentY
                val cardHeight = cardLayout.height
                val cardBottom = cardTop + cardHeight

                // 记录点击热区
                cardHitboxes.add(RectF(cardLeft, cardTop, cardRight, cardBottom) to book)

                // 1. 卡片底色
                cardPaint.style = Paint.Style.FILL
                cardPaint.color = if (dark) Color.parseColor("#221D1A") else Color.parseColor("#FFFFFF")
                cardPaint.alpha = if (dark) 220 else 240
                canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 14f, 14f, cardPaint)

                // 2. 卡片微边框
                cardPaint.style = Paint.Style.STROKE
                cardPaint.strokeWidth = 1.2f
                cardPaint.color = if (dark) Color.parseColor("#3D342E") else Color.parseColor("#E8E2D9")
                canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 14f, 14f, cardPaint)

                // 3. 左侧封面绘制
                val coverLeft = cardLeft + CARD_PAD_X
                val coverTop = cardTop + CARD_PAD_Y
                val coverRight = coverLeft + cardLayout.coverWidth
                val coverBottom = coverTop + cardLayout.coverHeight
                val coverRect = RectF(coverLeft, coverTop, coverRight, coverBottom)

                val bmp = book.coverUrl?.trim()?.let { coverBitmaps.get(it) }
                if (bmp != null && !bmp.isRecycled) {
                    val roundPath = Path().apply {
                        addRoundRect(coverRect, 8f, 8f, Path.Direction.CW)
                    }
                    canvas.save()
                    canvas.clipPath(roundPath)
                    val srcRect = Rect(0, 0, bmp.width, bmp.height)
                    canvas.drawBitmap(bmp, srcRect, coverRect, imagePaint)
                    canvas.restore()

                    // 封面微金边
                    cardPaint.style = Paint.Style.STROKE
                    cardPaint.strokeWidth = 1f
                    cardPaint.color = if (dark) Color.parseColor("#443830") else Color.parseColor("#DDD4C7")
                    canvas.drawRoundRect(coverRect, 8f, 8f, cardPaint)
                } else {
                    // 优雅古风占位底色
                    cardPaint.style = Paint.Style.FILL
                    cardPaint.color = if (dark) Color.parseColor("#2A2420") else Color.parseColor("#EFECE6")
                    canvas.drawRoundRect(coverRect, 8f, 8f, cardPaint)

                    cardPaint.style = Paint.Style.STROKE
                    cardPaint.strokeWidth = 1f
                    cardPaint.color = if (dark) Color.parseColor("#443830") else Color.parseColor("#DDD4C7")
                    canvas.drawRoundRect(coverRect, 8f, 8f, cardPaint)

                    // 居中绘制媒介 Emoji 与前两个字
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.isFakeBoldText = false
                    textPaint.textSize = cardLayout.coverWidth * 0.34f
                    canvas.drawText(book.mediaType.emoji, coverRect.centerX(), coverRect.centerY() - 4f, textPaint)

                    textPaint.textSize = cardLayout.coverWidth * 0.20f
                    textPaint.color = secondaryText
                    val shortTitle = book.title.take(2)
                    canvas.drawText(shortTitle, coverRect.centerX(), coverRect.centerY() + cardLayout.coverWidth * 0.28f, textPaint)
                }

                // 4. 右侧内容区绘制
                val textLeft = coverRight + COVER_TEXT_GAP
                var textY = cardTop + CARD_PAD_Y

                // 标题（StaticLayout 多行自动换行，最多 2 行）
                titlePaint.color = primaryText
                canvas.save()
                canvas.translate(textLeft, textY)
                cardLayout.titleLayout.draw(canvas)
                canvas.restore()

                textY += cardLayout.titleLayout.height + 4f

                // 状态标签或评分小徽章（位于标题行右侧顶端）
                val (badgeText, badgeColor) = when (book.status) {
                    BookStatus.FINISHED -> {
                        val ratingStr = book.rating?.let { "★ ${it / 2.0}" } ?: when (book.mediaType) {
                            MediaType.BOOK -> "📚 已读"
                            MediaType.MOVIE -> "🎬 已看"
                            MediaType.GAME -> "🏆 白金"
                            MediaType.ANIME -> "🌸 补完"
                            MediaType.MUSIC -> "💿 听完"
                        }
                        ratingStr to highlightColor
                    }
                    BookStatus.READING -> {
                        val ongoingText = when (book.mediaType) {
                            MediaType.BOOK -> "📖 在读"
                            MediaType.MOVIE -> "🍿 在看"
                            MediaType.GAME -> "🕹️ 游玩中"
                            MediaType.ANIME -> "📖 追番中"
                            MediaType.MUSIC -> "🎧 在听"
                        }
                        ongoingText to (if (dark) Color.parseColor("#81C784") else Color.parseColor("#3A6348"))
                    }
                    BookStatus.WISHLIST -> {
                        val wishText = when (book.mediaType) {
                            MediaType.BOOK -> "🌟 想读"
                            MediaType.MOVIE -> "🌟 想看"
                            MediaType.GAME -> "🌟 想玩"
                            MediaType.ANIME -> "🌟 想追"
                            MediaType.MUSIC -> "🌟 想听"
                        }
                        wishText to accentGold
                    }
                    BookStatus.PAUSED -> "⏸️ 搁置" to secondaryText
                    BookStatus.DROPPED -> "✖️ 弃置" to secondaryText
                }
                drawSmallTag(canvas, cardRight - 55f, cardTop + CARD_PAD_Y + 12f, badgeText, badgeColor, dark)

                // 作者与分类（单行 StaticLayout，超宽自动省略号）
                metaPaint.color = secondaryText
                canvas.save()
                canvas.translate(textLeft, textY)
                cardLayout.metaLayout.draw(canvas)
                canvas.restore()

                textY += cardLayout.metaLayout.height + 6f

                // 短评金句（StaticLayout 多行自动换行，最多 2 行）
                cardLayout.quoteLayout?.let { layout ->
                    quotePaint.color = if (dark) Color.parseColor("#C8B8A6") else Color.parseColor("#5A4E45")
                    canvas.save()
                    canvas.translate(textLeft, textY)
                    layout.draw(canvas)
                    canvas.restore()
                }

                currentY += cardHeight + CARD_GAP
            }
            currentY += 25f
        }

        // 4. 绘制时光轴竖线贯通
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 2.5f
        linePaint.color = accentGold
        linePaint.alpha = if (dark) 90 else 70
        canvas.drawLine(axisX, 260f, axisX, currentY - 30f, linePaint)

        // 5. 底部印章与结语
        currentY += 30f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.textSize = canvasWidth * 0.034f
        textPaint.color = accentGold
        canvas.drawText(bottomSlogan, canvasWidth / 2f, currentY, textPaint)

        currentY += 35f
        textPaint.isFakeBoldText = false
        textPaint.textSize = canvasWidth * 0.025f
        textPaint.color = secondaryText
        val dateStr = SimpleDateFormat("yyyy' 年 'MM' 月 'dd' 日 · 生成于《阅痕 ReadTrace》'", Locale.CHINA).format(Date())
        canvas.drawText(dateStr, canvasWidth / 2f, currentY, textPaint)

        // 底部居中印章
        drawChinatownSeal(canvas, canvasWidth / 2f - 28f, currentY + 20f, bottomSeal1, bottomSeal2)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = Math.abs(event.x - downX)
                val dy = Math.abs(event.y - downY)
                val dt = System.currentTimeMillis() - downTime
                if (dx < touchSlop && dy < touchSlop && dt < 450) {
                    val clicked = cardHitboxes.firstOrNull { it.first.contains(event.x, event.y) }
                    if (clicked != null) {
                        performClick()
                        HapticFeedbackEngine.lightClick(context)
                        onBookClickListener?.invoke(clicked.second)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private data class HexaTheme(
        val mainTitle: String,
        val topSeal1: String,
        val topSeal2: String,
        val bottomSlogan: String,
        val bottomSeal1: String,
        val bottomSeal2: String,
    )

    private fun drawCapsuleBadge(canvas: Canvas, cx: Float, cy: Float, text: String, color: Int, dark: Boolean) {
        textPaint.textSize = 28f
        textPaint.isFakeBoldText = false
        val textWidth = textPaint.measureText(text)
        val rect = RectF(cx - textWidth / 2f - 24f, cy - 20f, cx + textWidth / 2f + 24f, cy + 20f)

        badgePaint.style = Paint.Style.FILL
        badgePaint.color = color
        badgePaint.alpha = if (dark) 35 else 25
        canvas.drawRoundRect(rect, 20f, 20f, badgePaint)

        badgePaint.style = Paint.Style.STROKE
        badgePaint.strokeWidth = 1.5f
        badgePaint.alpha = if (dark) 120 else 90
        canvas.drawRoundRect(rect, 20f, 20f, badgePaint)

        textPaint.color = color
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, cx, cy + 9f, textPaint)
    }

    private fun drawSmallTag(canvas: Canvas, x: Float, y: Float, text: String, color: Int, dark: Boolean) {
        textPaint.textSize = 22f
        textPaint.isFakeBoldText = true
        val textWidth = textPaint.measureText(text)
        val rect = RectF(x - textWidth / 2f - 12f, y - 14f, x + textWidth / 2f + 12f, y + 14f)

        badgePaint.style = Paint.Style.FILL
        badgePaint.color = color
        badgePaint.alpha = if (dark) 45 else 30
        canvas.drawRoundRect(rect, 10f, 10f, badgePaint)

        badgePaint.style = Paint.Style.STROKE
        badgePaint.strokeWidth = 1f
        badgePaint.alpha = if (dark) 160 else 120
        canvas.drawRoundRect(rect, 10f, 10f, badgePaint)

        textPaint.color = color
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, x, y + 7f, textPaint)
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

    /**
     * 离屏生成 1080P 超清宣纸画卷长图 Bitmap
     */
    fun exportUltraHdBitmap(): Bitmap {
        val targetWidth = 1080
        val targetHeight = calculateContentHeight(targetWidth.toFloat()).toInt()
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawScrollContent(canvas, targetWidth.toFloat(), targetHeight.toFloat())
        return bitmap
    }
}
