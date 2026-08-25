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
import android.util.AttributeSet
import android.view.MotionEvent
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
            intArrayOf(Color.parseColor("#10141C"), Color.parseColor("#182030"), Color.parseColor("#0E121A")),
            Color.parseColor("#A0C4E2"),
            intArrayOf(Color.parseColor("#80E0FF"), Color.parseColor("#C39BD3"), Color.parseColor("#F9E79F"), Color.parseColor("#85C1E9")),
            Color.parseColor("#151D2A"),
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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

    private fun loadCoverBitmap(url: String) {
        if (url.isBlank()) {
            gameCoverBitmap = null
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

                if (bmp != null) {
                    post {
                        gameCoverBitmap = bmp
                        invalidate()
                    }
                }
            } catch (_: Exception) {
                gameCoverBitmap = null
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCartridge(canvas, width.toFloat(), height.toFloat())
    }

    private fun drawCartridge(canvas: Canvas, w: Float, h: Float) {
        // 1. 绘制背景渐变
        val bgShader = LinearGradient(0f, 0f, w, h, currentTheme.bgColors, null, Shader.TileMode.CLAMP)
        paint.shader = bgShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val gameItem = game ?: return

        // 2. 计算 3:4 实体卡带盒区域 (居中适配)
        val pad = w * 0.05f
        val boxLeft = pad
        val boxTop = h * 0.03f
        val boxRight = w - pad
        val boxBottom = h - h * 0.03f
        val boxW = boxRight - boxLeft
        val boxH = boxBottom - boxTop
        val cornerRadius = boxW * 0.04f

        val boxRect = RectF(boxLeft, boxTop, boxRight, boxBottom)

        // 盒体背景
        paint.color = currentTheme.cardBgColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, paint)

        // 盒体外边框 (高阶流光边框)
        paint.color = currentTheme.caseBorderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        canvas.drawRoundRect(boxRect, cornerRadius, cornerRadius, paint)

        // 3. 顶部全息流光带 (Hologram Header Band)
        val headerH = boxH * 0.09f
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

        // 顶部文字
        textPaint.color = Color.WHITE
        textPaint.textSize = headerH * 0.36f
        textPaint.isFakeBoldText = true
        canvas.drawText("READTRACE PLATINUM EDITION", boxLeft + boxW * 0.05f, boxTop + headerH * 0.62f, textPaint)

        textPaint.color = Color.argb(230, 255, 255, 255)
        textPaint.textSize = headerH * 0.28f
        textPaint.isFakeBoldText = false
        val platformStr = "STEAM VERIFIED · 100% CLEAR"
        canvas.drawText(platformStr, boxRight - boxW * 0.05f - textPaint.measureText(platformStr), boxTop + headerH * 0.62f, textPaint)
        canvas.restore()

        // 4. 中部游戏主视觉封面
        val coverTop = boxTop + headerH + boxH * 0.03f
        val coverW = boxW * 0.90f
        val coverH = boxH * 0.46f
        val coverLeft = boxLeft + (boxW - coverW) * 0.5f
        val coverRect = RectF(coverLeft, coverTop, coverLeft + coverW, coverTop + coverH)

        if (gameCoverBitmap != null) {
            canvas.save()
            val coverClip = Path().apply {
                addRoundRect(coverRect, 16f, 16f, Path.Direction.CW)
            }
            canvas.clipPath(coverClip)
            canvas.drawBitmap(gameCoverBitmap!!, null, coverRect, paint)
            canvas.restore()
        } else {
            paint.color = Color.argb(40, 255, 255, 255)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(coverRect, 16f, 16f, paint)

            textPaint.color = currentTheme.subTextColor
            textPaint.textSize = coverH * 0.16f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎮", coverRect.centerX(), coverRect.centerY(), textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }

        // 封面边框与玻璃反光
        paint.color = currentTheme.caseBorderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(coverRect, 16f, 16f, paint)

        // 5. 游戏标题与制作团队
        val titleY = coverRect.bottom + boxH * 0.052f
        textPaint.color = currentTheme.textColor
        textPaint.textSize = boxH * 0.042f
        textPaint.isFakeBoldText = true
        val title = if (gameItem.title.length > 14) gameItem.title.substring(0, 13) + "..." else gameItem.title
        canvas.drawText(title, coverLeft, titleY, textPaint)

        val authorY = titleY + boxH * 0.032f
        textPaint.color = currentTheme.trophyColor
        textPaint.textSize = boxH * 0.024f
        textPaint.isFakeBoldText = false
        val author = "制作/发行: " + (gameItem.author.orEmpty().ifBlank { "独立神作工作室" })
        canvas.drawText(author, coverLeft, authorY, textPaint)

        // 6. 白金通关徽章 (左下) & 六维心智雷达 (右下)
        val infoRowTop = authorY + boxH * 0.025f
        val infoRowH = boxH * 0.18f

        // A. 左侧白金通关徽章盒
        val badgeW = coverW * 0.46f
        val badgeRect = RectF(coverLeft, infoRowTop, coverLeft + badgeW, infoRowTop + infoRowH)
        paint.color = Color.argb(30, Color.red(currentTheme.trophyColor), Color.green(currentTheme.trophyColor), Color.blue(currentTheme.trophyColor))
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(badgeRect, 12f, 12f, paint)

        paint.color = currentTheme.trophyColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(badgeRect, 12f, 12f, paint)

        // 徽章内容
        textPaint.color = currentTheme.trophyColor
        textPaint.textSize = infoRowH * 0.28f
        textPaint.isFakeBoldText = true
        canvas.drawText("🏆 白金通关认证", badgeRect.left + badgeW * 0.08f, badgeRect.top + infoRowH * 0.38f, textPaint)

        textPaint.color = currentTheme.textColor
        textPaint.textSize = infoRowH * 0.20f
        textPaint.isFakeBoldText = false
        val scoreAvg = String.format(Locale.getDefault(), "%.1f", mindprint?.averageScore() ?: 9.8)
        canvas.drawText("通关评级: SSS (战力 $scoreAvg)", badgeRect.left + badgeW * 0.08f, badgeRect.top + infoRowH * 0.68f, textPaint)

        val curDate = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())
        textPaint.color = currentTheme.subTextColor
        textPaint.textSize = infoRowH * 0.16f
        canvas.drawText("入库时刻: $curDate", badgeRect.left + badgeW * 0.08f, badgeRect.top + infoRowH * 0.90f, textPaint)

        // B. 右侧六维心智战力雷达
        val radarCenterX = coverLeft + coverW - infoRowH * 0.55f
        val radarCenterY = infoRowTop + infoRowH * 0.5f
        val radarRadius = infoRowH * 0.44f

        drawMiniRadar(canvas, radarCenterX, radarCenterY, radarRadius)

        // 7. 底部名台词金句横幅 (Quote Banner)
        val quoteTop = infoRowTop + infoRowH + boxH * 0.025f
        val quoteH = boxBottom - quoteTop - boxH * 0.025f
        val quoteRect = RectF(coverLeft, quoteTop, coverLeft + coverW, quoteTop + quoteH)

        paint.color = Color.argb(40, Color.red(currentTheme.caseBorderColor), Color.green(currentTheme.caseBorderColor), Color.blue(currentTheme.caseBorderColor))
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(quoteRect, 10f, 10f, paint)

        paint.color = currentTheme.caseBorderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(quoteRect, 10f, 10f, paint)

        val quote = gameItem.shortComment?.ifBlank { "“犹豫就会败北！忍者的宿命，唯有侍奉唯一之主。”" }
            ?: "“落叶跳费，黄金树将庇护你我。褪色者啊，成为艾尔登之王吧！”"
        textPaint.color = currentTheme.textColor
        textPaint.textSize = quoteH * 0.42f
        textPaint.isFakeBoldText = false
        val displayQuote = if (quote.length > 28) quote.substring(0, 27) + "..." else quote
        canvas.drawText(displayQuote, quoteRect.left + quoteRect.width() * 0.04f, quoteRect.top + quoteH * 0.65f, textPaint)
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
        if (event.action == MotionEvent.ACTION_DOWN) {
            onCartridgeClickListener?.invoke()
            return true
        }
        return super.onTouchEvent(event)
    }

    fun create1080pPosterBitmap(): Bitmap {
        val targetW = 1080
        val targetH = 1440 // 3:4 经典卡带盒比例
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawCartridge(canvas, targetW.toFloat(), targetH.toFloat())
        return bmp
    }
}
