package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
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
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.CoverImageHelper
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🛂 精神宇宙巡礼护照视图 (CulturalPassportView)
 *
 * 重构升级：
 * 1. 【封面嵌入】：每个签证戳印文字上方均嵌入该作品的真实微缩封面海报；
 * 2. 【复古实体护照质感】：双圆/八角印泥纹样、持照人文化足迹抬头、防伪金箔暗纹；
 * 3. 【盖印物理冲击与粒子】：点击产生弹簧缩放、墨滴震荡光环与 360° 墨滴喷溅；
 * 4. 【异步图片缓存】：双层 LRU 缓存，流畅无掉帧。
 */
class CulturalPassportView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class StampTouchTarget(
        val rect: RectF,
        val item: Book,
        val index: Int,
        val cx: Float,
        val cy: Float,
        val radius: Float,
    )

    private class InkSpark(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val color: Int,
        val radius: Float,
        var alpha: Float = 1.0f,
    ) {
        fun update(dt: Float) {
            x += vx * dt
            y += vy * dt
            vx *= 0.94f
            vy *= 0.94f
            alpha = (alpha - 1.8f * dt).coerceAtLeast(0f)
        }
    }

    private var items: List<Book> = emptyList()
    private var currentTab: MediaType = MediaType.ANIME // 🌸 番剧 或 🎮 游戏
    private val coverBitmaps = mutableMapOf<String, Bitmap>()

    private val touchTargets = mutableListOf<StampTouchTarget>()
    var onStampClickListener: ((item: Book, screenX: Float, screenY: Float) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shockwavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
    }

    // 盖印激荡动画状态
    private var activeImpactIndex: Int = -1
    private var impactProgress: Float = 0f
    private var impactCx: Float = 0f
    private var impactCy: Float = 0f
    private var impactRadius: Float = 0f
    private var impactColor: Int = Color.RED
    private val sparks = mutableListOf<InkSpark>()
    private var impactAnimator: ValueAnimator? = null

    private val stampColors = intArrayOf(
        Color.parseColor("#C84B31"), // 朱砂红
        Color.parseColor("#1B4965"), // 霁蓝
        Color.parseColor("#2D6A4F"), // 苍翠
        Color.parseColor("#5A189A"), // 紫藤
        Color.parseColor("#D35400"), // 琥珀橙
        Color.parseColor("#0E7490"), // 青碧
    )

    fun setData(items: List<Book>, tab: MediaType = MediaType.ANIME) {
        this.items = items
        this.currentTab = tab

        // 预加载所有封面图片
        items.forEach { book ->
            val url = book.coverUrl.orEmpty().trim()
            if (url.isNotBlank() && !coverBitmaps.containsKey(url)) {
                CoverImageHelper.loadCoverBitmap(context, url, 260, 390) { bmp ->
                    if (bmp != null) {
                        coverBitmaps[url] = bmp
                        postInvalidate()
                    }
                }
            }
        }

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
        val pad = w * 0.04f
        val cellW = (w - pad * 2 - w * 0.04f) / cols
        val cellH = cellW * 1.54f
        val headerH = w * 0.44f
        val totalH = (headerH + rows * cellH + w * 0.12f).toInt()
        setMeasuredDimension(w, totalH.coerceAtLeast(600))
    }

    fun triggerStampImpact(index: Int, cx: Float, cy: Float, radius: Float, color: Int) {
        activeImpactIndex = index
        impactCx = cx
        impactCy = cy
        impactRadius = radius
        impactColor = color
        sparks.clear()

        val rand = Random(System.currentTimeMillis())
        for (i in 0 until 18) {
            val angle = rand.nextDouble(0.0, Math.PI * 2.0)
            val speed = rand.nextFloat() * 320f + 160f
            val vx = (cos(angle) * speed).toFloat()
            val vy = (sin(angle) * speed).toFloat()
            val startX = cx + (cos(angle) * radius * 0.85f).toFloat()
            val startY = cy + (sin(angle) * radius * 0.85f).toFloat()
            val sz = rand.nextFloat() * 4.5f + 2.5f
            sparks.add(InkSpark(startX, startY, vx, vy, color, sz))
        }

        impactAnimator?.cancel()
        impactAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550L
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener { va ->
                impactProgress = va.animatedValue as Float
                sparks.forEach { it.update(0.016f) }
                invalidate()
            }
        }
        impactAnimator?.start()
    }

    fun getStampScreenCoordinates(cx: Float, cy: Float): Pair<Float, Float> {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return Pair(location[0] + cx, location[1] + cy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            val hit = touchTargets.firstOrNull { it.rect.contains(x, y) }
            if (hit != null) {
                val color = stampColors[hit.index % stampColors.size]
                triggerStampImpact(hit.index, hit.cx, hit.cy, hit.radius, color)
                val (sx, sy) = getStampScreenCoordinates(hit.cx, hit.cy)
                onStampClickListener?.invoke(hit.item, sx, sy)
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
        val headerH = w * 0.40f
        val headerRect = RectF(pad + w * 0.02f, headerTop, w - pad - w * 0.02f, headerTop + headerH)

        // 护照抬头背板 (深蓝烫金风)
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
        canvas.drawCircle(headerRect.left + w * 0.13f, headerRect.centerY(), w * 0.085f, paint)
        canvas.drawCircle(headerRect.left + w * 0.13f, headerRect.centerY(), w * 0.070f, paint)

        textPaint.color = Color.parseColor("#E0A96D")
        textPaint.textSize = w * 0.052f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("🌌", headerRect.left + w * 0.13f, headerRect.centerY() + w * 0.018f, textPaint)

        // 护照标题
        val textLeft = headerRect.left + w * 0.26f
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.parseColor("#F5EFE6")
        textPaint.textSize = w * 0.040f
        textPaint.isFakeBoldText = true
        canvas.drawText("READTRACE PASSPORT", textLeft, headerRect.top + headerH * 0.28f, textPaint)

        textPaint.color = Color.parseColor("#E0A96D")
        textPaint.textSize = w * 0.032f
        canvas.drawText("阅痕 · 精神宇宙巡礼护照", textLeft, headerRect.top + headerH * 0.48f, textPaint)

        // 持照人信息
        textPaint.color = Color.parseColor("#A89F91")
        textPaint.textSize = w * 0.023f
        textPaint.isFakeBoldText = false
        val holderInfo = "持照人: 精神旅行家 · 签发地: ReadTrace Universe"
        canvas.drawText(holderInfo, textLeft, headerRect.top + headerH * 0.70f, textPaint)

        val animeCount = items.count { it.mediaType == MediaType.ANIME }
        val gameCount = items.count { it.mediaType == MediaType.GAME }
        val visaStats = "已获签证: 🌸 $animeCount 部番剧入境 · 🎮 $gameCount 款游戏通关"
        canvas.drawText(visaStats, textLeft, headerRect.top + headerH * 0.88f, textPaint)

        // 3. 签证页印章网格 (Visa Stamp Grid)
        val filtered = items.filter { it.mediaType == currentTab }
        val cols = 3
        val gridTop = headerRect.bottom + w * 0.04f
        val cellW = (w - pad * 2 - w * 0.04f) / cols
        val cellH = cellW * 1.54f

        filtered.forEachIndexed { idx, item ->
            val col = idx % cols
            val row = idx / cols
            val cx = pad + w * 0.02f + col * cellW + cellW * 0.5f
            val cy = gridTop + row * cellH + cellH * 0.5f

            val stampCardRect = RectF(cx - cellW * 0.47f, cy - cellH * 0.48f, cx + cellW * 0.47f, cy + cellH * 0.48f)
            if (!isExport) {
                touchTargets.add(StampTouchTarget(stampCardRect, item, idx, cx, cy, cellW * 0.45f))
            }

            drawSingleVisaStampWithCover(canvas, item, cx, cy, cellW, cellH, idx, isExport)
        }

        // 4. 绘制盖印墨迹震荡同心冲击环与放射微粒 (动态图层)
        if (!isExport && activeImpactIndex >= 0 && impactProgress in 0.001f..0.999f) {
            val shockR = impactRadius * (1.0f + impactProgress * 0.85f)
            val shockAlpha = ((1f - impactProgress) * 200).toInt()
            shockwavePaint.style = Paint.Style.STROKE
            shockwavePaint.strokeWidth = (1f - impactProgress) * 6f + 1.5f
            shockwavePaint.color = impactColor
            shockwavePaint.alpha = shockAlpha
            canvas.drawCircle(impactCx, impactCy, shockR, shockwavePaint)

            val innerR = impactRadius * (0.8f + impactProgress * 0.5f)
            val innerAlpha = ((1f - impactProgress) * 140).toInt()
            shockwavePaint.strokeWidth = 2f
            shockwavePaint.alpha = innerAlpha
            canvas.drawCircle(impactCx, impactCy, innerR, shockwavePaint)

            sparks.forEach { sp ->
                sparkPaint.style = Paint.Style.FILL
                sparkPaint.color = sp.color
                sparkPaint.alpha = (sp.alpha * 255).toInt()
                canvas.drawCircle(sp.x, sp.y, sp.radius * sp.alpha, sparkPaint)
            }
        }
    }

    private fun drawSingleVisaStampWithCover(
        canvas: Canvas,
        item: Book,
        cx: Float,
        cy: Float,
        cellW: Float,
        cellH: Float,
        index: Int,
        isExport: Boolean,
    ) {
        val color = stampColors[index % stampColors.size]
        val hash = item.title.hashCode()
        val tiltAngle = ((hash % 15) - 7).toFloat() // 微弱倾角，逼真模拟实体印泥

        canvas.save()

        // 弹簧回弹与按压形变
        if (!isExport && index == activeImpactIndex && impactProgress in 0.001f..0.999f) {
            val scale = if (impactProgress < 0.35f) {
                1.0f - (impactProgress / 0.35f) * 0.16f
            } else {
                val p = (impactProgress - 0.35f) / 0.65f
                0.84f + (1.12f - 0.84f) * sin(p * Math.PI.toFloat())
            }
            canvas.scale(scale, scale, cx, cy)
        }

        canvas.rotate(tiltAngle, cx, cy)

        // 1. 签证戳印外卡槽 (Visa Frame)
        val cardRect = RectF(cx - cellW * 0.46f, cy - cellH * 0.48f, cx + cellW * 0.46f, cy + cellH * 0.48f)

        // 印泥卡底色 (微透羊皮印泥浅晕)
        paint.color = Color.argb(18, Color.red(color), Color.green(color), Color.blue(color))
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(cardRect, 12f, 12f, paint)

        // 外圈实线印泥轮廓
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f
        canvas.drawRoundRect(cardRect, 12f, 12f, paint)

        // 内圈虚线防伪边
        val innerMargin = cellW * 0.04f
        val innerRect = RectF(cardRect.left + innerMargin, cardRect.top + innerMargin, cardRect.right - innerMargin, cardRect.bottom - innerMargin)
        paint.strokeWidth = 1.0f
        paint.pathEffect = DashPathEffect(floatArrayOf(5f, 3.5f), 0f)
        canvas.drawRoundRect(innerRect, 9f, 9f, paint)
        paint.pathEffect = null

        // 2. 顶部印泥标志 (Header Seal)
        val isGame = item.mediaType == MediaType.GAME
        textPaint.color = color
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = cellW * 0.10f
        textPaint.isFakeBoldText = true
        val topBadge = if (isGame) "🎮 PLATINUM" else "🌸 IMMIGRATION"
        canvas.drawText(topBadge, cx, cardRect.top + cellW * 0.15f, textPaint)

        // 3. 上方真实封面微缩海报 (Cover Artwork Thumbnail - 核心满足用户诉求)
        val coverW = cellW * 0.68f
        val coverH = coverW * 1.36f
        val coverLeft = cx - coverW * 0.5f
        val coverTop = cardRect.top + cellW * 0.20f
        val coverRect = RectF(coverLeft, coverTop, coverLeft + coverW, coverTop + coverH)

        val bmp = coverBitmaps[item.coverUrl.orEmpty().trim()]
        if (bmp != null && !bmp.isRecycled) {
            canvas.save()
            val coverClip = Path().apply {
                addRoundRect(coverRect, 7f, 7f, Path.Direction.CW)
            }
            canvas.clipPath(coverClip)

            val bmpW = bmp.width.toFloat()
            val bmpH = bmp.height.toFloat()
            val targetRatio = coverW / coverH
            val bmpRatio = bmpW / bmpH

            val srcRect = if (bmpRatio > targetRatio) {
                val cropW = bmpH * targetRatio
                val left = (bmpW - cropW) * 0.5f
                Rect(left.toInt(), 0, (left + cropW).toInt(), bmpH.toInt())
            } else {
                val cropH = bmpW / targetRatio
                val top = (bmpH - cropH) * 0.5f
                Rect(0, top.toInt(), bmpW.toInt(), (top + cropH).toInt())
            }
            canvas.drawBitmap(bmp, srcRect, coverRect, bitmapPaint)
            canvas.restore()
        } else {
            // 优雅占位
            paint.color = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(coverRect, 7f, 7f, paint)

            textPaint.textSize = coverH * 0.32f
            textPaint.color = color
            canvas.drawText(if (isGame) "🎮" else "🌸", coverRect.centerX(), coverRect.centerY() + coverH * 0.10f, textPaint)
        }

        // 封面边缘微光边框与右下角戳印徽章
        paint.color = Color.argb(160, Color.red(color), Color.green(color), Color.blue(color))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawRoundRect(coverRect, 7f, 7f, paint)

        // 4. 下方文字：作品标题 (Title Below Cover)
        val titleY = coverRect.bottom + cellW * 0.14f
        val cleanTitle = if (item.title.length > 6) item.title.substring(0, 5) + "…" else item.title
        textPaint.color = color
        textPaint.textSize = (cellW * 0.12f).coerceAtLeast(10f)
        textPaint.isFakeBoldText = true
        canvas.drawText(cleanTitle, cx, titleY, textPaint)

        // 5. 最底部：年份与审核通过签注 (Year & Approval Date)
        val year = item.tags.firstOrNull { it.contains("年") }?.replace("年", "") ?: "2024"
        val dateY = titleY + cellW * 0.11f
        textPaint.color = Color.argb(200, Color.red(color), Color.green(color), Color.blue(color))
        textPaint.textSize = cellW * 0.088f
        textPaint.isFakeBoldText = false
        val approvalText = if (isGame) "$year · 100% CLEAR" else "$year · APPROVED"
        canvas.drawText(approvalText, cx, dateY, textPaint)

        canvas.restore()
    }

    fun exportUltraHdPassportBitmap(): Bitmap {
        val targetW = 1080
        val filtered = items.filter { it.mediaType == currentTab }
        val cols = 3
        val rows = (filtered.size + cols - 1) / cols
        val pad = targetW * 0.04f
        val cellW = (targetW - pad * 2 - targetW * 0.04f) / cols
        val cellH = cellW * 1.54f
        val headerH = targetW * 0.44f
        val targetH = (headerH + rows * cellH + targetW * 0.12f).toInt().coerceAtLeast(1080)

        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawPassport(canvas, targetW.toFloat(), targetH.toFloat(), isExport = true)
        return bmp
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        impactAnimator?.cancel()
        sparks.clear()
    }
}
