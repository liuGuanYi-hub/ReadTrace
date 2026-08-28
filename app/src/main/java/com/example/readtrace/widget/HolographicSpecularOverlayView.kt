package com.example.readtrace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class HolographicSpecularOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val bounds = RectF()

    private var currentPitch = 0f
    private var currentRoll = 0f
    var cornerRadius = 24f * resources.displayMetrics.density

    // 优雅全息烫金/彩虹覆膜高光色彩带
    private val rainbowColors = intArrayOf(
        Color.parseColor("#00FFFFFF"),
        Color.parseColor("#12E0C3FC"), // 极浅紫霞
        Color.parseColor("#208EC5FC"), // 晨曦冰蓝
        Color.parseColor("#38FFFFFF"), // 核心聚光纯白
        Color.parseColor("#20FED6E3"), // 浅樱粉
        Color.parseColor("#12FFF6B7"), // 浅金曜
        Color.parseColor("#00FFFFFF"),
    )

    private val colorPositions = floatArrayOf(
        0.0f, 0.20f, 0.40f, 0.50f, 0.60f, 0.80f, 1.0f,
    )

    private val goldColors = intArrayOf(
        Color.TRANSPARENT,
        Color.parseColor("#18E0A96D"),
        Color.parseColor("#30FFFFFF"),
        Color.TRANSPARENT,
    )
    private val goldPositions = floatArrayOf(0f, 0.45f, 0.55f, 1f)

    private val shaderMatrix = android.graphics.Matrix()
    private var baseRainbowGradient: LinearGradient? = null
    private var baseGoldGradient: LinearGradient? = null

    init {
        setWillNotDraw(false)
    }

    fun updateAngles(pitch: Float, roll: Float) {
        this.currentPitch = pitch
        this.currentRoll = roll
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fw = w.toFloat()
        val fh = h.toFloat()
        bounds.set(0f, 0f, fw, fh)
        clipPath.reset()
        clipPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)

        if (fw > 0f && fh > 0f) {
            baseRainbowGradient = LinearGradient(
                -fw * 0.4f,
                -fh * 0.4f,
                fw * 1.4f,
                fh * 1.4f,
                rainbowColors,
                colorPositions,
                Shader.TileMode.CLAMP,
            )
            baseGoldGradient = LinearGradient(
                fw * 0.2f,
                -fh * 0.2f,
                fw * 0.8f,
                fh * 1.2f,
                goldColors,
                goldPositions,
                Shader.TileMode.CLAMP,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        canvas.save()
        canvas.clipPath(clipPath)

        // 1. 正向全息彩虹扫光（基于 GPU Matrix 位移，零堆内存分配）
        val rainbow = baseRainbowGradient
        if (rainbow != null) {
            val centerOffsetX = currentRoll * (w * 0.85f)
            val centerOffsetY = -currentPitch * (h * 0.85f)
            shaderMatrix.setTranslate(centerOffsetX, centerOffsetY)
            rainbow.setLocalMatrix(shaderMatrix)
            paint.shader = rainbow
            canvas.drawRect(bounds, paint)
        }

        // 2. 反向差速全息烫金高光层（裸眼 3D 浮雕深层反光）
        val gold = baseGoldGradient
        if (gold != null) {
            val reverseOffsetX = -currentRoll * (w * 0.45f)
            val reverseOffsetY = currentPitch * (h * 0.45f)
            shaderMatrix.setTranslate(reverseOffsetX, reverseOffsetY)
            gold.setLocalMatrix(shaderMatrix)
            paint.shader = gold
            canvas.drawRect(bounds, paint)
        }

        canvas.restore()
    }
}
