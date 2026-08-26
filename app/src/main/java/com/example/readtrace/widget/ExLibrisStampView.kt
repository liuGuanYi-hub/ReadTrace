package com.example.readtrace.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * 📜 典藏中世纪铜版画藏书票自定义渲染视图 (ExLibrisStampView)
 * 对标 One Page Love / Land-book 瑞士排版与欧洲古籍藏书票：
 * - 纯矢量双重古典版画饰边（Double Engraved Borders & Corner Flourishes）；
 * - "EX-LIBRIS // BIBLIOTHECA READTRACE" 衬线典藏手卷排版；
 * - 3D 浮雕烫金火漆封蜡印章（Wax Seal Stamp）；
 * - 极客等宽防伪证书编号与四时自适应配色体系。
 */
class ExLibrisStampView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class Theme(
        val bgColors: IntArray,
        val borderColor: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val sealColor: Int,
    ) {
        PARCHMENT(
            bgColors = intArrayOf(Color.parseColor("#FBF7EE"), Color.parseColor("#EFE6CE")),
            borderColor = Color.parseColor("#7A5835"),
            textPrimary = Color.parseColor("#3D2B1F"),
            textSecondary = Color.parseColor("#8C6D46"),
            sealColor = Color.parseColor("#A82222"), // 古典酒红封蜡
        ),
        MIDNIGHT(
            bgColors = intArrayOf(Color.parseColor("#151A24"), Color.parseColor("#0A0D14")),
            borderColor = Color.parseColor("#4DEEEA"),
            textPrimary = Color.parseColor("#FFFFFF"),
            textSecondary = Color.parseColor("#88A0C0"),
            sealColor = Color.parseColor("#4DEEEA"), // 极光青封蜡
        ),
        WOODCUT(
            bgColors = intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#F0F0F0")),
            borderColor = Color.parseColor("#111111"),
            textPrimary = Color.parseColor("#111111"),
            textSecondary = Color.parseColor("#555555"),
            sealColor = Color.parseColor("#D4AF37"), // 曜金封蜡
        ),
        CYBER(
            bgColors = intArrayOf(Color.parseColor("#081510"), Color.parseColor("#020906")),
            borderColor = Color.parseColor("#00FF88"),
            textPrimary = Color.parseColor("#00FF88"),
            textSecondary = Color.parseColor("#008844"),
            sealColor = Color.parseColor("#00FF88"), // 荧光绿封蜡
        ),
    }

    var currentTheme: Theme = Theme.PARCHMENT
        set(value) {
            field = value
            invalidate()
        }

    var bookTitle: String = "小王子"
        set(value) {
            field = value
            invalidate()
        }

    var authorName: String = "安托万·德·圣-埃克苏佩里"
        set(value) {
            field = value
            invalidate()
        }

    var serialNumber: String = "#EXL-2026-0826 // NO.042"
        set(value) {
            field = value
            invalidate()
        }

    var quoteText: String = "真正重要的东西，用肉眼是看不见的。"
        set(value) {
            field = value
            invalidate()
        }

    var coverBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    // 绘制画笔
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cardRect = RectF()
    private val borderPath = Path()

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val pad = 12f * density
        cardRect.set(pad, pad, w - pad, h - pad)

        // 1. 绘制藏书票底板
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            currentTheme.bgColors,
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(cardRect, 16f * density, 16f * density, bgPaint)

        // 2. 绘制古典双重版画花纹外框与内框
        drawEngravedBorders(canvas, cardRect, density)

        // 3. 绘制顶部 "EX-LIBRIS" 典藏标题
        textPaint.color = currentTheme.textPrimary
        textPaint.typeface = Typeface.SERIF
        textPaint.textSize = 18f * resources.displayMetrics.scaledDensity
        textPaint.isFakeBoldText = true
        textPaint.letterSpacing = 0.25f
        canvas.drawText("EX · LIBRIS", w / 2f, cardRect.top + 36f * density, textPaint)

        // 4. 绘制副标题 "READTRACE BIBLIOTHECA"
        textPaint.color = currentTheme.textSecondary
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 8.5f * resources.displayMetrics.scaledDensity
        textPaint.isFakeBoldText = false
        textPaint.letterSpacing = 0.18f
        canvas.drawText("COLLECTION OF THE MINDPRINT", w / 2f, cardRect.top + 52f * density, textPaint)

        // 5. 绘制中央封面或版画图腾插画
        drawCenterArtwork(canvas, w, h, cardRect, density)

        // 6. 绘制书名与作者
        textPaint.color = currentTheme.textPrimary
        textPaint.typeface = Typeface.SERIF
        textPaint.textSize = 16f * resources.displayMetrics.scaledDensity
        textPaint.isFakeBoldText = true
        textPaint.letterSpacing = 0.05f
        canvas.drawText("《$bookTitle》", w / 2f, cardRect.bottom - 105f * density, textPaint)

        textPaint.color = currentTheme.textSecondary
        textPaint.typeface = Typeface.SERIF
        textPaint.textSize = 11.5f * resources.displayMetrics.scaledDensity
        textPaint.isFakeBoldText = false
        canvas.drawText("—— $authorName 著", w / 2f, cardRect.bottom - 86f * density, textPaint)

        // 7. 绘制金句
        textPaint.color = currentTheme.textPrimary
        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.textSize = 10f * resources.displayMetrics.scaledDensity
        val displayQuote = if (quoteText.length > 20) "“" + quoteText.substring(0, 19) + "…”" else "“$quoteText”"
        canvas.drawText(displayQuote, w / 2f, cardRect.bottom - 66f * density, textPaint)

        // 8. 绘制底部等宽证书编号
        textPaint.color = currentTheme.textSecondary
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 8f * resources.displayMetrics.scaledDensity
        textPaint.letterSpacing = 0.12f
        canvas.drawText(serialNumber, w / 2f, cardRect.bottom - 22f * density, textPaint)

        // 9. 绘制右下角 3D 烫金火漆封蜡印章 (Wax Seal Stamp)
        drawWaxSealStamp(canvas, cardRect.right - 44f * density, cardRect.bottom - 44f * density, 26f * density)
    }

    private fun drawEngravedBorders(canvas: Canvas, rect: RectF, density: Float) {
        borderPaint.color = currentTheme.borderColor
        borderPaint.strokeWidth = 2f
        borderPaint.pathEffect = null

        // 外边框
        val outerPad = 8f * density
        val outerRect = RectF(rect.left + outerPad, rect.top + outerPad, rect.right - outerPad, rect.bottom - outerPad)
        canvas.drawRoundRect(outerRect, 10f * density, 10f * density, borderPaint)

        // 内细虚线框
        val innerPad = 13f * density
        val innerRect = RectF(rect.left + innerPad, rect.top + innerPad, rect.right - innerPad, rect.bottom - innerPad)
        borderPaint.strokeWidth = 1f
        borderPaint.pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
        canvas.drawRoundRect(innerRect, 8f * density, 8f * density, borderPaint)
        borderPaint.pathEffect = null
    }

    private fun drawCenterArtwork(canvas: Canvas, w: Float, h: Float, rect: RectF, density: Float) {
        val cx = w / 2f
        val cy = rect.top + (rect.height() * 0.40f)
        val artW = 100f * density
        val artH = 135f * density

        val artRect = RectF(cx - artW / 2f, cy - artH / 2f, cx + artW / 2f, cy + artH / 2f)

        val bmp = coverBitmap
        if (bmp != null) {
            canvas.save()
            val clipPath = Path().apply {
                addRoundRect(artRect, 6f * density, 6f * density, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            val src = Rect(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, src, artRect, null)
            canvas.restore()
        } else {
            // 默认文艺版画羽毛笔图腾
            borderPaint.color = currentTheme.borderColor
            borderPaint.strokeWidth = 1.2f
            canvas.drawRoundRect(artRect, 6f * density, 6f * density, borderPaint)

            textPaint.textSize = 36f * resources.displayMetrics.scaledDensity
            canvas.drawText("📜", cx, cy + 12f * density, textPaint)
        }

        // 封面金边线
        borderPaint.color = currentTheme.borderColor
        borderPaint.strokeWidth = 1.5f
        canvas.drawRoundRect(artRect, 6f * density, 6f * density, borderPaint)
    }

    private fun drawWaxSealStamp(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // 1. 火漆外缘微不规则底圆
        sealPaint.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.2f,
            intArrayOf(Color.WHITE, currentTheme.sealColor, Color.parseColor("#220000")),
            floatArrayOf(0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, sealPaint)

        // 2. 火漆内凹陷环
        borderPaint.color = Color.parseColor("#44FFFFFF")
        borderPaint.strokeWidth = 1.5f
        canvas.drawCircle(cx, cy, radius * 0.75f, borderPaint)

        // 3. 火漆中央 "RT" 钢印
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.SERIF
        textPaint.textSize = radius * 0.65f
        textPaint.isFakeBoldText = true
        textPaint.letterSpacing = 0f
        canvas.drawText("RT", cx, cy + (radius * 0.22f), textPaint)
    }

    /**
     * 生成 4K 高清 Bitmap 用于海报导出
     */
    fun createHighResBitmap(): Bitmap {
        val targetWidth = 1440
        val targetHeight = 2160
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val oldW = width
        val oldH = height
        layout(0, 0, targetWidth, targetHeight)
        draw(canvas)
        layout(0, 0, oldW, oldH)

        return bitmap
    }
}
