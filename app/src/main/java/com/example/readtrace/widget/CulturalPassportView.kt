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
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.example.readtrace.model.Book
import com.example.readtrace.model.MediaType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 🛂 精神宇宙巡礼护照视图 (CulturalPassportView)
 *
 * P5 阶段二升级：
 * 1. 盖印物理下压与回弹冲击（Ink Squash & Recoil）：点击印章产生 1.0 -> 0.82 -> 1.15 -> 1.0 弹性形变；
 * 2. 印泥同心震荡冲击环（Ink Shockwave Ring）：朱砂/霁蓝墨色光环自中心扩散淡出；
 * 3. 墨迹微粒放射喷溅（Radial Ink Sparks）：自印章外轮廓呈 360° 喷射 16 颗自发光墨滴微粒；
 * 4. 绝对屏幕坐标回传，精准协同全屏 Confetti 物理彩屑礼花。
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

    private val touchTargets = mutableListOf<StampTouchTarget>()
    var onStampClickListener: ((item: Book, screenX: Float, screenY: Float) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shockwavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
                touchTargets.add(StampTouchTarget(stampRect, item, idx, cx, cy, radius))
            }

            drawSingleVisaStamp(canvas, item, cx, cy, radius, idx, isExport)
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

    private fun drawSingleVisaStamp(canvas: Canvas, item: Book, cx: Float, cy: Float, radius: Float, index: Int, isExport: Boolean) {
        val color = stampColors[index % stampColors.size]
        val hash = item.title.hashCode()
        val tiltAngle = ((hash % 21) - 10).toFloat()

        canvas.save()

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

        val isGame = item.mediaType == MediaType.GAME
        if (isGame) {
            drawOctagonStamp(canvas, item, cx, cy, radius, color)
        } else {
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        impactAnimator?.cancel()
        sparks.clear()
    }
}
