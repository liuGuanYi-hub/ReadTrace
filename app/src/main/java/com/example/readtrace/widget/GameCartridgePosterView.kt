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
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookMindprint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class GameCartridgePosterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class CartridgeTheme(
        val displayName: String,
        val bgColors: IntArray,
        val caseBorderColor: Int,
        val holoBandColors: IntArray,
        val cardBgColor: Int,
        val textColor: Int,
        val subTextColor: Int,
        val trophyColor: Int,
        val radarColor: Int,
    ) {
        PLATINUM_HOLO(
            "💎 耀世白金",
            intArrayOf(Color.parseColor("#0B0E14"), Color.parseColor("#141B26"), Color.parseColor("#0A0D12")),
            Color.parseColor("#85B8E2"),
            intArrayOf(Color.parseColor("#7EE8FA"), Color.parseColor("#EECDA3"), Color.parseColor("#ECA0FF"), Color.parseColor("#80D0C7")),
            Color.parseColor("#101722"),
            Color.parseColor("#F0F6FC"),
            Color.parseColor("#8B949E"),
            Color.parseColor("#E5E8E8"), // 白金银
            Color.parseColor("#58A6FF"),
        ),
        CYBER_NEON(
            "⚡ 赛博霓虹",
            intArrayOf(Color.parseColor("#080B10"), Color.parseColor("#0F1A24"), Color.parseColor("#150A21")),
            Color.parseColor("#00F5D4"),
            intArrayOf(Color.parseColor("#00F5D4"), Color.parseColor("#7B2CBF"), Color.parseColor("#F72585")),
            Color.parseColor("#111827"),
            Color.parseColor("#E0F7FA"),
            Color.parseColor("#80DEEA"),
            Color.parseColor("#00F5D4"), // 霓虹青
            Color.parseColor("#F72585"),
        ),
        DARK_ASH(
            "🔥 暗黑魂系",
            intArrayOf(Color.parseColor("#121110"), Color.parseColor("#1C1510"), Color.parseColor("#0D0B0A")),
            Color.parseColor("#D47A37"),
            intArrayOf(Color.parseColor("#E65100"), Color.parseColor("#FFB74D"), Color.parseColor("#BF360C")),
            Color.parseColor("#1E1712"),
            Color.parseColor("#FDFEFE"),
            Color.parseColor("#A69385"),
            Color.parseColor("#FF9F1C"), // 初火金
            Color.parseColor("#E76F51"),
        ),
        RETRO_FAMICOM(
            "🎮 红白经典",
            intArrayOf(Color.parseColor("#F5EBE1"), Color.parseColor("#EBDCC9"), Color.parseColor("#E0CDB8")),
            Color.parseColor("#A61B24"),
            intArrayOf(Color.parseColor("#A61B24"), Color.parseColor("#D4AF37"), Color.parseColor("#2E4053")),
            Color.parseColor("#FAF3EB"),
            Color.parseColor("#2C221E"),
            Color.parseColor("#7A685D"),
            Color.parseColor("#C84B31"), // 经典红
            Color.parseColor("#2D4263"),
        ),
    }

    private var currentTheme: CartridgeTheme = CartridgeTheme.PLATINUM_HOLO
    private var game: Book? = null
    private var mindprint: BookMindprint? = null
    private var gameCoverBitmap: Bitmap? = null

    /** 封面取景焦点（0=贴左/上，1=贴右/下，0.5=居中）：拖拽调整，按作品持久化，导出同偏移渲染 */
    var coverFocalX: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var coverFocalY: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    /** 取景变化回调（拖拽结束/双击复位时触发）：Activity 据此按作品 id 持久化 */
    var onCoverOffsetChanged: ((focalX: Float, focalY: Float) -> Unit)? = null

    private val lastCoverRect = RectF()
    private var draggingCover = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastTapTime = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val staticTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun setData(
        game: Book,
        mindprint: BookMindprint?,
        theme: CartridgeTheme = CartridgeTheme.PLATINUM_HOLO,
    ) {
        this.game = game
        this.mindprint = mindprint
        this.currentTheme = theme

        loadCoverBitmap(game.coverUrl.orEmpty())
        invalidate()
    }

    fun setTheme(theme: CartridgeTheme) {
        this.currentTheme = theme
        invalidate()
    }

    fun getTheme(): CartridgeTheme = currentTheme

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
    }

    private fun loadCoverBitmap(url: String) {
        if (url.isBlank()) {
            gameCoverBitmap = null
            invalidate()
            return
        }

        com.example.readtrace.util.CoverImageHelper.loadCoverBitmap(context, url, 720, 1080) { bmp ->
            gameCoverBitmap = bmp
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCartridge(canvas, width.toFloat(), height.toFloat())
    }

    private fun drawCartridge(canvas: Canvas, w: Float, h: Float) {
        // 1. 绘制背景渐变
        val bgShader = LinearGradient(0f, 0f, w, h, currentTheme.bgColors, null, Shader.TileMode.CLAMP)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = bgShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val gameItem = game ?: return

        // 2. 实体卡带外壳 (Cartridge Case, 3:4 比例自适应居中)
        val padH = w * 0.04f
        val padV = h * 0.025f
        val boxLeft = padH
        val boxTop = padV
        val boxRight = w - padH
        val boxBottom = h - padV
        val boxW = boxRight - boxLeft
        val boxH = boxBottom - boxTop
        val cornerRadius = boxW * 0.045f

        val boxRect = RectF(boxLeft, boxTop, boxRight, boxBottom)

        // 盒体背景
        paint.color = currentTheme.cardBgColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, paint)

        // 盒体外边框 (高阶流光微光边框)
        paint.color = currentTheme.caseBorderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, paint)

        // 3. 顶部全息流光带 (Hologram Header Band)
        val headerH = boxH * 0.075f
        val headerRect = RectF(boxLeft, boxTop, boxRight, boxTop + headerH)

        canvas.save()
        val headerClip = Path().apply {
            addRoundRect(boxRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(headerClip)

        val holoShader = LinearGradient(boxLeft, boxTop, boxRight, boxTop + headerH, currentTheme.holoBandColors, null, Shader.TileMode.CLAMP)
        paint.shader = holoShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(headerRect, paint)
        paint.shader = null

        // 顶部文字与徽标 (左侧品牌 · 右侧限定胶囊)
        textPaint.color = Color.WHITE
        textPaint.textSize = headerH * 0.38f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("READTRACE PLATINUM", boxLeft + boxW * 0.04f, boxTop + headerH * 0.64f, textPaint)

        // 右侧限定徽标胶囊
        val pillText = "100% CLEAR ★"
        textPaint.textSize = headerH * 0.30f
        textPaint.isFakeBoldText = true
        val pillTextW = textPaint.measureText(pillText)
        val pillPadH = boxW * 0.02f
        val pillW = pillTextW + pillPadH * 2
        val pillH = headerH * 0.60f
        val pillRight = boxRight - boxW * 0.04f
        val pillLeft = pillRight - pillW
        val pillTop = boxTop + (headerH - pillH) * 0.5f
        val pillRect = RectF(pillLeft, pillTop, pillRight, pillTop + pillH)

        paint.color = Color.argb(80, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(pillRect, pillH * 0.5f, pillH * 0.5f, paint)

        paint.color = Color.argb(120, 255, 255, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(pillRect, pillH * 0.5f, pillH * 0.5f, paint)

        textPaint.color = Color.WHITE
        canvas.drawText(pillText, pillLeft + pillPadH, pillTop + pillH * 0.70f, textPaint)
        canvas.restore()

        // 4. 中部游戏主视觉封面 (CenterCrop 保持原始纵横比无畸变)
        val coverTop = boxTop + headerH + boxH * 0.022f
        val coverW = boxW * 0.92f
        val coverH = boxH * 0.42f
        val coverLeft = boxLeft + (boxW - coverW) * 0.5f
        val coverRect = RectF(coverLeft, coverTop, coverLeft + coverW, coverTop + coverH)
        lastCoverRect.set(coverRect)

        if (gameCoverBitmap != null && !gameCoverBitmap!!.isRecycled) {
            canvas.save()
            val coverClip = Path().apply {
                addRoundRect(coverRect, 14f, 14f, Path.Direction.CW)
            }
            canvas.clipPath(coverClip)

            val bmp = gameCoverBitmap!!
            val bmpW = bmp.width.toFloat()
            val bmpH = bmp.height.toFloat()
            val targetRatio = coverW / coverH
            val bmpRatio = bmpW / bmpH

            val srcRect = if (bmpRatio > targetRatio) {
                val cropW = bmpH * targetRatio
                val left = (bmpW - cropW) * coverFocalX.coerceIn(0f, 1f)
                Rect(left.toInt(), 0, (left + cropW).toInt(), bmpH.toInt())
            } else {
                val cropH = bmpW / targetRatio
                val top = (bmpH - cropH) * coverFocalY.coerceIn(0f, 1f)
                Rect(0, top.toInt(), bmpW.toInt(), (top + cropH).toInt())
            }
            canvas.drawBitmap(bmp, srcRect, coverRect, bitmapPaint)
            canvas.restore()
        } else {
            paint.color = Color.argb(30, 255, 255, 255)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(coverRect, 14f, 14f, paint)

            textPaint.color = currentTheme.subTextColor
            textPaint.textSize = coverH * 0.16f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎮", coverRect.centerX(), coverRect.centerY(), textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }

        // 封面流光高精边框
        paint.color = currentTheme.caseBorderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(coverRect, 14f, 14f, paint)

        // 5. 游戏标题与制作团队
        val titleY = coverRect.bottom + boxH * 0.048f
        textPaint.color = currentTheme.textColor
        textPaint.textSize = boxH * 0.038f
        textPaint.isFakeBoldText = true
        val title = if (gameItem.title.length > 18) gameItem.title.substring(0, 17) + "…" else gameItem.title
        canvas.drawText(title, coverLeft, titleY, textPaint)

        val authorY = titleY + boxH * 0.028f
        textPaint.color = currentTheme.trophyColor
        textPaint.textSize = boxH * 0.022f
        textPaint.isFakeBoldText = false
        val author = "制作/发行: " + (gameItem.author.orEmpty().ifBlank { "独立神作工作室" })
        val displayAuthor = if (author.length > 24) author.substring(0, 23) + "…" else author
        canvas.drawText(displayAuthor, coverLeft, authorY, textPaint)

        // 6. 白金通关徽章 (左侧) & 六维心智雷达 (右侧)
        val infoRowTop = authorY + boxH * 0.020f
        val infoRowH = boxH * 0.175f

        // A. 左侧白金通关徽章盒 (自适应宽度与内边距)
        val badgeW = coverW * 0.60f
        val badgeRect = RectF(coverLeft, infoRowTop, coverLeft + badgeW, infoRowTop + infoRowH)
        paint.color = Color.argb(35, Color.red(currentTheme.trophyColor), Color.green(currentTheme.trophyColor), Color.blue(currentTheme.trophyColor))
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(badgeRect, 10f, 10f, paint)

        paint.color = currentTheme.trophyColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(badgeRect, 10f, 10f, paint)

        // 徽章文本 (严格自动测量缩放，确保 100% 居内无溢出)
        val badgePadX = 10f * (w / 320f)
        val maxBadgeTextW = badgeW - badgePadX * 2.2f

        // 行1: 🏆 白金通关认证
        val titleBadge = "🏆 白金通关认证"
        textPaint.color = currentTheme.trophyColor
        textPaint.textSize = (infoRowH * 0.22f).coerceAtLeast(10f)
        textPaint.isFakeBoldText = true
        while (textPaint.measureText(titleBadge) > maxBadgeTextW && textPaint.textSize > 8f) {
            textPaint.textSize -= 0.5f
        }
        canvas.drawText(titleBadge, badgeRect.left + badgePadX, badgeRect.top + infoRowH * 0.35f, textPaint)

        // 行2: 评级: SSS · 战力 8.9
        val scoreAvg = String.format(Locale.getDefault(), "%.1f", mindprint?.averageScore() ?: 9.8)
        val rankBadge = "评级: SSS · 战力 $scoreAvg"
        textPaint.color = currentTheme.textColor
        textPaint.textSize = (infoRowH * 0.18f).coerceAtLeast(9f)
        textPaint.isFakeBoldText = false
        while (textPaint.measureText(rankBadge) > maxBadgeTextW && textPaint.textSize > 7f) {
            textPaint.textSize -= 0.5f
        }
        canvas.drawText(rankBadge, badgeRect.left + badgePadX, badgeRect.top + infoRowH * 0.64f, textPaint)

        // 行3: 入库时刻: 2026.08.28
        val curDate = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        val dateBadge = "入库时刻: $curDate"
        textPaint.color = currentTheme.subTextColor
        textPaint.textSize = (infoRowH * 0.15f).coerceAtLeast(8f)
        while (textPaint.measureText(dateBadge) > maxBadgeTextW && textPaint.textSize > 6f) {
            textPaint.textSize -= 0.5f
        }
        canvas.drawText(dateBadge, badgeRect.left + badgePadX, badgeRect.top + infoRowH * 0.88f, textPaint)

        // B. 右侧六维心智战力雷达
        val radarCenterX = coverLeft + badgeW + (coverW - badgeW) * 0.5f
        val radarCenterY = infoRowTop + infoRowH * 0.5f
        val radarRadius = (coverW - badgeW) * 0.42f

        drawMiniRadar(canvas, radarCenterX, radarCenterY, radarRadius)

        // 7. 底部名台词与高光短评横幅 (Quote Banner, 支持自动多行折行与全字展示)
        val quoteTop = infoRowTop + infoRowH + boxH * 0.020f
        val quoteBottom = boxBottom - boxH * 0.022f
        val quoteRect = RectF(coverLeft, quoteTop, coverLeft + coverW, quoteBottom)

        paint.color = Color.argb(35, Color.red(currentTheme.caseBorderColor), Color.green(currentTheme.caseBorderColor), Color.blue(currentTheme.caseBorderColor))
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(quoteRect, 8f, 8f, paint)

        paint.color = Color.argb(120, Color.red(currentTheme.caseBorderColor), Color.green(currentTheme.caseBorderColor), Color.blue(currentTheme.caseBorderColor))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(quoteRect, 8f, 8f, paint)

        val quote = gameItem.shortComment?.ifBlank { "“犹豫就会败北！忍者的宿命，唯有侍奉唯一之主。”" }
            ?: "“落叶跳费，黄金树将庇护你我。褪色者啊，成为艾尔登之王吧！”"

        val quotePadX = 10f * (w / 320f)
        val quoteContentW = (quoteRect.width() - quotePadX * 2).toInt().coerceAtLeast(50)

        staticTextPaint.color = currentTheme.textColor
        staticTextPaint.textSize = boxH * 0.025f
        staticTextPaint.isFakeBoldText = false

        val formattedQuote = if (quote.startsWith("“") || quote.startsWith("\"")) quote else "“$quote”"
        val staticLayout = StaticLayout.Builder.obtain(
            formattedQuote,
            0,
            formattedQuote.length,
            staticTextPaint,
            quoteContentW,
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setLineSpacing(2f, 1.15f)
            .build()

        canvas.save()
        val textTop = quoteRect.top + (quoteRect.height() - staticLayout.height) * 0.5f
        canvas.translate(quoteRect.left + quotePadX, textTop.coerceAtLeast(quoteRect.top + 4f))
        staticLayout.draw(canvas)
        canvas.restore()
    }

    private fun drawMiniRadar(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val scores = floatArrayOf(
            mindprint?.depthScore?.toFloat() ?: 9.5f,
            mindprint?.artistryScore?.toFloat() ?: 9.8f,
            mindprint?.emotionScore?.toFloat() ?: 9.6f,
            mindprint?.logicScore?.toFloat() ?: 9.2f,
            mindprint?.difficultyScore?.toFloat() ?: 8.5f,
            mindprint?.healingScore?.toFloat() ?: 7.5f,
        )

        paint.color = Color.argb(60, Color.red(currentTheme.radarColor), Color.green(currentTheme.radarColor), Color.blue(currentTheme.radarColor))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f

        for (step in 1..3) {
            val r = radius * (step / 3f)
            val gridPath = Path()
            for (i in 0..5) {
                val angle = (i * 60 - 90) * Math.PI / 180.0
                val px = (cx + r * cos(angle)).toFloat()
                val py = (cy + r * sin(angle)).toFloat()
                if (i == 0) gridPath.moveTo(px, py) else gridPath.lineTo(px, py)
            }
            gridPath.close()
            canvas.drawPath(gridPath, paint)
        }

        // 数据多边形
        val dataPath = Path()
        for (i in 0..5) {
            val norm = (scores[i] / 10f).coerceIn(0.1f, 1.0f)
            val r = radius * norm
            val angle = (i * 60 - 90) * Math.PI / 180.0
            val px = (cx + r * cos(angle)).toFloat()
            val py = (cy + r * sin(angle)).toFloat()
            if (i == 0) dataPath.moveTo(px, py) else dataPath.lineTo(px, py)
        }
        dataPath.close()

        paint.color = Color.argb(100, Color.red(currentTheme.radarColor), Color.green(currentTheme.radarColor), Color.blue(currentTheme.radarColor))
        paint.style = Paint.Style.FILL
        canvas.drawPath(dataPath, paint)

        paint.color = currentTheme.radarColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawPath(dataPath, paint)
    }

    var onCartridgeClickListener: (() -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                downX = event.x
                downY = event.y
                draggingCover = lastCoverRect.contains(event.x, event.y) && gameCoverBitmap != null
            }
            MotionEvent.ACTION_MOVE -> if (draggingCover) {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                panCoverBy(dx, dy)
            }
            MotionEvent.ACTION_UP -> {
                val moved = kotlin.math.abs(event.x - downX) > ViewConfiguration.get(context).scaledTouchSlop ||
                    kotlin.math.abs(event.y - downY) > ViewConfiguration.get(context).scaledTouchSlop
                if (draggingCover && !moved) {
                    // 封面区双击：复位取景焦点
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 300) {
                        coverFocalX = 0.5f
                        coverFocalY = 0.5f
                        onCoverOffsetChanged?.invoke(coverFocalX, coverFocalY)
                        lastTapTime = 0L
                    } else {
                        lastTapTime = now
                    }
                }
                if (draggingCover && moved) {
                    onCoverOffsetChanged?.invoke(coverFocalX, coverFocalY)
                }
                if (!moved) {
                    // 轻点任意区域保持原有卡带点击反馈
                    onCartridgeClickListener?.invoke()
                }
                draggingCover = false
            }
        }
        return true
    }

    /** 手指拖动换算为取景焦点位移：屏幕位移 ÷ 源图可视余量对应的屏幕像素 */
    private fun panCoverBy(dx: Float, dy: Float) {
        val bmp = gameCoverBitmap ?: return
        val r = lastCoverRect
        if (r.width() <= 0f || r.height() <= 0f) return
        val bmpRatio = bmp.width.toFloat() / bmp.height.toFloat()
        val targetRatio = r.width() / r.height()
        if (bmpRatio > targetRatio) {
            val screenSlackX = r.width() * (1f - targetRatio / bmpRatio)
            if (screenSlackX > 1f) coverFocalX -= dx / screenSlackX
        } else {
            val screenSlackY = r.height() * (1f - bmpRatio / targetRatio)
            if (screenSlackY > 1f) coverFocalY -= dy / screenSlackY
        }
    }

    fun create1080pPosterBitmap(): Bitmap {
        val targetW = 1080
        val targetH = 1520 // 3:4.22 经典卡带盒比例
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawCartridge(canvas, targetW.toFloat(), targetH.toFloat())
        return bmp
    }
}
