package com.example.readtrace.widget

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class AnimeTimelineScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var isDarkMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var animeList: List<Book> = emptyList()
    private var groupedByYear: Map<String, List<Book>> = emptyMap()

    // 绘制画笔
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    companion object {
        /** 无短评时卡片的基础高度 */
        private const val BASE_CARD_HEIGHT = 112f

        /** 短评区顶部相对卡片顶部的偏移 */
        private const val QUOTE_TOP_OFFSET = 88f

        /** 短评最多显示的行数，超出截断加省略号 */
        private const val QUOTE_MAX_LINES = 3

        /** 卡片之间的垂直间距 */
        private const val CARD_GAP = 12f

        /** 标题区顶部相对卡片顶部的偏移（StaticLayout 绘制原点） */
        private const val TITLE_TOP_OFFSET = 6f

        /** 标题最多显示的行数，超出截断加省略号 */
        private const val TITLE_MAX_LINES = 2

        /** 标题 StaticLayout 的行间距附加值 */
        private const val TITLE_LINE_SPACING = 5f
    }

    fun setAnimeData(list: List<Book>) {
        this.animeList = list.filter { it.mediaType == MediaType.ANIME }
        
        // 按照年份或状态进行结构化分组
        val groups = LinkedHashMap<String, MutableList<Book>>()
        
        // 1. 已补完作品按年份提取
        animeList.filter { it.status == BookStatus.FINISHED }.forEach { book ->
            val year = extractYearFromBook(book)
            groups.getOrPut(year) { mutableListOf() }.add(book)
        }
        
        // 2. 想追待看作品
        val wishlist = animeList.filter { it.status == BookStatus.WISHLIST }
        if (wishlist.isNotEmpty()) {
            groups["待看清单 · 想追"] = wishlist.toMutableList()
        }

        this.groupedByYear = groups
        requestLayout()
        invalidate()
    }

    private fun extractYearFromBook(book: Book): String {
        // 从 tags 中查找带有 "年" 的标签，如 "1995年"
        val tagYear = book.tags.firstOrNull { it.contains("年") }
        if (tagYear != null) return tagYear

        // 从 startDate 中提取
        val date = book.startDate.orEmpty()
        if (date.length >= 4 && date.substring(0, 4).toIntOrNull() != null) {
            return "${date.substring(0, 4)} 年"
        }
        return "经典追番"
    }

    /** 短评金句在卡片内的最大可绘宽度（右侧预留评分标签区域） */
    private fun quoteMaxWidth(canvasWidth: Float): Float =
        (canvasWidth - 45f) - (90f + 24f) - 18f - 130f

    /** 番剧标题在卡片内的最大可绘宽度（右侧同样预留评分标签区域） */
    private fun titleMaxWidth(canvasWidth: Float): Float = quoteMaxWidth(canvasWidth)

    /** meta 行最大可绘宽度（与评分标签不同水平区，仅减去左右内边距） */
    private fun metaMaxWidth(canvasWidth: Float): Float =
        (canvasWidth - 45f) - (90f + 24f) - 18f - 18f

    /**
     * 为某部番剧构建短评金句的多行 [StaticLayout]。
     * 超过 [QUOTE_MAX_LINES] 行时截断并以省略号结尾，避免卡片被超长短评撑高。
     */
    private fun quoteLayoutFor(book: Book, canvasWidth: Float): StaticLayout? {
        val comment = book.shortComment?.trim()
        if (comment.isNullOrEmpty()) return null
        val quote = "“$comment”"

        quotePaint.textSize = canvasWidth * 0.024f
        val maxWidth = quoteMaxWidth(canvasWidth).toInt().coerceAtLeast(1)

        fun build(text: String): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, quotePaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1f)
                .build()

        val layout = build(quote)
        if (layout.lineCount <= QUOTE_MAX_LINES) return layout

        // 超出最大行数：截取前 QUOTE_MAX_LINES 行末尾并追加省略号
        val end = layout.getLineEnd(QUOTE_MAX_LINES - 1).coerceAtMost(quote.length)
        val truncated = quote.substring(0, end).trimEnd().trimEnd('”') + "…”"
        return build(truncated)
    }

    /**
     * 为某部番剧构建标题的多行 [StaticLayout]（加粗）。
     * 超过 [TITLE_MAX_LINES] 行时截断并以省略号结尾。
     */
    private fun titleLayoutFor(book: Book, canvasWidth: Float): StaticLayout {
        titlePaint.textSize = canvasWidth * 0.032f
        titlePaint.isFakeBoldText = true
        val maxWidth = titleMaxWidth(canvasWidth).toInt().coerceAtLeast(1)

        fun build(text: String): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, titlePaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(TITLE_LINE_SPACING, 1f)
                .build()

        val layout = build(book.title)
        if (layout.lineCount <= TITLE_MAX_LINES) return layout

        val end = layout.getLineEnd(TITLE_MAX_LINES - 1).coerceAtMost(book.title.length)
        val truncated = book.title.substring(0, end).trimEnd() + "…"
        return build(truncated)
    }

    /**
     * 为某部番剧构建 meta 行（分类 · 作者）的单行 [StaticLayout]。
     * 超宽自动末尾省略号（[TextUtils.TruncateAt.END]）——meta 是次要信息，
     * 单行截断而非换行，避免撑高卡片。
     */
    private fun metaLayoutFor(book: Book, canvasWidth: Float): StaticLayout {
        metaPaint.textSize = canvasWidth * 0.024f
        val text = "${book.category.orEmpty()} · ${book.author.orEmpty()}"
        val maxWidth = metaMaxWidth(canvasWidth).toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(text, 0, text.length, metaPaint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1f)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(1)
            .build()
    }

    /**
     * 单张卡片的完整布局结果（坐标均相对卡片顶部）。
     * [calculateContentHeight] 与 [drawScrollContent] 共用，保证测量与绘制一致。
     *
     * 单行标题时各坐标与既有已验证布局完全一致；标题换到第二行时，
     * meta / 短评 / 卡片高度整体联动下移，评分标签固定在首行右侧不受影响。
     */
    private class CardLayout(
        val titleLayout: StaticLayout,
        val metaLayout: StaticLayout,
        val quoteLayout: StaticLayout?,
        val metaTop: Float,
        val quoteTop: Float,
        val height: Float,
    )

    private fun cardLayoutFor(book: Book, canvasWidth: Float): CardLayout {
        val titleLayout = titleLayoutFor(book, canvasWidth)
        val metaLayout = metaLayoutFor(book, canvasWidth)
        val quoteLayout = quoteLayoutFor(book, canvasWidth)

        // 标题超出单行时，其多出的高度逐级下推 meta 与短评
        val singleTitleHeight = titlePaint.fontSpacing + TITLE_LINE_SPACING
        val extraTitleHeight = max(0f, titleLayout.height - singleTitleHeight)

        // meta 原 drawText 基线为 64f；StaticLayout 顶部 = 基线 + ascent（ascent 为负）
        val metaTop = 64f + metaPaint.ascent() + extraTitleHeight
        val quoteTop = QUOTE_TOP_OFFSET + extraTitleHeight
        val height = if (quoteLayout != null) {
            quoteTop + quoteLayout.height + 16f
        } else {
            BASE_CARD_HEIGHT + extraTitleHeight
        }
        return CardLayout(titleLayout, metaLayout, quoteLayout, metaTop, quoteTop, height)
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

        // 2. 头部：题字与印章
        val primaryText = if (dark) Color.parseColor("#F5F0E6") else Color.parseColor("#2C241E")
        val secondaryText = if (dark) Color.parseColor("#A89F91") else Color.parseColor("#7A6E65")
        val accentGold = if (dark) Color.parseColor("#E6C265") else Color.parseColor("#996515")
        val sakuraColor = Color.parseColor("#E57373")

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        textPaint.textSize = canvasWidth * 0.052f
        textPaint.color = primaryText
        canvas.drawText("「 追 番 编 年 史 · 心 智 画 卷 」", canvasWidth / 2f, 100f, textPaint)

        // 红色朱砂印章
        drawChinatownSeal(canvas, canvasWidth - 110f, 65f, "追番", "印记")

        textPaint.isFakeBoldText = false
        textPaint.textSize = canvasWidth * 0.030f
        textPaint.color = secondaryText
        canvas.drawText("1995 ~ 2024 · 历经三十载光阴 · 沉淀七十一座精神坐标", canvasWidth / 2f, 150f, textPaint)

        // 统计胶囊条
        val finishedCount = animeList.count { it.status == BookStatus.FINISHED }
        val wishlistCount = animeList.count { it.status == BookStatus.WISHLIST }
        val statText = "🌸 补完 $finishedCount 部   ·   🌟 想追 $wishlistCount 部   ·   💮 精神印记共鸣"
        drawCapsuleBadge(canvas, canvasWidth / 2f, 205f, statText, accentGold, dark)

        // 3. 中轴时光线与各年份番剧
        val axisX = 90f
        var currentY = 270f

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

            // 绘制该年份下的番剧卡片
            books.forEach { book ->
                val cardLayout = cardLayoutFor(book, canvasWidth)
                val cardLeft = axisX + 24f
                val cardRight = canvasWidth - 45f
                val cardTop = currentY
                val cardHeight = cardLayout.height
                val cardBottom = cardTop + cardHeight

                // 卡片底色
                cardPaint.style = Paint.Style.FILL
                cardPaint.color = if (dark) Color.parseColor("#221D1A") else Color.parseColor("#FFFFFF")
                cardPaint.alpha = if (dark) 220 else 240
                canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 14f, 14f, cardPaint)

                // 卡片微边框
                cardPaint.style = Paint.Style.STROKE
                cardPaint.strokeWidth = 1.2f
                cardPaint.color = if (dark) Color.parseColor("#3D342E") else Color.parseColor("#E8E2D9")
                canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 14f, 14f, cardPaint)

                // 番剧标题（StaticLayout 多行自动换行，最多 2 行）
                titlePaint.color = primaryText
                canvas.save()
                canvas.translate(cardLeft + 18f, cardTop + TITLE_TOP_OFFSET)
                cardLayout.titleLayout.draw(canvas)
                canvas.restore()

                // 监督与制作社 / 分类（单行 StaticLayout，超宽自动省略号）
                metaPaint.color = secondaryText
                canvas.save()
                canvas.translate(cardLeft + 18f, cardTop + cardLayout.metaTop)
                cardLayout.metaLayout.draw(canvas)
                canvas.restore()

                // 短评金句（StaticLayout 多行自动换行，最多 3 行）
                cardLayout.quoteLayout?.let { layout ->
                    quotePaint.color = if (dark) Color.parseColor("#C8B8A6") else Color.parseColor("#5A4E45")
                    canvas.save()
                    canvas.translate(cardLeft + 18f, cardTop + cardLayout.quoteTop)
                    layout.draw(canvas)
                    canvas.restore()
                }

                // 状态标签或评分
                val isFinished = book.status == BookStatus.FINISHED
                val badgeText = if (isFinished) {
                    book.rating?.let { "★ $it" } ?: "🌸 已补完"
                } else {
                    "🌟 想追"
                }
                val badgeColor = if (isFinished) sakuraColor else accentGold
                drawSmallTag(canvas, cardRight - 85f, cardTop + 28f, badgeText, badgeColor, dark)

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
        canvas.drawText("「 人 生 如 旅 · 阅 痕 常 留 」", canvasWidth / 2f, currentY, textPaint)

        currentY += 35f
        textPaint.isFakeBoldText = false
        textPaint.textSize = canvasWidth * 0.025f
        textPaint.color = secondaryText
        val dateStr = SimpleDateFormat("yyyy' 年 'MM' 月 'dd' 日 · 生成于《阅痕 ReadTrace》'", Locale.CHINA).format(Date())
        canvas.drawText(dateStr, canvasWidth / 2f, currentY, textPaint)

        // 底部居中印章
        drawChinatownSeal(canvas, canvasWidth / 2f - 28f, currentY + 20f, "阅痕", "永驻")
    }

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
