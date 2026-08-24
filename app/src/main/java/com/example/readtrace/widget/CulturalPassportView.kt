package com.example.readtrace.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CulturalPassportView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class StampTouchTarget(
        val rect: RectF,
        val item: Book,
    )

    private var items: List<Book> = emptyList()
    private var currentTab: MediaType = MediaType.ANIME // 🌸 番剧 或 🎮 游戏

    private val touchTargets = mutableListOf<StampTouchTarget>()
    var onStampClickListener: ((Book) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val stampColors = intArrayOf(
        Color.parseColor("#C84B31"), // 朱砂红
        Color.parseColor("#2D4263"), // 霁蓝
        Color.parseColor("#1B5E20"), // 苍翠
        Color.parseColor("#6A1B9A"), // 紫藤
        Color.parseColor("#D35400"), // 琥珀橙
        Color.parseColor("#16A085"), // 青碧
    )

    fun setData(items: List<Book>, tab: MediaType = MediaType.ANIME) {
        this.items = items
        this.currentTab = tab
        requestLayout()
        invalidate()
    }

    fun setTab(tab: MediaType) {
        this.currentTab = tab
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val filtered = items.filter { it.mediaType == currentTab }
        val cols = 3
        val rows = (filtered.size + cols - 1) / cols
        val cellH = w / cols * 0.95f
        val headerH = w * 0.52f
        val totalH = (headerH + rows * cellH + w * 0.1f).toInt()
        setMeasuredDimension(w, totalH.coerceAtLeast(600))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            val hit = touchTargets.firstOrNull { it.rect.contains(x, y) }
            if (hit != null) {
                onStampClickListener?.invoke(hit.item)
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPassport(canvas, width.toFloat(), height.toFloat(), isExport = false)
    }

    fun drawPassport(canvas: Canvas, w: Float, h: Float, isExport: Boolean = false) {
        touchTargets.clear()

        // 1. 绘制复古护照羊皮纸纹理底色
        val bgShader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(Color.parseColor("#FAF6F0"), Color.parseColor("#F4EDE2"), Color.parseColor("#EDE4D5")),
            null,
            Shader.TileMode.CLAMP,
        )
        paint.shader = bgShader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // 绘制护照暗纹网格与外金边
        val pad = w * 0.04f
        val passportRect = RectF(pad, pad, w - pad, h - pad)
        paint.color = Color.parseColor("#33B78254")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(passportRect, 18f, 18f, paint)

        // 2. 护照首页抬头 (Passport Header & Identification)
        val headerTop = pad + w * 0.03f
        val headerH = w * 0.42f
        val headerRect = RectF(pad + w * 0.02f, headerTop, w - pad - w * 0.02f, headerTop + headerH)

        // 护照抬头背板 (深蓝/酒红高级烫金风)
        val headerBgShader = LinearGradient(
            headerRect.left, headerRect.top, headerRect.right, headerRect.bottom,
            intArrayOf(Color.parseColor("#1B263B"), Color.parseColor("#0D1B2A")),
            null,
            Shader.TileMode.CLAMP,
        )
        paint.shader = headerBgShader
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(headerRect, 14f, 14f, paint)
        paint.shader = null

        // 烫金国徽/星系图腾
        paint.color = Color.parseColor("#E0A96D")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawCircle(headerRect.left + w * 0.14f, headerRect.centerY(), w * 0.09f, paint)
        canvas.drawCircle(headerRect.left + w * 0.14f, headerRect.centerY(), w * 0.075f, paint)

        textPaint.color = Color.parseColor("#E0A96D")
        textPaint.textSize = w * 0.055f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("🌌", headerRect.left + w * 0.14f, headerRect.centerY() + w * 0.018f, textPaint)

        // 护照标题
        val textLeft = headerRect.left + w * 0.28f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.parseColor("#F5EFE6")
        textPaint.textSize = w * 0.042f
        textPaint.isFakeBoldText = true
        canvas.drawText("READTRACE PASSPORT", textLeft, headerRect.top + headerH * 0.30f, textPaint)

        textPaint.color = Color.parseColor("#E0A96D")
        textPaint.textSize = w * 0.034f
        canvas.drawText("阅痕 · 精神宇宙巡礼护照", textLeft, headerRect.top + headerH * 0.50f, textPaint)

        // 持照人信息
        textPaint.color = Color.parseColor("#A89F91")
        textPaint.textSize = w * 0.024f
        textPaint.isFakeBoldText = false
        val holderInfo = "持照人: 精神旅行家 · 签发地: ReadTrace Universe"
        canvas.drawText(holderInfo, textLeft, headerRect.top + headerH * 0.72f, textPaint)

        val animeCount = items.count { it.mediaType == MediaType.ANIME }
        val gameCount = items.count { it.mediaType == MediaType.GAME }
        val visaStats = "已获签证: 🌸 $animeCount 部番剧入境 · 🎮 $gameCount 款游戏通关"
        canvas.drawText(visaStats, textLeft, headerRect.top + headerH * 0.88f, textPaint)

        // 3. 签证页印章网格 (Visa Stamp Grid)
        val filtered = items.filter { it.mediaType == currentTab }
        val cols = 3
        val gridTop = headerRect.bottom + w * 0.05f
        val cellW = (w - pad * 2 - w * 0.04f) / cols
        val cellH = cellW * 0.98f

        filtered.forEachIndexed { idx, item ->
            val col = idx % cols
            val row = idx / cols
            val cx = pad + w * 0.02f + col * cellW + cellW * 0.5f
            val cy = gridTop + row * cellH + cellH * 0.5f
            val radius = cellW * 0.40f

            val stampRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            if (!isExport) {
                touchTargets.add(StampTouchTarget(stampRect, item))
            }

            drawSingleVisaStamp(canvas, item, cx, cy, radius, idx)
        }
    }

    private fun drawSingleVisaStamp(canvas: Canvas, item: Book, cx: Float, cy: Float, radius: Float, index: Int) {
        val color = stampColors[index % stampColors.size]
        val hash = item.title.hashCode()
        // 自然手盖倾斜角 (-10° 到 +10°)
        val tiltAngle = ((hash % 21) - 10).toFloat()

        canvas.save()
        canvas.rotate(tiltAngle, cx, cy)

        val isGame = item.mediaType == MediaType.GAME

        if (isGame) {
            // 游戏：八边形 / 盾牌通关印章
            drawOctagonStamp(canvas, item, cx, cy, radius, color)
        } else {
            // 番剧：双环圆形入境印章
            drawCircularVisaStamp(canvas, item, cx, cy, radius, color)
        }

        canvas.restore()
    }

    private fun drawCircularVisaStamp(canvas: Canvas, item: Book, cx: Float, cy: Float, radius: Float, color: Int) {
        // 外圆环
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        canvas.drawCircle(cx, cy, radius, paint)

        // 内圆虚线环
        paint.strokeWidth = 1.2f
        paint.pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        canvas.drawCircle(cx, cy, radius * 0.84f, paint)
        paint.pathEffect = null

        // 提取年份与简称
        val year = item.tags.firstOrNull { it.contains("年") }?.replace("年", "") ?: "2024"
        val cleanTitle = if (item.title.length > 5) item.title.substring(0, 4) + ".." else item.title

        textPaint.color = color
        textPaint.textAlign = Paint.Align.CENTER

        // 顶弧标注
        textPaint.textSize = radius * 0.22f
        textPaint.isFakeBoldText = true
        canvas.drawText("🌸 IMMIGRATION", cx, cy - radius * 0.48f, textPaint)

        // 中心作品名
        textPaint.textSize = radius * 0.32f
        textPaint.isFakeBoldText = true
        canvas.drawText(cleanTitle, cx, cy + radius * 0.08f, textPaint)

        // 底部年份与已补完
        textPaint.textSize = radius * 0.20f
        textPaint.isFakeBoldText = false
        canvas.drawText("$year · APPROVED", cx, cy + radius * 0.55f, textPaint)
    }

    private fun drawOctagonStamp(canvas: Canvas, item: Book, cx: Float, cy: Float, radius: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f

        val octPath = Path()
        val side = radius * 0.45f
        octPath.moveTo(cx - side, cy - radius)
        octPath.lineTo(cx + side, cy - radius)
        octPath.lineTo(cx + radius, cy - side)
        octPath.lineTo(cx + radius, cy + side)
        octPath.lineTo(cx + side, cy + radius)
        octPath.lineTo(cx - side, cy + radius)
        octPath.lineTo(cx - radius, cy + side)
        octPath.lineTo(cx - radius, cy - side)
        octPath.close()
        canvas.drawPath(octPath, paint)

        val cleanTitle = if (item.title.length > 5) item.title.substring(0, 4) + ".." else item.title

        textPaint.color = color
        textPaint.textAlign = Paint.Align.CENTER

        // 顶部
        textPaint.textSize = radius * 0.20f
        textPaint.isFakeBoldText = true
        canvas.drawText("🎮 STEAM PASSED", cx, cy - radius * 0.45f, textPaint)

        // 中心作品名
        textPaint.textSize = radius * 0.32f
        textPaint.isFakeBoldText = true
        canvas.drawText(cleanTitle, cx, cy + radius * 0.08f, textPaint)

        // 底部
        textPaint.textSize = radius * 0.20f
        textPaint.isFakeBoldText = false
        canvas.drawText("CLEARED · 100%", cx, cy + radius * 0.55f, textPaint)
    }

    fun exportUltraHdPassportBitmap(): Bitmap {
        val targetW = 1080
        val filtered = items.filter { it.mediaType == currentTab }
        val cols = 3
        val rows = (filtered.size + cols - 1) / cols
        val cellH = targetW / cols * 0.95f
        val headerH = targetW * 0.52f
        val targetH = (headerH + rows * cellH + targetW * 0.1f).toInt().coerceAtLeast(1080)

        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawPassport(canvas, targetW.toFloat(), targetH.toFloat(), isExport = true)
        return bmp
    }
}
