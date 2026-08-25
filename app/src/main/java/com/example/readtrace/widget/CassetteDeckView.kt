package com.example.readtrace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * 📼 80 年代复古透光透明磁带卡座视图 (CassetteDeckView)
 *
 * 核心特性：
 * 1. 高透亚克力外壳与复古网格手写标签（SIDE A / 4-TRACK STEREO）；
 * 2. 双白色六角齿轮卷轴随音乐匀速旋转；
 * 3. 供带轮与收带轮磁带厚度根据播放进度（0.0f ~ 1.0f）动态演变；
 * 4. 底部金属拾音磁头与青铜色导带滚轮。
 */
class CassetteDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    var isPlaying: Boolean = false
        private set

    var progress: Float = 0.35f // 播放进度 0f ~ 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var trackTitle: String = "晴る"
        set(value) {
            field = value
            invalidate()
        }

    var artistName: String = "ヨルシカ"
        set(value) {
            field = value
            invalidate()
        }

    private var spoolRotationAngle = 0f
    private var rotationAnimator: ValueAnimator? = null

    // 路径与矩阵缓存
    private val bodyRect = RectF()
    private val labelRect = RectF()
    private val centerWindowRect = RectF()

    init {
        setupRotationAnimator()
    }

    private fun setupRotationAnimator() {
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = null
            addUpdateListener {
                if (isPlaying) {
                    spoolRotationAngle = (spoolRotationAngle + 2.0f) % 360f
                    invalidate()
                }
            }
        }
    }

    fun togglePlay(play: Boolean) {
        if (isPlaying == play) return
        isPlaying = play
        if (isPlaying) {
            rotationAnimator?.start()
        } else {
            rotationAnimator?.cancel()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 磁带标准 1.6 : 1 宽长比
        val tapeW = w * 0.88f
        val tapeH = tapeW * 0.63f
        val left = (w - tapeW) / 2f
        val top = (h - tapeH) / 2f
        bodyRect.set(left, top, left + tapeW, top + tapeH)

        // 1. 绘制透明外壳底色与亚克力反光
        drawCassetteBody(canvas, bodyRect)

        // 2. 绘制复古磁带标签贴纸
        drawVintageLabel(canvas, bodyRect)

        // 3. 绘制中央透视窗与双磁带卷带厚度
        drawTapeSpoolsAndRibbon(canvas, bodyRect)

        // 4. 绘制底部拾音磁头与铜导带滚轴
        drawMagneticHeadArea(canvas, bodyRect)
    }

    private fun drawCassetteBody(canvas: Canvas, r: RectF) {
        // 外壳深灰透明材质
        bodyPaint.shader = LinearGradient(
            r.left, r.top, r.left, r.bottom,
            intArrayOf(
                Color.parseColor("#1C2028"),
                Color.parseColor("#11141A"),
                Color.parseColor("#0C0E12"),
            ),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(r, 20f, 20f, bodyPaint)

        // 外圈高光边框
        strokePaint.color = Color.parseColor("#344054")
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(r, 20f, 20f, strokePaint)

        // 磁带四角复古螺丝孔
        bodyPaint.shader = null
        bodyPaint.color = Color.parseColor("#475467")
        val screwOffset = 18f
        canvas.drawCircle(r.left + screwOffset, r.top + screwOffset, 5f, bodyPaint)
        canvas.drawCircle(r.right - screwOffset, r.top + screwOffset, 5f, bodyPaint)
        canvas.drawCircle(r.left + screwOffset, r.bottom - screwOffset, 5f, bodyPaint)
        canvas.drawCircle(r.right - screwOffset, r.bottom - screwOffset, 5f, bodyPaint)
    }

    private fun drawVintageLabel(canvas: Canvas, r: RectF) {
        val labelW = r.width() * 0.84f
        val labelH = r.height() * 0.58f
        val lLeft = r.centerX() - labelW / 2f
        val lTop = r.top + r.height() * 0.12f
        labelRect.set(lLeft, lTop, lLeft + labelW, lTop + labelH)

        // 复古泛黄米白牛皮纸贴纸
        labelPaint.color = Color.parseColor("#ECE5D8")
        canvas.drawRoundRect(labelRect, 10f, 10f, labelPaint)

        // 顶部复古红蓝双色彩条 (Vintage Stripes)
        labelPaint.color = Color.parseColor("#C0392B")
        canvas.drawRect(labelRect.left, labelRect.top + 8f, labelRect.right, labelRect.top + 14f, labelPaint)
        labelPaint.color = Color.parseColor("#2980B9")
        canvas.drawRect(labelRect.left, labelRect.top + 16f, labelRect.right, labelRect.top + 20f, labelPaint)

        // 标签文字 SIDE A & 4-TRACK STEREO
        textPaint.color = Color.parseColor("#101828")
        textPaint.textSize = 28f
        textPaint.isFakeBoldText = true
        canvas.drawText("SIDE A", labelRect.left + 50f, labelRect.top + 55f, textPaint)

        textPaint.textSize = 18f
        textPaint.isFakeBoldText = false
        textPaint.color = Color.parseColor("#667085")
        canvas.drawText("TYPE II · HIGH BIAS", labelRect.right - 80f, labelRect.top + 55f, textPaint)

        // 曲目与艺术家手写排版
        textPaint.textSize = 32f
        textPaint.isFakeBoldText = true
        textPaint.color = Color.parseColor("#1D2939")
        canvas.drawText("《$trackTitle》", labelRect.centerX(), labelRect.top + 60f, textPaint)

        textPaint.textSize = 20f
        textPaint.isFakeBoldText = false
        textPaint.color = Color.parseColor("#475467")
        canvas.drawText(artistName, labelRect.centerX(), labelRect.top + 85f, textPaint)
    }

    private fun drawTapeSpoolsAndRibbon(canvas: Canvas, r: RectF) {
        // 中央透明观察窗
        val winW = r.width() * 0.62f
        val winH = r.height() * 0.32f
        val winLeft = r.centerX() - winW / 2f
        val winTop = r.centerY() - winH / 2f + 15f
        centerWindowRect.set(winLeft, winTop, winLeft + winW, winTop + winH)

        bodyPaint.color = Color.parseColor("#090B0E")
        canvas.drawRoundRect(centerWindowRect, 8f, 8f, bodyPaint)

        val leftHubX = centerWindowRect.left + winW * 0.28f
        val rightHubX = centerWindowRect.right - winW * 0.28f
        val hubY = centerWindowRect.centerY()

        // 计算磁带厚度（左侧供带轮越来越小，右侧收带轮越来越大）
        val maxTapeR = winH * 0.44f
        val minTapeR = winH * 0.22f
        val leftTapeRadius = maxTapeR - (maxTapeR - minTapeR) * progress
        val rightTapeRadius = minTapeR + (maxTapeR - minTapeR) * progress

        // 绘制深红棕色磁带圆环
        tapePaint.color = Color.parseColor("#38180E")
        canvas.drawCircle(leftHubX, hubY, leftTapeRadius, tapePaint)
        canvas.drawCircle(rightHubX, hubY, rightTapeRadius, tapePaint)

        // 磁带微细纹理
        strokePaint.color = Color.parseColor("#4F2315")
        strokePaint.strokeWidth = 1.5f
        canvas.drawCircle(leftHubX, hubY, leftTapeRadius * 0.85f, strokePaint)
        canvas.drawCircle(rightHubX, hubY, rightTapeRadius * 0.85f, strokePaint)

        // 绘制左右白色六角齿轮卷轴 (Spools)
        drawSpoolHub(canvas, leftHubX, hubY, winH * 0.20f, spoolRotationAngle)
        drawSpoolHub(canvas, rightHubX, hubY, winH * 0.20f, spoolRotationAngle)

        // 连接双轮的磁带拉线
        strokePaint.color = Color.parseColor("#2B120B")
        strokePaint.strokeWidth = 3f
        canvas.drawLine(
            leftHubX, hubY + leftTapeRadius * 0.95f,
            rightHubX, hubY + rightTapeRadius * 0.95f,
            strokePaint,
        )
    }

    private fun drawSpoolHub(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float) {
        // 白色基座
        hubPaint.color = Color.parseColor("#F4F5F7")
        canvas.drawCircle(cx, cy, radius, hubPaint)

        // 六角齿孔
        canvas.save()
        canvas.rotate(angle, cx, cy)
        hubPaint.color = Color.parseColor("#1D2939")
        for (i in 0 until 6) {
            val a = Math.toRadians((i * 60).toDouble())
            val tx = cx + (radius * 0.62f * cos(a)).toFloat()
            val ty = cy + (radius * 0.62f * sin(a)).toFloat()
            canvas.drawCircle(tx, ty, radius * 0.22f, hubPaint)
        }
        // 中心孔
        canvas.drawCircle(cx, cy, radius * 0.38f, hubPaint)
        canvas.restore()
    }

    private fun drawMagneticHeadArea(canvas: Canvas, r: RectF) {
        // 磁带底部梯形磁头槽
        val bottomH = r.height() * 0.18f
        val trapPath = Path().apply {
            moveTo(r.centerX() - r.width() * 0.30f, r.bottom - bottomH)
            lineTo(r.centerX() + r.width() * 0.30f, r.bottom - bottomH)
            lineTo(r.centerX() + r.width() * 0.24f, r.bottom - 4f)
            lineTo(r.centerX() - r.width() * 0.24f, r.bottom - 4f)
            close()
        }
        bodyPaint.color = Color.parseColor("#161A22")
        canvas.drawPath(trapPath, bodyPaint)

        // 底部导带青铜小滚轮
        bodyPaint.color = Color.parseColor("#D4AF37")
        canvas.drawCircle(r.centerX() - r.width() * 0.18f, r.bottom - bottomH * 0.5f, 6f, bodyPaint)
        canvas.drawCircle(r.centerX() + r.width() * 0.18f, r.bottom - bottomH * 0.5f, 6f, bodyPaint)

        // 核心磁头触点
        bodyPaint.color = Color.parseColor("#98A2B3")
        canvas.drawRoundRect(
            r.centerX() - 25f, r.bottom - bottomH * 0.8f,
            r.centerX() + 25f, r.bottom - 8f,
            4f, 4f, bodyPaint,
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rotationAnimator?.cancel()
    }
}
